package com.glowtrack.client;

import com.glowtrack.network.Payloads;
import com.glowtrack.track.TrackPoint;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 客户端轨迹缓存：单例。
 *
 * <p>两种数据来源：</p>
 * <ul>
 *   <li>服务端模式：网络下发（快照 + 增量）</li>
 *   <li>降级模式：本地采样已加载实体</li>
 * </ul>
 *
 * <p>当前"正在追踪"的目标及其轨迹点存于此，供渲染器与 GUI 读取。</p>
 */
public final class ClientTrackState {
    private static ClientTrackState instance;

    private boolean serverMode = false;   // 是否收到服务端 hello
    private final List<Payloads.PlayerEntry> whitelist = new ArrayList<>();
    private UUID tracked;                 // 当前追踪目标
    private final List<TrackPoint> points = new ArrayList<>();
    private final Set<UUID> trackedSet = new HashSet<>(); // 正被追踪的集合（渲染用）
    private double scrubRatio = 1.0;      // 时间轴游标 0..1（GUI 与渲染器共享）
    private double viewEndRatio = 1.0;    // 时间轴可见窗口右边缘 0..1（1=最新）
    private double viewSpanRatio = 0.15;  // 可见窗口宽度占总长比例

    public static ClientTrackState get() {
        if (instance == null) instance = new ClientTrackState();
        return instance;
    }

    public boolean isServerMode() { return serverMode; }

    public void onServerHello(int protocol) {
        serverMode = true;
    }

    public void onTrackList(List<Payloads.PlayerEntry> list) {
        whitelist.clear();
        whitelist.addAll(list);
    }

    public List<Payloads.PlayerEntry> whitelist() { return whitelist; }

    public void onTrackData(Payloads.TrackData data) {
        UUID id = data.player();
        if (data.isSnapshot()) {
            tracked = id;
            points.clear();
            trackedSet.add(id);
            clearActions();   // 换目标：上一个目标的事件标记不能残留
            scrubTimeMillis = Long.MIN_VALUE;
            scrubRatio = 1.0;
            viewEndRatio = 1.0;
            replaying = false;   // 切换目标：停回放
        }
        if (id.equals(tracked)) {
            for (Payloads.NetPoint np : data.points()) {
                ResourceLocation dim = ResourceLocation.tryParse(np.dim());
                if (dim == null) continue;   // 坏维度点直接跳过：否则造出 dimension=null 的点，
                                             // 后续渲染/导出时 p.dimension().toString() 抛 NPE 崩客户端
                points.add(new TrackPoint(np.t(), np.x(), np.y(), np.z(),
                        dim, np.v(), np.yaw(), np.pitch()));
            }
            points.sort(java.util.Comparator.comparingLong(TrackPoint::timeMillis));
            refreshView();
            reanchorScrub();   // 轨迹增长时游标按绝对时刻重锚定，不漂移
        }
    }

    /**
     * 本地导入轨迹（/gt import）：把导出的 JSON 轨迹"打开"到当前会话。
     * tracked/points 全量替换，游标与窗口复位到最新；不进服务端存储。
     */
    public void importPoints(UUID id, String name, List<TrackPoint> imported) {
        tracked = id;
        points.clear();
        trackedSet.add(id);
        clearActions();   // 换目标：残留的事件标记属于上一个目标
        points.addAll(imported);
        points.sort(java.util.Comparator.comparingLong(TrackPoint::timeMillis));
        refreshView();
        scrubTimeMillis = Long.MIN_VALUE;
        scrubRatio = 1.0;
        viewEndRatio = 1.0;
        replaying = false;   // 换目标：停回放
    }

    /** 行为事件（回放/实时显示用，与轨迹点独立）。 */
    private final List<com.glowtrack.track.ActionEvent> actions = new ArrayList<>();

    /**
     * 行为事件条数上限。
     * 事件流是"只进不出"的（不像轨迹点有服务端上限兜底），
     * 长时间开着追踪会无上限增长直到吃满内存。超出后丢最旧的。
     */
    private static final int MAX_ACTIONS = 20000;

    /** 服务端推送行为事件：追加 + 排序（与轨迹点同语义）。 */
    public void onActionData(java.util.UUID id, List<com.glowtrack.network.ActionDataPackets.NetAction> list) {
        for (var na : list) {
            actions.add(new com.glowtrack.track.ActionEvent(na.t(), na.type(), id.toString(),
                    na.x(), na.y(), na.z(), na.target()));
        }
        // 超出上限：丢最旧的（保留最近的事件）
        if (actions.size() > MAX_ACTIONS) {
            actions.subList(0, actions.size() - MAX_ACTIONS).clear();
        }
        actions.sort(java.util.Comparator.comparingLong(com.glowtrack.track.ActionEvent::t));
    }

