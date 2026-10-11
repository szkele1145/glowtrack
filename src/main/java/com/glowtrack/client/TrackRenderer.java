package com.glowtrack.client;

import com.glowtrack.track.TrackPoint;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 世界内轨迹渲染器。
 *
 * <p>效果（选品既定）：速度热力彩虹着色 + 发光光晕 + 穿墙 + 时间标签 + 流动脉冲。</p>
 *
 * <p><b>关键：线条用连续条带（debugLineStrip）一次性画完整段，绝不断跳顶点</b>
 * ——否则条带会把断口斜连，产生乱飞的抽风线。流动感改用逐顶点亮度波实现，
 * 保持条带连续、平滑。</p>
 */
@EventBusSubscriber(modid = com.glowtrack.GlowTrack.MOD_ID, value = Dist.CLIENT)
public final class TrackRenderer {
    private TrackRenderer() {}

    private static final float LINE_WIDTH = 5.0f;
    private static final double FLAT_OFFSET = 5.0;      // 2D 模式：观察者高度下方 5 格
    private static final double CLUSTER_RADIUS = 24.0;  // 时间点融合空间半径

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        List<TrackPoint> points = ClientTrackState.get().pointsView();
        if (points.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (buffers == null) return;
        ClientSettings s = ClientSettings.get();

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        ResourceLocation curDim = mc.player.level().dimension().location();
        List<int[]> runs = contiguousRuns(points, curDim);
        if (runs.isEmpty()) { pose.popPose(); return; }

        double flatY = mc.player.getY() - FLAT_OFFSET;
        long now = System.currentTimeMillis();

        // 穿墙
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        // 发光层（宽、加色）
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        if (s.mode3d) drawLine(pose, buffers, points, runs, LINE_WIDTH * 3.4f, 0.26f, true, now, true, 0);
        if (s.mode2d) drawLine(pose, buffers, points, runs, LINE_WIDTH * 3.4f, 0.26f, true, now, false, flatY);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        // 主线层（细、含流动脉冲）
        if (s.mode3d) drawLine(pose, buffers, points, runs, LINE_WIDTH, 1.0f, false, now, true, 0);
        if (s.mode2d) drawLine(pose, buffers, points, runs, LINE_WIDTH, 1.0f, false, now, false, flatY);

        // 恢复深度，再画时间标签（避免与线条 buffer 混淆）
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        buffers.endBatch();   // 先 flush 线条

        if (s.mode3d) drawTimeLabels(pose, buffers, points, runs, true, 0);
        if (s.mode2d) drawTimeLabels(pose, buffers, points, runs, false, flatY);
        buffers.endBatch();

        pose.popPose();
    }

    private static List<int[]> contiguousRuns(List<TrackPoint> points, ResourceLocation curDim) {
        List<int[]> runs = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < points.size(); i++) {
            boolean match = points.get(i).inDimension(curDim);
            if (match && start < 0) start = i;
            else if (!match && start >= 0) { runs.add(new int[]{start, i}); start = -1; }
        }
        if (start >= 0) runs.add(new int[]{start, points.size()});
        return runs;
    }

    /**
     * 画一条连续轨迹线。每个 run 用连续条带一次画完（不断跳顶点）。
     * 主线层用逐顶点亮度波制造流动脉冲，发光层保持均匀。
     */
    private static void drawLine(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                 List<TrackPoint> points, List<int[]> runs,
                                 float width, float alpha, boolean isGlow, long now,
                                 boolean use3d, double flatY) {
        VertexConsumer buf = buffers.getBuffer(RenderType.debugLineStrip(width));
        Matrix4f mat = pose.last().pose();
        float flow = (now % 3000L) / 3000f;   // 流动相位 0..1

        for (int[] run : runs) {
            int a0 = run[0], a1 = run[1];
            if (a1 - a0 < 2) continue;
            // 逐顶点写入，保持条带连续
            for (int i = a0; i < a1; i++) {
                TrackPoint p = points.get(i);
                float y = use3d ? (float) p.y() : (float) flatY;
                // 彩虹渐变：沿路径位置铺满色环（选品主题=彩虹）
                float u = (i - a0) / (float) (a1 - a0);
                float[] c = hsvToRgb(u, 0.9f, 0.65f);
                float a = alpha;
                if (!isGlow) {
                    // 流动脉冲：亮度沿路径波动并随时间推进（连续、无断口）
                    float wave = 0.6f + 0.4f * (float) Math.sin((u * 6f - flow * 6f) * Math.PI * 2 * 0.5);
                    a = alpha * wave;
                }
                buf.addVertex(mat, (float) p.x(), y, (float) p.z())
                   .setColor(c[0], c[1], c[2], a);
            }
        }
    }

    /** 时间标签：每 N 分钟一点，密集区融合为范围。 */
    private static void drawTimeLabels(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                       List<TrackPoint> points, List<int[]> runs,
                                       boolean use3d, double flatY) {
        Minecraft mc = Minecraft.getInstance();
        List<TimeLabel> labels = buildTimeLabels(points);
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
                    buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
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
        SimpleDateFormat f = new SimpleDateFormat("HH:mm");

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
                        f.format(new Date(minT)) + "~" + f.format(new Date(maxT)), c.get(0)));
            } else {
                for (int idx : c) {
                    TrackPoint p = points.get(idx);
                    out.add(new TimeLabel(p.x(), p.y(), p.z(), f.format(new Date(p.timeMillis())), idx));
                }
            }
        }
        return out;
    }

    private static double dist(TrackPoint a, TrackPoint b) {
        double dx = a.x() - b.x(), dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** HSV → RGB（h∈[0,1) 为色相，用于彩虹渐变）。 */
    private static float[] hsvToRgb(float h, float s, float v) {
        float h6 = ((h % 1f) + 1f) % 1f * 6f;
        int i = (int) Math.floor(h6) % 6;
        float f = h6 - (float) Math.floor(h6);
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        switch (i) {
            case 0: return new float[]{v, t, p};
            case 1: return new float[]{q, v, p};
            case 2: return new float[]{p, v, t};
            case 3: return new float[]{p, q, v};
            case 4: return new float[]{t, p, v};
            default: return new float[]{v, p, q};
        }
    }
}
