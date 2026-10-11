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

import java.util.ArrayList;
import java.util.List;

/**
 * 世界内轨迹渲染器。
 *
 * <p>支持 5 种着色风格（见 {@link RenderStyle}），以及发光、穿墙、
 * 时间标签、流动虚线、方向箭头、起终点标记等开关。所有参数来自
 * {@link ClientSettings}，GUI 可实时调整。</p>
 *
 * <p>渲染分层：发光层（宽、加色、半透明）→ 主线层 → 装饰（箭头/标记/标签）。</p>
 */
@EventBusSubscriber(modid = com.glowtrack.GlowTrack.MOD_ID, value = Dist.CLIENT)
public final class TrackRenderer {
    private TrackRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        ClientTrackState state = ClientTrackState.get();
        List<TrackPoint> points = state.pointsView();
        if (points.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ClientSettings s = ClientSettings.get();

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        ResourceLocation curDim = mc.player.level().dimension().location();
        List<int[]> runs = contiguousRuns(points, curDim);
        if (runs.isEmpty()) { pose.popPose(); return; }

        if (s.wallhack) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }

        long now = System.currentTimeMillis();

        // 发光层
        if (s.glow) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            drawRuns(pose, buffers, points, runs, s.lineWidth * 3.2f, 0.28f, true, now, curDim);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }

        // 主线层
        drawRuns(pose, buffers, points, runs, s.lineWidth, 1.0f, false, now, curDim);

        if (s.wallhack) {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }

        // 装饰层
        if (s.showArrows) drawArrows(pose, buffers, points, runs, curDim);
        if (s.showMarkers) drawMarkers(pose, buffers, points, runs, curDim);
        if (s.timeLabels) drawTimeLabels(pose, buffers, points, runs);

        buffers.endBatch();
        pose.popPose();
    }

    /** 切分"连续同维度"的段索引区间 [start,end)。 */
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

    /** 画轨迹线（发光层或主线层）。 */
    private static void drawRuns(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                 List<TrackPoint> points, List<int[]> runs,
                                 float width, float alpha, boolean isGlow, long now,
                                 ResourceLocation curDim) {
        VertexConsumer buf = buffers.getBuffer(RenderType.debugLineStrip(width));
        Matrix4f mat = pose.last().pose();
        ClientSettings s = ClientSettings.get();
        // 流动虚线相位（随时间推进）
        float phase = s.dashPulse ? (now % 2000L) / 2000f : 0f;
        int total = points.size();

        for (int[] run : runs) {
            int a0 = run[0], a1 = run[1];
            if (a1 - a0 < 2) continue;
            for (int i = a0 + 1; i < a1; i++) {
                // 流动虚线：按弧长位置 + 相位决定是否可见
                if (s.dashPulse && !isGlow) {
                    float u = (i - a0) / (float) (a1 - a0);
                    if (((u * 14f + phase) % 1f) < 0.5f) continue;
                }
                TrackPoint p0 = points.get(i - 1), p1 = points.get(i);
                float[] c0 = colorFor(p0, i - 1, a0, a1, total, now, s);
                float[] c1 = colorFor(p1, i, a0, a1, total, now, s);
                buf.addVertex(mat, (float) p0.x(), (float) p0.y(), (float) p0.z())
                   .setColor(c0[0], c0[1], c0[2], alpha);
                buf.addVertex(mat, (float) p1.x(), (float) p1.y(), (float) p1.z())
                   .setColor(c1[0], c1[1], c1[2], alpha);
            }
        }
    }

    /** 按当前风格计算某点颜色（0..1 RGB）。 */
    private static float[] colorFor(TrackPoint p, int idx, int runStart, int runEnd,
                                    int total, long now, ClientSettings s) {
        float hue = s.baseHue;
        switch (s.style) {
            case SOLID:
                break;
            case RAINBOW: {
                float u = (idx - runStart) / (float) Math.max(1, (runEnd - runStart));
                hue = s.baseHue + u;
                break;
            }
            case HEAT: {
                float v = clamp(p.speed() / 3.0f);
                return hslToRgb((1f - v) * 0.66f, 0.9f, 0.55f);
            }
            case TIME: {
                float u = (idx - runStart) / (float) Math.max(1, (runEnd - runStart));
                // 旧暗新亮：亮度随位置提升
                return hslToRgb(s.baseHue, 0.7f, 0.25f + 0.45f * u);
            }
            case PULSE: {
                float u = (idx - runStart) / (float) Math.max(1, (runEnd - runStart));
                float wave = (float) Math.sin((u * 8f - (now % 2000L) / 2000f * 8f) * Math.PI);
                float l = 0.45f + 0.35f * Math.max(0, wave);
                return hslToRgb(s.baseHue, 0.85f, l);
            }
        }
        return hslToRgb(hue % 1f, 0.85f, 0.6f);
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float[] hslToRgb(float h, float s, float l) {
        h = ((h % 1f) + 1f) % 1f;
        float r, g, b;
        if (s == 0) { r = g = b = l; }
        else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hue2(p, q, h + 1f / 3f);
            g = hue2(p, q, h);
            b = hue2(p, q, h - 1f / 3f);
        }
        return new float[]{r, g, b};
    }

    private static float hue2(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 0.5f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }

    /** 沿路径画方向箭头（每段若干个，取朝向）。 */
    private static void drawArrows(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                   List<TrackPoint> points, List<int[]> runs, ResourceLocation curDim) {
        VertexConsumer buf = buffers.getBuffer(RenderType.lines());
        Matrix4f mat = pose.last().pose();
        for (int[] run : runs) {
            int a0 = run[0], a1 = run[1];
            int step = Math.max(8, (a1 - a0) / 10);
            for (int i = a0 + step; i < a1; i += step) {
                TrackPoint p = points.get(i);
                TrackPoint q = points.get(i - 1);
                double dx = p.x() - q.x(), dy = p.y() - q.y(), dz = p.z() - q.z();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1e-3) continue;
                dx /= len; dy /= len; dz /= len;
                // 一个短箭头：主干 + 两翼
                double ax = p.x(), ay = p.y(), az = p.z();
                double sz = 0.6;
                line(buf, mat, ax, ay, az, ax - dx * sz, ay - dy * sz, az - dz * sz, 1f, 1f, 1f);
                // 翼（近似，取垂直方向）
                double px = -dz, py = 0, pz = dx;
                double plen = Math.sqrt(px * px + pz * pz);
                if (plen > 1e-3) {
                    px /= plen; pz /= plen;
                    double wing = sz * 0.5;
                    line(buf, mat, ax - dx * sz, ay - dy * sz, az - dz * sz,
                            ax - dx * sz + px * wing, ay - dy * sz, az - dz * sz + pz * wing, 1f, 1f, 1f);
                    line(buf, mat, ax - dx * sz, ay - dy * sz, az - dz * sz,
                            ax - dx * sz - px * wing, ay - dy * sz, az - dz * sz - pz * wing, 1f, 1f, 1f);
                }
            }
        }
    }

    /** 起点（绿）/ 终点（红）/ 当前游标点（白）标记。 */
    private static void drawMarkers(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                    List<TrackPoint> points, List<int[]> runs, ResourceLocation curDim) {
        VertexConsumer buf = buffers.getBuffer(RenderType.lines());
        Matrix4f mat = pose.last().pose();
        for (int[] run : runs) {
            TrackPoint first = points.get(run[0]);
            TrackPoint last = points.get(run[1] - 1);
            diamond(buf, mat, first.x(), first.y(), first.z(), 0.2f, 0.9f, 0.3f);
            diamond(buf, mat, last.x(), last.y(), last.z(), 1f, 0.25f, 0.25f);
        }
        // 当前游标点
        List<TrackPoint> pts = ClientTrackState.get().pointsView();
        if (!pts.isEmpty()) {
            double r = scrubRatio();
            TrackPoint cp = pts.get((int) Math.round(r * (pts.size() - 1)));
            if (cp.inDimension(curDim)) diamond(buf, mat, cp.x(), cp.y(), cp.z(), 1f, 1f, 1f);
        }
    }

    private static double scrubRatio() {
        return ClientTrackState.get().scrubRatio();
    }

    private static void diamond(VertexConsumer buf, Matrix4f mat, double x, double y, double z,
                                float r, float g, float b) {
        double s = 0.35;
        // 竖直十字菱形
        line(buf, mat, x, y + s, z, x + s, y, z, r, g, b);
        line(buf, mat, x + s, y, z, x, y - s, z, r, g, b);
        line(buf, mat, x, y - s, z, x - s, y, z, r, g, b);
        line(buf, mat, x - s, y, z, x, y + s, z, r, g, b);
    }

    private static void line(VertexConsumer buf, Matrix4f mat,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             float r, float g, float b) {
        buf.addVertex(mat, (float) x1, (float) y1, (float) z1).setColor(r, g, b, 1f);
        buf.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, b, 1f);
    }

    /** 沿轨迹画时刻标签（面向相机公告牌）。 */
    private static void drawTimeLabels(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                       List<TrackPoint> points, List<int[]> runs) {
        Minecraft mc = Minecraft.getInstance();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm");
        for (int[] run : runs) {
            int a0 = run[0], a1 = run[1];
            int interval = Math.max(1, (a1 - a0) / 8);
            for (int i = a0; i < a1; i += interval) {
                TrackPoint p = points.get(i);
                String text = fmt.format(new java.util.Date(p.timeMillis()));
                pose.pushPose();
                pose.translate(p.x(), p.y() + 0.6, p.z());
                pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                float scale = 0.02f;
                pose.scale(scale, -scale, scale);
                float x = -mc.font.width(text) / 2f;
                mc.font.drawInBatch(text, x, 0f, 0xFFFFFF, true, pose.last().pose(),
                        buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                        LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
        }
    }
}