    /** 清空行为事件（换目标 / 停止追踪 / 断线时必须调用，否则残留上一个目标的事件标记）。 */
    private void clearActions() {
        actions.clear();
    }

    /** 只读行为事件视图（ActionRenderer 用）。 */
    public List<com.glowtrack.track.ActionEvent> actionsView() { return java.util.Collections.unmodifiableList(actions); }

    /** 本地降级模式：追加一个采样点。 */
    public void localAppend(TrackPoint p) {
        if (tracked == null) return;
        if (points.isEmpty() || p.timeMillis() >= points.get(points.size() - 1).timeMillis()) {
            points.add(p);
            refreshView();
            reanchorScrub();
        }
    }

    public void setLocalTracked(UUID id, String name) {
        tracked = id;
        points.clear();
        trackedSet.add(id);
        clearActions();   // 换目标：残留的事件标记属于上一个目标
        refreshView();
        scrubTimeMillis = Long.MIN_VALUE;
        scrubRatio = 1.0;
        viewEndRatio = 1.0;
        replaying = false;   // 切换目标：停回放
    }

    public UUID tracked() { return tracked; }

    /** 只读视图缓存：包装对象仅在 points 结构变化时重建，渲染/GUI 每帧调用不再产生新包装。 */
    private List<TrackPoint> pointsViewCache = java.util.Collections.unmodifiableList(points);

    /** 只读视图：渲染器/GUI 遍历用，防止外部改动内部列表。 */
    public List<TrackPoint> points() { return pointsViewCache; }

    /** 渲染器用的只读视图（等价 points()）。 */
    public List<TrackPoint> pointsView() { return pointsViewCache; }

    /** points 结构变化后调用，刷新只读视图缓存。 */
    private void refreshView() { pointsViewCache = java.util.Collections.unmodifiableList(points); }

    public boolean isTracking(UUID id) { return trackedSet.contains(id); }

    /** 时间轴游标位置（0..1）。 */
    public double scrubRatio() { return scrubRatio; }

    public void setScrubRatio(double r) {
        this.scrubRatio = Math.max(0.0, Math.min(1.0, r));
        // 记住游标对应的绝对时刻；轨迹增长时游标锚定该时刻不漂移
        List<TrackPoint> pts = points;
        if (!pts.isEmpty()) {
            long t0 = pts.get(0).timeMillis(), t1 = pts.get(pts.size() - 1).timeMillis();
            this.scrubTimeMillis = t0 + (long) (this.scrubRatio * (t1 - t0));
        }
    }

    /** 游标锚定的绝对时刻（轨迹增长时游标保持在此时刻）。 */
    private long scrubTimeMillis = Long.MIN_VALUE;

    /**
     * 轨迹数据更新后调用：把 scrubRatio 从锚定的绝对时刻重算出来。
     * 这样看历史时新点不断到达，游标仍稳定指向用户选中的那个时刻。
     */
    public void reanchorScrub() {
        if (points.isEmpty() || scrubTimeMillis == Long.MIN_VALUE) return;
        long t0 = points.get(0).timeMillis(), t1 = points.get(points.size() - 1).timeMillis();
        long span = Math.max(1, t1 - t0);
        this.scrubRatio = Math.max(0.0, Math.min(1.0,
                (scrubTimeMillis - t0) / (double) span));
    }

    public double viewEndRatio() { return viewEndRatio; }
    public double viewSpanRatio() { return viewSpanRatio; }
    public double viewStartRatio() { return Math.max(0.0, viewEndRatio - viewSpanRatio); }

    // ==================== 时间轴回放 ====================

    /** 是否正在回放动画（游标自动匀速推进，像看录像一样）。 */
    private boolean replaying;

    /** 上一 tick 的现实时间戳（毫秒），用于按现实时间推进游标。 */
    private long replayLastMs;

    /** 回放倍速：现实 1 秒 = 游戏多少秒（等价每 tick += 1.0/(totalSeconds/speedup*20)，慢轨迹快放）。可经 setReplaySpeed 调节。 */
    private static double REPLAY_SPEEDUP = 30.0;

    /** 回放倍速下限（现实 1 秒 = 游戏 1 秒）。 */
    private static final double REPLAY_SPEED_MIN = 1.0;

