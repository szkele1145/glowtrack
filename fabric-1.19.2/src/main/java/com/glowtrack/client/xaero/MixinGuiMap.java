package com.glowtrack.client.xaero;

import com.glowtrack.client.ClientSettings;
import com.glowtrack.client.ClientTrackState;
import com.glowtrack.track.TrackPoint;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

import java.util.List;

/**
 * 注入 Xaero 世界地图渲染，在地图上叠加当前追踪轨迹线。
 *
 * <p>相机坐标反射读 private {@code cameraX}/{@code cameraZ} 字段（跨版本比局部变量稳定）；
 * 矩阵栈直接取注入方法的第一个参数（{@code renderPreDropdown} 的 PoseStack）。</p>
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class MixinGuiMap {

    
    // 注入点只写方法名、不带描述符：本 mixin 标了 remap = false，
    // 描述符里的 Minecraft 类型名不会被重映射（编译期是 mojmap，运行时是
    // intermediary），带描述符会永远匹配不上且静默回滚。
    // 目标方法无重载（javap 已确认），按名字注入是安全的。
    @Inject(method = "renderPreDropdown", at = @At("TAIL"))
    private void glowtrack$drawTrajectory(PoseStack pose, int mouseX, int mouseY,
                                          float partialTicks, CallbackInfo ci) {
        try {
            XaeroMixinPlugin.markActivated("MixinGuiMap");
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
            var buf = buffers.getBuffer(com.glowtrack.client.render.TrackRibbonTypes.RIBBON);
            int total = pts.size();
            float lineWidth = Math.max(2f, ClientSettings.get().lineWidth * 2f);
            for (int i = 1; i < total; i++) {
                TrackPoint a = pts.get(i - 1), b = pts.get(i);
                if (!a.inDimension(b.dimension())) continue;
                float[] c = com.glowtrack.client.render.TrackGeometry.hsvToRgb(i / (float) total, 0.9f, 0.65f);
                double ax = b.x() - a.x(), az = b.z() - a.z();
                double len = Math.sqrt(ax * ax + az * az);
                if (len < 1e-9) continue;
                double nx = -az / len, nz = ax / len;
                float half = lineWidth * 0.5f;
                float x0 = (float) (a.x() - camX), z0 = (float) (a.z() - camZ);
                float x1 = (float) (b.x() - camX), z1 = (float) (b.z() - camZ);
                var mat = pose.last().pose();
                buf.vertex(x0 + (float) (nx * half), 0f, z0 + (float) (nz * half)).color(c[0], c[1], c[2], 1f);
                buf.vertex(x0 - (float) (nx * half), 0f, z0 - (float) (nz * half)).color(c[0], c[1], c[2], 1f);
                buf.vertex(x1 - (float) (nx * half), 0f, z1 - (float) (nz * half)).color(c[0], c[1], c[2], 1f);
                buf.vertex(x1 + (float) (nx * half), 0f, z1 + (float) (nz * half)).color(c[0], c[1], c[2], 1f);
            }
            buffers.endBatch();
        } catch (Throwable ignored) {
            // 运行期异常静默跳过（注入点已成功，仅数据不可用）
        }
    }
}
