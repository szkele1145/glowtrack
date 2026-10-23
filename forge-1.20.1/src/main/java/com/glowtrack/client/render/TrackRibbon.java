package com.glowtrack.client.render;

import com.glowtrack.track.TrackPoint;

import java.util.List;

/**
 * 轨迹带的三角几何构造（把折线变成有真实宽度的带子）。
 *
 * <p><b>为什么不用 glLineWidth</b>：Minecraft 的 {@code RenderType.debugLineStrip(width)}
 * 最终落到 OpenGL 的 {@code glLineWidth}，而现代显卡驱动（尤其 core profile / Vulkan 后端）
 * 会把它钳到 1~2 像素——想要"更粗的线"只调大 width 参数是没用的。
 * 真正可控的做法是自己生成三角带：把折线按法线方向向两侧偏移半个线宽，
 * 每段生成两个三角形，宽度就真的由几何决定。</p>
 *
 * <p>带子的顶点序列：每个折线点产出 L/R 两个顶点，相邻点之间组成两个三角形
 * （L0,R0,L1 和 R0,R1,L1），共线时法线退化为任意垂线。</p>
 */
public final class TrackRibbon {

    /** 单条带子的顶点：x,y,z + r,g,b,a（每点 7 个 float）。 */
    public static final int STRIDE = 7;

    private TrackRibbon() {}

    /**
     * 为一段折线生成带子顶点。
     *
     * @param pts      折线点（x,z 为平面坐标，y 由调用方给出）
     * @param y        统一的 y 高度（2D 模式用平面高度；3D 模式应逐点取 p.y()）
     * @param width    线宽（世界坐标格数）
     * @param colors   每个点对应的 RGBA（长度须等于 pts.size()）
     * @param out      输出缓冲（复用，避免每帧分配）
     * @return 写入的顶点数
     */
    public static int build(List<TrackPoint> pts, boolean use3d, double flatY,
                            float width, float[] colors, float[] out) {
        int n = pts.size();
        if (n < 2) return 0;
        float half = width * 0.5f;

        int w = 0;
        for (int i = 0; i < n; i++) {
            TrackPoint p = pts.get(i);
            double px = p.x(), pz = p.z();
            double py = use3d ? p.y() : flatY;

            // 段方向：前向（p[i]-p[i-1]）与后向（p[i+1]-p[i]）平均，得到平滑法线
            double dx = 0, dz = 0;
            if (i > 0) {
                double ax = px - pts.get(i - 1).x(), az = pz - pts.get(i - 1).z();
                double len = Math.sqrt(ax * ax + az * az);
                if (len > 1e-6) { dx += ax / len; dz += az / len; }
            }
            if (i < n - 1) {
                double bx = pts.get(i + 1).x() - px, bz = pts.get(i + 1).z() - pz;
                double len = Math.sqrt(bx * bx + bz * bz);
                if (len > 1e-6) { dx += bx / len; dz += bz / len; }
            }
            // 平面法线 = (-dz, dx)，长度归一
            double nl = Math.sqrt(dx * dx + dz * dz);
            double nx, nz;
            if (nl < 1e-6) { nx = 1; nz = 0; }   // 退化：任意垂线
            else { nx = -dz / nl; nz = dx / nl; }

            float cr = colors[i * 4], cg = colors[i * 4 + 1], cb = colors[i * 4 + 2], ca = colors[i * 4 + 3];

            // 左顶点
            out[w++] = (float) (px + nx * half);
            out[w++] = (float) py;
            out[w++] = (float) (pz + nz * half);
            out[w++] = cr; out[w++] = cg; out[w++] = cb; out[w++] = ca;
            // 右顶点
            out[w++] = (float) (px - nx * half);
            out[w++] = (float) py;
            out[w++] = (float) (pz - nz * half);
            out[w++] = cr; out[w++] = cg; out[w++] = cb; out[w++] = ca;
        }
        return w / STRIDE;
    }
}