    /** 回放倍速上限（现实 1 秒 = 游戏 120 秒）。 */
    private static final double REPLAY_SPEED_MAX = 120.0;

    /** 设置回放倍速（clamp 1..120）。 */
    public static void setReplaySpeed(double x) {
        REPLAY_SPEEDUP = Math.max(REPLAY_SPEED_MIN, Math.min(REPLAY_SPEED_MAX, x));
    }

    /** 当前回放倍速。 */
    public static double getReplaySpeed() { return REPLAY_SPEEDUP; }

    public boolean isReplaying() { return replaying; }

    /** 切换回放状态。开启时若游标已在末尾（≈1.0）则从头开始播放；无轨迹时不开启。 */
    public void toggleReplay() {
        if (!replaying && points.isEmpty()) return;
        replaying = !replaying;
        if (replaying) {
            if (scrubRatio >= 0.999) setScrubRatio(0.0);   // 从头播
            // 窗口跳到游标可见（游标置于窗口内 80% 处）
            viewEndRatio = Math.max(viewSpanRatio, Math.min(1.0, scrubRatio + viewSpanRatio * 0.2));
            replayLastMs = System.currentTimeMillis();
        }
    }

    /**
     * 每客户端 tick 调用：回放时按绝对时刻匀速推进游标（数据增长不漂移）。
     * 推进到末尾自动停止。
     */
    public void tickReplay() {
        if (!replaying) return;
        if (points.isEmpty()) { replaying = false; return; }
        long now = System.currentTimeMillis();
        long dt = Math.min(1000L, Math.max(0L, now - replayLastMs));   // 防卡顿补帧过长
        replayLastMs = now;
        if (dt == 0) return;

        long t0 = points.get(0).timeMillis(), t1 = points.get(points.size() - 1).timeMillis();
        long span = t1 - t0;
        if (span <= 0) {   // 单点/零时长轨迹：直接播完
            setScrubRatio(1.0);
            replaying = false;
            return;
        }
        long targetTime = t0 + (long) (scrubRatio * span) + (long) (dt * REPLAY_SPEEDUP);
        if (targetTime >= t1) {
            setScrubRatio(1.0);
            replaying = false;   // 播完自动停
        } else {
            setScrubRatio((targetTime - t0) / (double) span);
        }
        // 游标移出可见窗口时窗口跟随（播放器语义）：游标置于窗口内 80% 处，前方留预览空间
        if (scrubRatio > viewEndRatio || scrubRatio < viewStartRatio()) {
            viewEndRatio = Math.max(viewSpanRatio, Math.min(1.0, scrubRatio + viewSpanRatio * 0.2));
        }
    }

    /** 平移时间轴可见窗口（正=向最新，负=向历史）。 */
    public void panView(double deltaRatio) {
        viewEndRatio = Math.max(viewSpanRatio, Math.min(1.0, viewEndRatio + deltaRatio));
    }

    /**
     * 缩放时间轴窗口（factor>1 放大细节，<1 缩小看全景）。
     * anchorRatio = 缩放锚点（鼠标位置对应的全长比例），缩放时该点保持不动。
     */
    public void zoomView(double factor, double anchorRatio) {
        double newSpan = Math.max(0.02, Math.min(1.0, viewSpanRatio * factor));
        // 锚点在窗口内的相对位置保持不变
        double winStart = Math.max(0.0, viewEndRatio - viewSpanRatio);
        double rel = (anchorRatio - winStart) / Math.max(1e-9, viewSpanRatio);
        rel = Math.max(0.0, Math.min(1.0, rel));
        viewSpanRatio = newSpan;
        viewEndRatio = Math.max(newSpan, Math.min(1.0, anchorRatio + (1.0 - rel) * newSpan));
    }

    /** 视图窗口跳到最新（开始追踪新目标时调用）。 */
    public void snapViewToLatest() {
        viewEndRatio = 1.0;
    }

    /** 停止追踪：清空当前目标与轨迹，同时移除追踪标记（修复泄漏）。 */
    public void clearTracking() {
        if (tracked != null) trackedSet.remove(tracked);
        tracked = null;
        points.clear();
        refreshView();
        clearActions();   // 停止追踪：事件标记不能残留（否则 ActionRenderer 继续画上一个目标的事件）
        // 复位游标与视图：否则下次追踪会带着上一次的游标位置/缩放，
        // 表现为"刚开追踪就停在半路"或"时间轴窗口莫名其妙"
        scrubTimeMillis = Long.MIN_VALUE;
        scrubRatio = 1.0;
        viewEndRatio = 1.0;
        viewSpanRatio = 0.15;
        replaying = false;   // 数据清空，停回放
    }

