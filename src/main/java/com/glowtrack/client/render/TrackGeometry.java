package com.glowtrack.client.render;

import com.glowtrack.track.TrackPoint;
import com.glowtrack.track.TrackTimes;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨迹几何与着色工具（世界渲染 / Xaero 地图共用）。
 *
 * <ul>
 *   <li>维度分段：把按时间排序的点列切成当前维度的连续段</li>
 *   <li>彩虹着色：与世界渲染一致的 HSV 色环（可按距离或索引推进）</li>
 *   <li>共线抽稀：直线段跳过冗余顶点，长轨迹不卡帧</li>
 * </ul>
 */
public final class TrackGeometry {
    private TrackGeometry() {}

    /** hsvToRgb 的复用缓冲：渲染热路径每帧千次调用，避免逐顶点 new float[3] 的 GC 压力。非线程安全，仅渲染线程用。 */
    private static final float[] RGB = new float[3];

    /** 彩虹 HSV → RGB（h∈[0,1) 色相）。返回共享缓冲，调用方需立即消费。 */
    public static float[] hsvToRgb(float h, float s, float v) {
        float h6 = ((h % 1f) + 1f) % 1f * 6f;
        int i = (int) Math.floor(h6) % 6;
        float f = h6 - (float) Math.floor(h6);
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (i) {
            case 0: RGB[0] = v; RGB[1] = t; RGB[2] = p; break;
            case 1: RGB[0] = q; RGB[1] = v; RGB[2] = p; break;
            case 2: RGB[0] = p; RGB[1] = v; RGB[2] = t; break;
            case 3: RGB[0] = p; RGB[1] = q; RGB[2] = v; break;
            case 4: RGB[0] = t; RGB[1] = p; RGB[2] = v; break;
            default: RGB[0] = v; RGB[1] = p; RGB[2] = q; break;
        }
        return RGB;
    }

    /** 按维度切连续段，返回 [start, end) 下标对列表。 */
    public static List<int[]> contiguousRuns(List<TrackPoint> points, net.minecraft.resources.ResourceLocation dim) {
        List<int[]> runs = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < points.size(); i++) {
            boolean match = points.get(i).inDimension(dim);
            if (match && start < 0) start = i;
            else if (!match && start >= 0) { runs.add(new int[]{start, i}); start = -1; }
        }
        if (start >= 0) runs.add(new int[]{start, points.size()});
        return runs;
    }

    /** 点 p 到线段 a-b 的垂直距离（共线抽稀用）。 */
    public static double perpDist(TrackPoint a, TrackPoint p, TrackPoint b) {
        double abx = b.x() - a.x(), abz = b.z() - a.z();
        double apx = p.x() - a.x(), apz = p.z() - a.z();
        double len2 = abx * abx + abz * abz;
        if (len2 < 1e-9) return Math.sqrt(apx * apx + apz * apz);
        double t = Math.max(0, Math.min(1, (apx * abx + apz * abz) / len2));
        double cx = a.x() + t * abx - p.x(), cz = a.z() + t * abz - p.z();
        return Math.sqrt(cx * cx + cz * cz);
    }

    /** 找时间最接近 t 的点（透传 TrackTimes，供渲染层便捷调用）。 */
    public static TrackPoint nearestByTime(List<TrackPoint> pts, long t) {
        return TrackTimes.nearestByTime(pts, t);
    }
}
