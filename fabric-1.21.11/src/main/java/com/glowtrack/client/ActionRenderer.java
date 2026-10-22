package com.glowtrack.client;

import com.glowtrack.track.ActionEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 行为事件渲染（Fabric / 1.21.11）：回放时在事件位置显示方块线框/浮动标签（放/破/交互/开容器）。
 *
 * <p>触发条件：回放模式（isReplaying）显示游标时刻前 4 秒内的动作，随时间淡出；
 * 实时追踪模式显示最近 2 秒的新动作（看"他刚干了什么"）。</p>
 *
 * <p>性能：按事件时间戳二分定位窗口起点，窗口外跳过；每帧顶点数极小。</p>
 *
 * <p><b>1.21.9+ 的变化</b>：深度测试/混合状态不再由 {@code RenderSystem} 在运行时切换，
 * 而是声明在 {@code RenderPipeline} 里（见 {@code TrackRibbonTypes}）。
 * 这里的"穿墙显示"因此直接复用 RIBBON 管线（它本身就是关闭深度测试的）。</p>
 */
public final class ActionRenderer {
    private ActionRenderer() {}

    /** 回放模式窗口：游标前 4 秒内的事件显示。 */
    private static final long REPLAY_WINDOW_MS = 4000L;
    /** 实时模式窗口：最近 2 秒内的新动作显示。 */
    private static final long LIVE_WINDOW_MS = 2000L;