    /** 重置（断开连接时）。 */
    public void reset() {
        serverMode = false;
        whitelist.clear();
        tracked = null;
        points.clear();
        refreshView();
        trackedSet.clear();
        clearActions();   // 断线：上个服务器的行为事件不能带到下个连接
        scrubRatio = 1.0;
        viewEndRatio = 1.0;
        viewSpanRatio = 0.15;
        replaying = false;   // 断开连接，停回放
    }

    /**
     * 导出当前追踪轨迹为 JSON，写入模组目录 export/ 下。
     * @return 结果描述（成功含文件名，失败含原因）
     */
    public String exportSelected(String name) {
        if (points.isEmpty()) return "无轨迹可导出";
        try {
            java.nio.file.Path dir = net.minecraft.client.Minecraft.getInstance()
                    .gameDirectory.toPath().resolve("glowtrack").resolve("export");
            java.nio.file.Files.createDirectories(dir);
            String safe = (name == null || name.isBlank()) ? "track" : name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            java.nio.file.Path f = dir.resolve(safe + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".json");

            // GSON 序列化（防名字里的引号/反斜杠破坏 JSON 结构）
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("player", tracked == null ? "" : tracked.toString());
            root.addProperty("name", safe);
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (TrackPoint p : points) {
                com.google.gson.JsonObject e = new com.google.gson.JsonObject();
                e.addProperty("t", p.timeMillis());
                e.addProperty("x", p.x());
                e.addProperty("y", p.y());
                e.addProperty("z", p.z());
                e.addProperty("d", p.dimension() == null ? "" : p.dimension().toString());
                e.addProperty("v", p.speed());
                arr.add(e);
            }
            root.add("points", arr);
            java.nio.file.Files.writeString(f, root.toString(), java.nio.charset.StandardCharsets.UTF_8);
            return "已导出 " + points.size() + " 点 → " + f.getFileName();
        } catch (Exception e) {
            return "导出失败: " + e.getMessage();
        }
    }

    /**
     * 导出当前追踪轨迹为 CSV（表头 + 每点一行，与 TrackStore.exportCsv 同格式），
     * 写入模组目录 export/ 下（与 JSON 导出同目录、同文件名规则）。
     * @return 结果描述（成功含文件名，失败含原因）
     */
    public String exportSelectedCsv(String name) {
        if (points.isEmpty()) return "无轨迹可导出";
        try {
            java.nio.file.Path dir = net.minecraft.client.Minecraft.getInstance()
                    .gameDirectory.toPath().resolve("glowtrack").resolve("export");
            java.nio.file.Files.createDirectories(dir);
            String safe = (name == null || name.isBlank()) ? "track" : name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            java.nio.file.Path f = dir.resolve(safe + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".csv");

            StringBuilder sb = new StringBuilder("time,x,y,z,dim,speed,yaw,pitch\n");
            for (TrackPoint p : points) {
                // 维度为 null 时写空串——直接 append(null) 会写出字面量 "null"，
                // 导入时被当成维度名解析失败
                String dimStr = p.dimension() == null ? "" : p.dimension().toString();
                sb.append(p.timeMillis()).append(',')
                  .append(p.x()).append(',')
                  .append(p.y()).append(',')
                  .append(p.z()).append(',')
                  .append(dimStr).append(',')
                  .append(p.speed()).append(',')
                  .append(p.yaw()).append(',')
                  .append(p.pitch()).append('\n');
            }
            java.nio.file.Files.writeString(f, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
            return Text.s("glowtrack.export.csv_done", f.getFileName().toString());
        } catch (Exception e) {
            return "导出失败: " + e.getMessage();
        }
    }

    // ==================== 发送便捷方法 ====================

    public static void sendToServer(Object payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || !(payload instanceof com.glowtrack.network.Packet pkt)) return;
        try {
            // Forge 1.19.4 没有 CustomPacketPayload（1.20.5+ 才有），
            // 统一装进 Envelope 走 SimpleChannel。
            net.minecraft.network.FriendlyByteBuf buf =
                    new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            pkt.write(buf);
            com.glowtrack.network.NetworkHandler.CHANNEL.sendToServer(
                    new com.glowtrack.network.Envelope(pkt.id(), buf));
        } catch (Throwable ignored) {
            // 连接尚未建立 / 服务端无此通道——静默跳过
        }
    }
}
