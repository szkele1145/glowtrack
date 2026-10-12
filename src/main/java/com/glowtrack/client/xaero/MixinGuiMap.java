package com.glowtrack.client.xaero;

import com.glowtrack.client.ClientSettings;
import com.glowtrack.client.ClientTrackState;
import com.glowtrack.track.TrackPoint;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

import java.util.List;

/**
 * 注入 Xaero 世界地图渲染，在地图上叠加当前追踪轨迹线。
 *
 * <p><b>v1.2.1：重写 @Local 依赖。</b>经真实 jar（WM 1.47.0）反汇编核实：GuiMap.render 的
 * flooredCameraX/flooredCameraZ/renderTypeBuffers 三个局部变量在 TAIL 处【作用域已死】
 * （活跃区间终止于 ~9747，TAIL=10336），导致整个 mixin 静默回滚、世界地图轨迹失效。
 * 只有 matrixStack 存活到 TAIL。</p>
 *
 * <p>新方案：
 * <ul>
 *   <li>保留 @Local(name="matrixStack")（唯一 TAIL 存活的变量）。</li>
 *   <li>相机坐标改【反射读 private double cameraX/cameraZ 字段】（javap 核实存在，
 *       字段名跨版本稳定）后自行 Math.floor——不再依赖易死的局部变量。</li>
 *   <li>BufferSource 自建（Minecraft.renderBuffers()），与 Xaero 内部缓冲解耦。</li>
 * </ul>
 * 失败面从"任意局部变量变动"缩小到"字段被改名"。</p>
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class MixinGuiMap {

    @Inject(method = "render", at = @At("TAIL"))
    private void glowtrack$drawTrajectory(CallbackInfo ci,
            @com.llamalad7.mixinextras.sugar.Local(name = "matrixStack") PoseStack pose) {
        try {
            if (!ClientSettings.get().xaeroMap) return;
            List<TrackPoint> pts = ClientTrackState.get().pointsView();
            if (pts.size() < 2) return;

            // 相机坐标：反射读字段（跨版本比 LVT 稳定），失败则跳过本帧
            double camX, camZ;
            try {
                java.lang.reflect.Field fx = GuiMap.class.getDeclaredField("cameraX");
                java.lang.reflect.Field fz = GuiMap.class.getDeclaredField("cameraZ");
                fx.setAccessible(true); fz.setAccessible(true);
                camX = Math.floor(((Number) fx.get(this)).doubleValue());
                camZ = Math.floor(((Number) fz.get(this)).doubleValue());
            } catch (Throwable t) {
                return;   // 字段名变了（未来 Xaero 大更新）→ 本帧跳过，不崩
            }

            Minecraft mc = Minecraft.getInstance();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            var buf = buffers.getBuffer(RenderType.debugLineStrip(3.0f));
            int total = pts.size();
            for (int i = 1; i < total; i++) {
                TrackPoint a = pts.get(i - 1), b = pts.get(i);
                if (!a.inDimension(b.dimension())) continue;
                float[] c = com.glowtrack.client.render.TrackGeometry.hsvToRgb(i / (float) total, 0.9f, 0.65f);
                buf.addVertex(pose.last().pose(), (float) (a.x() - camX), 0f, (float) (a.z() - camZ)).setColor(c[0], c[1], c[2], 1f);
                buf.addVertex(pose.last().pose(), (float) (b.x() - camX), 0f, (float) (b.z() - camZ)).setColor(c[0], c[1], c[2], 1f);
            }
            buffers.endBatch();
        } catch (Throwable ignored) {
            // 运行期异常静默跳过（注入点已成功，仅数据不可用）
        }
    }
}
