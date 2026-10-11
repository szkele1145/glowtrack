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

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V",
            ordinal = 0))
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
            for (int i = 1; i < pts.size(); i++) {
                TrackPoint a = pts.get(i - 1), b = pts.get(i);
                if (!a.inDimension(b.dimension())) continue;
                float[] c = heat(b.speed());
                buf.addVertex(mat, (float) a.x(), 0f, (float) a.z()).setColor(c[0], c[1], c[2], 1f);
                buf.addVertex(mat, (float) b.x(), 0f, (float) b.z()).setColor(c[0], c[1], c[2], 1f);
            }
            pose.popPose();
        } catch (Throwable ignored) {
            // Xaero 版本差异导致坐标/结构不符时静默跳过
        }
    }

    /** 速度 → 彩虹热力色（与世界渲染器一致）。 */
    private static float[] heat(float speed) {
        float v = Math.max(0f, Math.min(1f, speed / 3.0f));
        float h = (1f - v) * 0.66f;
        // HSL→RGB
        float s = 0.9f, l = 0.55f;
        float r, g, b;
        if (s == 0) { r = g = b = l; }
        else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hue(p, q, h + 1f / 3f); g = hue(p, q, h); b = hue(p, q, h - 1f / 3f);
        }
        return new float[]{r, g, b};
    }

    private static float hue(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 0.5f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }
}
