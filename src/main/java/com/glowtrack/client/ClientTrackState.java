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
        }
        if (id.equals(tracked)) {
            for (Payloads.NetPoint np : data.points()) {
                points.add(new TrackPoint(np.t(), np.x(), np.y(), np.z(),
                        ResourceLocation.tryParse(np.dim()), np.v(), np.yaw(), np.pitch()));
            }
            points.sort(java.util.Comparator.comparingLong(TrackPoint::timeMillis));
        }
    }

    /** 本地降级模式：追加一个采样点。 */
    public void localAppend(TrackPoint p) {
        if (tracked == null) return;
        if (points.isEmpty() || p.timeMillis() >= points.get(points.size() - 1).timeMillis())
            points.add(p);
    }

    public void setLocalTracked(UUID id, String name) {
        tracked = id;
        points.clear();
        trackedSet.add(id);
    }

    public UUID tracked() { return tracked; }

    /** 只读视图：渲染器/GUI 遍历用，防止外部改动内部列表。 */
    public List<TrackPoint> points() { return java.util.Collections.unmodifiableList(points); }

    /** 渲染器用的只读视图（等价 points()，命名更清晰）。 */
    public List<TrackPoint> pointsView() { return java.util.Collections.unmodifiableList(points); }

    public boolean isTracking(UUID id) { return trackedSet.contains(id); }

    /** 时间轴游标位置（0..1）。 */
    public double scrubRatio() { return scrubRatio; }

    public void setScrubRatio(double r) {
        this.scrubRatio = Math.max(0.0, Math.min(1.0, r));
    }

    public double viewEndRatio() { return viewEndRatio; }
    public double viewSpanRatio() { return viewSpanRatio; }
    public double viewStartRatio() { return Math.max(0.0, viewEndRatio - viewSpanRatio); }

    /** 平移时间轴可见窗口（正=向最新，负=向历史）。 */
    public void panView(double deltaRatio) {
        viewEndRatio = Math.max(viewSpanRatio, Math.min(1.0, viewEndRatio + deltaRatio));
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
        trackedSet.clear();
        scrubRatio = 1.0;
        viewEndRatio = 1.0;
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
            java.nio.file.Path f = dir.resolve(safe + "_" + System.currentTimeMillis() + ".json");
            StringBuilder sb = new StringBuilder();
            sb.append("{\"player\":\"").append(tracked).append("\",\"name\":\"").append(safe)
              .append("\",\"points\":[");
            for (int i = 0; i < points.size(); i++) {
                TrackPoint p = points.get(i);
                if (i > 0) sb.append(',');
                sb.append("{\"t\":").append(p.timeMillis())
                  .append(",\"x\":").append(p.x()).append(",\"y\":").append(p.y())
                  .append(",\"z\":").append(p.z())
                  .append(",\"d\":\"").append(p.dimension()).append("\",\"v\":").append(p.speed())
                  .append('}');
            }
            sb.append("]}");
            java.nio.file.Files.writeString(f, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
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
