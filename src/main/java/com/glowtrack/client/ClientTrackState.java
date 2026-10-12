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
            scrubTimeMillis = Long.MIN_VALUE;
            scrubRatio = 1.0;
            viewEndRatio = 1.0;
        }
        if (id.equals(tracked)) {
            for (Payloads.NetPoint np : data.points()) {
                points.add(new TrackPoint(np.t(), np.x(), np.y(), np.z(),
                        ResourceLocation.tryParse(np.dim()), np.v(), np.yaw(), np.pitch()));
            }
            points.sort(java.util.Comparator.comparingLong(TrackPoint::timeMillis));
            refreshView();
            reanchorScrub();   // 轨迹增长时游标按绝对时刻重锚定，不漂移
        }
    }

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
        refreshView();
        scrubTimeMillis = Long.MIN_VALUE;
        scrubRatio = 1.0;
        viewEndRatio = 1.0;
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
    }

    /** 重置（断开连接时）。 */
    public void reset() {
        serverMode = false;
        whitelist.clear();
        tracked = null;
        points.clear();
        refreshView();
        trackedSet.clear();
        scrubRatio = 1.0;
        viewEndRatio = 1.0;
        viewSpanRatio = 0.15;
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
                e.addProperty("d", p.dimension().toString());
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

    // ==================== 发送便捷方法 ====================

    public static void sendToServer(Object payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    (net.minecraft.network.protocol.common.custom.CustomPacketPayload) payload);
        }
    }
}