    public static void onRenderLevel(WorldRenderContext ctx) {
        com.glowtrack.client.ClientTrackState state = com.glowtrack.client.ClientTrackState.get();
        List<ActionEvent> actions = state.actionsView();
        if (actions.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (buffers == null) return;

        // 只渲染当前追踪目标的事件：客户端可能同时订阅多个目标，
        // 不按 tracked 过滤会把别人的事件也画出来
        java.util.UUID trackedId = state.tracked();
        String trackedStr = trackedId == null ? null : trackedId.toString();

        boolean replaying = state.isReplaying();
        long windowMs = replaying ? REPLAY_WINDOW_MS : LIVE_WINDOW_MS;

        // 当前"关注时刻"：回放=游标时刻；实时=现实时间
        long now;
        if (replaying) {
            List<com.glowtrack.track.TrackPoint> pts = state.pointsView();
            if (pts.isEmpty()) return;
            long t0 = pts.get(0).timeMillis(), t1 = pts.get(pts.size() - 1).timeMillis();
            now = t0 + (long) (state.scrubRatio() * (t1 - t0));
        } else {
            now = System.currentTimeMillis();
        }

        PoseStack pose = ctx.matrices();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = pose.last().pose();
        Font font = mc.font;

        // 二分定位窗口起点（actions 按 t 升序）
        int start = 0;
        while (start < actions.size() && actions.get(start).t() < now - windowMs) start++;

        for (int i = start; i < actions.size(); i++) {
            ActionEvent e = actions.get(i);
            if (e.t() > now) break;
            // 过滤：只画当前追踪目标的事件（trackedStr 为 null 表示没有追踪目标）
            if (trackedStr == null || !trackedStr.equals(e.playerId())) continue;
            float age = (now - e.t()) / (float) windowMs;   // 0..1
            float alpha = 0.85f * (1.0f - age);
            if (alpha <= 0.05f) continue;

            int color = switch (e.type()) {
                case "place" -> 0x00FFFF;   // 放置=青
                case "break" -> 0xFF5555;   // 破坏=红
                case "open", "interact" -> 0xFFD700;  // 交互/打开=金
                default -> 0xFFFFFF;
            };
            float cr = ((color >> 16) & 0xFF) / 255f;
            float cg = ((color >> 8) & 0xFF) / 255f;
            float cb = (color & 0xFF) / 255f;

            var buf = buffers.getBuffer(com.glowtrack.client.render.TrackRibbonTypes.RIBBON);
            drawBoxEdges(buf, mat, e.x(), e.y(), e.z(), 1.02f, cr, cg, cb, alpha);

            // 浮动标签：动作描述
            String label = switch (e.type()) {
                case "place" -> "+ " + e.target();
                case "break" -> "- " + e.target();
                case "open" -> "≡ " + e.target();
                default -> "? " + e.target();
            };
            float ty = (float) (e.y() + 1.5 + age * 0.3);   // 随淡出上浮
            // 浮动标签用世界内 billboard 渲染（与 TrackRenderer.drawTimeLabels 同法）：
            pose.pushPose();
            pose.translate(e.x(), ty, e.z());
            // 1.21.9+：相机朝向由 Camera.rotation()（四元数）给出。
            pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
            float ls = 0.04f;
            pose.scale(ls, -ls, ls);
            float lx = -font.width(label) / 2f;
            font.drawInBatch(label, lx, 0f, 0xFFFFFF, true, pose.last().pose(),
                    buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                    LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }

        buffers.endBatch();
        pose.popPose();
    }

    /** 画单位立方体的 12 条边（中心在 x+0.5,y+0.5,z+0.5，半边长 half）。 */
    private static void drawBoxEdges(com.mojang.blaze3d.vertex.VertexConsumer buf, Matrix4f mat,
                                     double x, double y, double z, float half,
                                     float r, float g, float b, float a) {
        double x0 = x, x1 = x + half * 2;
        double y0 = y, y1 = y + half * 2;
        double z0 = z, z1 = z + half * 2;
        // 底面 4 条
        edge(buf, mat, x0,y0,z0, x1,y0,z0, r,g,b,a);
        edge(buf, mat, x1,y0,z0, x1,y0,z1, r,g,b,a);
        edge(buf, mat, x1,y0,z1, x0,y0,z1, r,g,b,a);
        edge(buf, mat, x0,y0,z1, x0,y0,z0, r,g,b,a);
        // 顶面 4 条
        edge(buf, mat, x0,y1,z0, x1,y1,z0, r,g,b,a);
        edge(buf, mat, x1,y1,z0, x1,y1,z1, r,g,b,a);
        edge(buf, mat, x1,y1,z1, x0,y1,z1, r,g,b,a);
        edge(buf, mat, x0,y1,z1, x0,y1,z0, r,g,b,a);
        // 竖 4 条
        edge(buf, mat, x0,y0,z0, x0,y1,z0, r,g,b,a);
        edge(buf, mat, x1,y0,z0, x1,y1,z0, r,g,b,a);
        edge(buf, mat, x1,y0,z1, x1,y1,z1, r,g,b,a);
        edge(buf, mat, x0,y0,z1, x0,y1,z1, r,g,b,a);
    }

    /** 棱线厚度（世界格）。方块边长只有 1 格，线太粗会糊成一团。 */
    private static final double EDGE_THICK = 0.12;

    /**
     * 画一条有真实厚度的棱：从 (x0,y0,z0) 到 (x1,y1,z1)，厚度 EDGE_THICK。
     *
     * <p>用垂直于线段方向的两个正交偏移向量构造矩形截面，保证任意方向
     * （水平/垂直/斜向）都能得到稳定厚度，而不是像 glLineWidth 那样被驱动钳成 1px。</p>
     */
    private static void edge(com.mojang.blaze3d.vertex.VertexConsumer buf, Matrix4f mat,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             float r, float g, float b, float a) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) return;
        double ux = dx / len, uy = dy / len, uz = dz / len;

        // 取一个与主方向不平行的辅助向量，做叉乘得到两个正交的偏移向量
        double ax, ay, az;
        if (Math.abs(uy) < 0.9) { ax = 0; ay = 1; az = 0; } else { ax = 1; ay = 0; az = 0; }
        double v1x = uy * az - uz * ay, v1y = uz * ax - ux * az, v1z = ux * ay - uy * ax;
        double l1 = Math.sqrt(v1x * v1x + v1y * v1y + v1z * v1z);
        if (l1 < 1e-9) return;
        v1x /= l1; v1y /= l1; v1z /= l1;
        double v2x = uy * v1z - uz * v1y, v2y = uz * v1x - ux * v1z, v2z = ux * v1y - uy * v1x;

        double h = EDGE_THICK * 0.5;
        double o1x = v1x * h, o1y = v1y * h, o1z = v1z * h;
        double o2x = v2x * h, o2y = v2y * h, o2z = v2z * h;

        // 两个四边形拼成"井"字截面（十字形），从任意角度看都有厚度
        quad(buf, mat,
                x0 + o1x, y0 + o1y, z0 + o1z,  x0 - o1x, y0 - o1y, z0 - o1z,
                x1 - o1x, y1 - o1y, z1 - o1z,  x1 + o1x, y1 + o1y, z1 + o1z, r, g, b, a);
        quad(buf, mat,
                x0 + o2x, y0 + o2y, z0 + o2z,  x0 - o2x, y0 - o2y, z0 - o2z,
                x1 - o2x, y1 - o2y, z1 - o2z,  x1 + o2x, y1 + o2y, z1 + o2z, r, g, b, a);
    }

    /** 一个四边形（QUADS 模式的 4 个顶点）。 */
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer buf, Matrix4f mat,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3,
                             float r, float g, float b, float a) {
        buf.addVertex(mat, (float) x0, (float) y0, (float) z0).setColor(r, g, b, a);
        buf.addVertex(mat, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        buf.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        buf.addVertex(mat, (float) x3, (float) y3, (float) z3).setColor(r, g, b, a);
    }
}
