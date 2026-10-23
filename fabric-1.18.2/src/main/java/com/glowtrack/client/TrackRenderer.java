package com.glowtrack.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import com.glowtrack.track.TrackPoint;
import com.glowtrack.client.render.TrackRibbon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界内轨迹渲染器。
 *
 * <p>效果（选品既定）：速度热力彩虹着色 + 发光光晕 + 穿墙 + 时间标签 + 流动脉冲。</p>
 *
 * <p><b>关键：线条用连续条带（debugLineStrip）一次性画完整段，绝不断跳顶点</b>
 * ——否则条带会把断口斜连，产生乱飞的抽风线。流动感改用逐顶点亮度波实现，
 * 保持条带连续、平滑。</p>
 */public final class TrackRenderer {
    private TrackRenderer() {}

    // 线宽来自 ClientSettings.lineWidth（1~12 格，GUI 滑块可调）。
    // 光晕层固定 2.2 倍于主线；注意这是真实几何宽度，不是 glLineWidth——
    // 后者在现代驱动上被钳到 1~2 像素，调了也没用。
    private static final double FLAT_OFFSET = 5.0;      // 2D 模式：观察者高度下方 5 格
    private static final double CLUSTER_RADIUS = 24.0;  // 时间点融合空间半径

    /** 主线半宽（世界格）= 用户设置 / 2。渲染线程每次进入取最新值。 */
    private static float mainHalfWidth() {
        return ClientSettings.get().lineWidth * 0.5f;
    }    public static void onRenderLevel(WorldRenderContext ctx) {
        ClientTrackState state = ClientTrackState.get();
        List<TrackPoint> all = state.pointsView();
        if (all.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (buffers == null) return;
        ClientSettings s = ClientSettings.get();

        // 回放模式：只画游标之前的点（渲染到游标，回放动画核心）
        // 取前缀子列表 [0, cursorIdx]，下标空间与全列表一致（时间标签的 pointIndex 仍有效）
        List<TrackPoint> points = all;
        if (state.isReplaying()) {
            int cursorIdx = replayCursorIndex(all, state.scrubRatio());
            points = all.subList(0, Math.min(all.size(), cursorIdx + 1));
            if (points.isEmpty()) return;
        }

        PoseStack pose = ctx.matrixStack();
        Vec3 cam = ctx.camera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        ResourceLocation curDim = mc.player.getLevel().dimension().location();
        List<int[]> runs = com.glowtrack.client.render.TrackGeometry.contiguousRuns(points, curDim);
        if (runs.isEmpty()) { pose.popPose(); return; }

        double flatY = mc.player.getY() - FLAT_OFFSET;
        long now = System.currentTimeMillis();

        // 穿墙
        // 发光层（更宽、加色混合——混合状态由 RIBBON_GLOW 的 transparency shard 自己管）
        float mainW = mainHalfWidth() * 2f;
        if (s.mode3d) drawLine(pose, buffers, points, runs, mainW * 2.2f, 0.26f, true, now, true, 0);
        if (s.mode2d) drawLine(pose, buffers, points, runs, mainW * 2.2f, 0.26f, true, now, false, flatY);

        // 主线层（含流动脉冲）
        if (s.mode3d) drawLine(pose, buffers, points, runs, mainW, 1.0f, false, now, true, 0);
        if (s.mode2d) drawLine(pose, buffers, points, runs, mainW, 1.0f, false, now, false, flatY);

        // 恢复深度，再画时间标签（避免与线条 buffer 混淆）        buffers.endBatch();   // 先 flush 线条

        // 时间标签：用全列表构建缓存（回放中裁剪列表每帧变，避免缓存失效重建），
        // 但用裁剪后的 runs 过滤——回放时只显示游标之前的标签
        if (s.mode3d) drawTimeLabels(pose, buffers, all, runs, true, 0);
        if (s.mode2d) drawTimeLabels(pose, buffers, all, runs, false, flatY);
        buffers.endBatch();

        pose.popPose();
    }

    /** 回放模式：游标对应的全列表下标（时间比例 × (size-1)，与 TimelineWidget 渲染一致）。 */
    private static int replayCursorIndex(List<TrackPoint> all, double scrubRatio) {
        return Math.max(0, Math.min(all.size() - 1, (int) Math.round(scrubRatio * (all.size() - 1))));
    }

    /**
     * 画一条连续轨迹线。每个 run 用连续条带一次画完（不断跳顶点）。
     * 主线层用逐顶点亮度波制造流动脉冲，发光层保持均匀。
     * 长直线段自动抽稀（共线点距离线 > 0.05 才保留），千点轨迹不卡帧。
     *
     * <p><b>用三角几何画粗线</b>：{@code debugLineStrip} 走 OpenGL 的 glLineWidth，
     * 现代驱动会把它钳到 1~2 像素，"加粗"根本不起作用。这里改成自己生成
     * 三角带（每段折线向两侧偏移半个线宽形成四边形），线宽由几何真正决定。</p>
     */
    private static void drawLine(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                 List<TrackPoint> points, List<int[]> runs,
                                 float width, float alpha, boolean isGlow, long now,
                                 boolean use3d, double flatY) {
        // 主线与发光层用不同的 RenderType（= 不同的顶点缓冲 + 独立的混合状态）
        VertexConsumer buf = buffers.getBuffer(isGlow
                ? com.glowtrack.client.render.TrackRibbonTypes.RIBBON_GLOW
                : com.glowtrack.client.render.TrackRibbonTypes.RIBBON);
        Matrix4f mat = pose.last().pose();
        float flow = (now % 3000L) / 3000f;   // 流动相位 0..1

        // 复用缓冲，避免每帧分配（渲染热路径）
        float[] colors = new float[4096 * 4];
        float[] verts = new float[4096 * 2 * TrackRibbon.STRIDE];
        int[] idx = new int[4096];

        for (int[] run : runs) {
            int a0 = run[0], a1 = run[1];
            if (a1 - a0 < 2) continue;

            // 1) 收集本段保留的点（抽稀后）到紧凑数组
            int n = 0;
            double cumDist = 0;   // 沿路径累计距离
            int prevKept = a0;
            for (int i = a0; i < a1; i++) {
                TrackPoint p = points.get(i);
                if (i > a0) cumDist += points.get(i - 1).distanceTo(p);
                // 共线抽稀：当前点与保留的前一点、后一点近似共线（偏差<0.05格）则跳过绘制，
                // 但至少保留段首/段尾/每8个点一个，保证曲线细节与标签锚点不丢
                if (i > a0 && i < a1 - 1 && (i - prevKept) < 8) {
                    TrackPoint prev = points.get(prevKept);
                    TrackPoint next = points.get(Math.min(i + 1, a1 - 1));
                    if (com.glowtrack.client.render.TrackGeometry.perpDist(prev, p, next) < 0.05) continue;
                }
                prevKept = i;
                if (n >= idx.length) break;
                idx[n] = i;
                // 彩虹：每 3 格距离走完一轮色环（快速流动的彩色）
                float u = (float) ((cumDist / 3.0) % 1.0);
                float[] c = com.glowtrack.client.render.TrackGeometry.hsvToRgb(u, 0.9f, 0.65f);
                float a = alpha;
                if (!isGlow) {
                    // 流动脉冲：亮度沿路径波动并随时间推进（连续、无断口）
                    float wave = 0.6f + 0.4f * (float) Math.sin((cumDist * 1.2 - flow * 12) * Math.PI);
                    a = alpha * wave;
                }
                colors[n * 4] = c[0]; colors[n * 4 + 1] = c[1]; colors[n * 4 + 2] = c[2]; colors[n * 4 + 3] = a;
                n++;
            }
            if (n < 2) continue;

            // 2) 生成带子顶点（每点 L/R 两个顶点）
            List<TrackPoint> sub = new ArrayList<>(n);
            for (int k = 0; k < n; k++) sub.add(points.get(idx[k]));
            float[] segColors = new float[n * 4];
            System.arraycopy(colors, 0, segColors, 0, n * 4);
            int vCount = com.glowtrack.client.render.TrackRibbon.build(sub, use3d, flatY, width, segColors, verts);
            if (vCount < 2) continue;

            // 3) 写成 quad：每对相邻点组成一个四边形（L0,R0,L1,R1）
            for (int k = 0; k + 1 < vCount; k += 2) {
                int b0 = k * TrackRibbon.STRIDE;
                int b1 = (k + 1) * TrackRibbon.STRIDE;
                putVert(buf, mat, verts, b0);
                putVert(buf, mat, verts, b0 + TrackRibbon.STRIDE);
                putVert(buf, mat, verts, b1);
                putVert(buf, mat, verts, b1 + TrackRibbon.STRIDE);
            }
        }
    }

    private static void putVert(VertexConsumer buf, Matrix4f mat, float[] v, int o) {
        buf.vertex(v[o], v[o + 1], v[o + 2])
           .color(v[o + 3], v[o + 4], v[o + 5], v[o + 6]);
    }

    /** 点 p 到线段 a-b 的垂直距离（用于共线抽稀）。已移至 TrackGeometry。 */

    /** 时间标签缓存：points 尺寸/首末时间戳/间隔设置未变时复用（渲染热路径，每帧省一次全量聚类）。 */
    private static List<TimeLabel> labelCache = new ArrayList<>();
    private static int labelCacheSize = -1;
    private static long labelCacheT0, labelCacheT1;
    private static int labelCacheInterval;

    /** 时间标签：每 N 分钟一点，密集区融合为范围。 */
    private static void drawTimeLabels(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                       List<TrackPoint> points, List<int[]> runs,
                                       boolean use3d, double flatY) {
        Minecraft mc = Minecraft.getInstance();
        int interval = Math.max(5, ClientSettings.get().timePointSeconds);
        long t0 = points.get(0).timeMillis(), t1 = points.get(points.size() - 1).timeMillis();
        if (points.size() != labelCacheSize || t0 != labelCacheT0 || t1 != labelCacheT1
                || interval != labelCacheInterval) {
            labelCache = buildTimeLabels(points);
            labelCacheSize = points.size();
            labelCacheT0 = t0; labelCacheT1 = t1; labelCacheInterval = interval;
        }
        List<TimeLabel> labels = labelCache;
        for (TimeLabel l : labels) {
            if (!inAnyRun(runs, l.pointIndex)) continue;
            double yy = use3d ? l.y : flatY;
            pose.pushPose();
            pose.translate(l.x, yy + 0.8, l.z);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            float scale = 0.03f;
            pose.scale(scale, -scale, scale);
            float tx = -mc.font.width(l.text) / 2f;
            mc.font.drawInBatch(l.text, tx, 0f, 0xFFFFFF, true, pose.last().pose(),
                    buffers, true,
                    LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }

    private static boolean inAnyRun(List<int[]> runs, int idx) {
        for (int[] r : runs) if (idx >= r[0] && idx < r[1]) return true;
        return false;
    }

    private record TimeLabel(double x, double y, double z, String text, int pointIndex) {}

    private static List<TimeLabel> buildTimeLabels(List<TrackPoint> points) {
        ClientSettings s = ClientSettings.get();
        List<TimeLabel> out = new ArrayList<>();
        if (points.isEmpty()) return out;
        long intervalMs = Math.max(5, s.timePointSeconds) * 1000L;
        // 时间标签统一显示到秒：时:分:秒（公共工具）
        java.util.function.LongFunction<String> f = com.glowtrack.track.TrackTimes::hms;

        List<Integer> anchors = new ArrayList<>();
        long last = Long.MIN_VALUE;
        for (int i = 0; i < points.size(); i++) {
            long t = points.get(i).timeMillis();
            if (last == Long.MIN_VALUE || t - last >= intervalMs) { anchors.add(i); last = t; }
        }

        List<List<Integer>> clusters = new ArrayList<>();
        List<Integer> cur = null;
        int prev = -1;
        for (int idx : anchors) {
            if (cur == null || prev < 0 || dist(points.get(prev), points.get(idx)) > CLUSTER_RADIUS) {
                cur = new ArrayList<>();
                clusters.add(cur);
            }
            cur.add(idx);
            prev = idx;
        }

        int mergeAt = Math.max(1, s.mergeThreshold);
        for (List<Integer> c : clusters) {
            if (c.size() > mergeAt) {
                double cx = 0, cy = 0, cz = 0;
                long minT = Long.MAX_VALUE, maxT = Long.MIN_VALUE;
                for (int idx : c) {
                    TrackPoint p = points.get(idx);
                    cx += p.x(); cy += p.y(); cz += p.z();
                    minT = Math.min(minT, p.timeMillis());
                    maxT = Math.max(maxT, p.timeMillis());
                }
                int n = c.size();
                out.add(new TimeLabel(cx / n, cy / n, cz / n,
                        f.apply(minT) + "~" + f.apply(maxT), c.get(0)));
            } else {
                for (int idx : c) {
                    TrackPoint p = points.get(idx);
                    out.add(new TimeLabel(p.x(), p.y(), p.z(), f.apply(p.timeMillis()), idx));
                }
            }
        }
        return out;
    }

    private static double dist(TrackPoint a, TrackPoint b) {
        double dx = a.x() - b.x(), dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
