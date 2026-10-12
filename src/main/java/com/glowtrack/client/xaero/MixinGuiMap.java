package com.glowtrack.client.xaero;

import com.glowtrack.client.ClientSettings;
import com.glowtrack.client.ClientTrackState;
import com.glowtrack.track.TrackPoint;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
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
 * <p>参考 XaeroPlus 的做法：在 {@code GuiMap.render} 的 {@code endBatch()} 处注入，
 * 拿到局部变量（相机坐标 / PoseStack / BufferSource），平移到相机原点后按世界坐标画线。</p>
 *
 * <p><b>防御式</b>：mixin 配置为 required=false、defaultRequire=0，且整体 try/catch，
 * 任何不匹配（Xaero 版本差异）只会静默跳过，绝不崩溃。</p>
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class MixinGuiMap {

    @Inject(method = "render", at = @At("TAIL"))
    private void glowtrack$drawTrajectory(CallbackInfo ci,
            @Local(name = "flooredCameraX") int camX,
            @Local(name = "flooredCameraZ") int camZ,
            @Local(name = "matrixStack") PoseStack pose,
            @Local(name = "renderTypeBuffers") MultiBufferSource.BufferSource buffers) {
        try {
            if (!ClientSettings.get().xaeroMap) return;
            List<TrackPoint> pts = ClientTrackState.get().pointsView();
            if (pts.size() < 2) return;

            pose.pushPose();
            pose.translate(-camX, -camZ, 1.0f);
            var mat = pose.last().pose();
            var buf = buffers.getBuffer(RenderType.debugLineStrip(3.0f));
            int total = pts.size();
            for (int i = 1; i < total; i++) {
                TrackPoint a = pts.get(i - 1), b = pts.get(i);
                if (!a.inDimension(b.dimension())) continue;
                float[] c = com.glowtrack.client.render.TrackGeometry.hsvToRgb(i / (float) total, 0.9f, 0.65f);   // 彩虹渐变
                buf.addVertex(mat, (float) a.x(), 0f, (float) a.z()).setColor(c[0], c[1], c[2], 1f);
                buf.addVertex(mat, (float) b.x(), 0f, (float) b.z()).setColor(c[0], c[1], c[2], 1f);
            }
            pose.popPose();
        } catch (Throwable ignored) {
            // Xaero 版本差异导致坐标/结构不符时静默跳过
        }
    }
}
