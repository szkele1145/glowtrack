package com.glowtrack.client.xaero;

import com.glowtrack.client.ClientSettings;
import com.glowtrack.client.ClientTrackState;
import com.glowtrack.track.TrackPoint;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.common.minimap.render.MinimapFBORenderer;

import java.util.List;

/**
 * 注入 Xaero 小地图渲染，在小地图上叠加当前追踪轨迹线。
 *
 * <p>参考 XaeroPlus：在 {@code MinimapFBORenderer.renderChunksToFBO} 内
 * 钩住 {@code SupportXaeroWorldmap.drawMinimap} 调用，拿到玩家中心坐标与
 * PoseStack/BufferSource，平移 {@code (-xFloored, -zFloored, 0)} 后按世界坐标画线。</p>
 *
 * <p><b>防御式</b>：required=false + try/catch，Xaero 版本不符时静默跳过。</p>
 */
@Mixin(value = MinimapFBORenderer.class, remap = false)
public abstract class MixinMinimap {

    @WrapOperation(method = "renderChunksToFBO", at = @At(value = "INVOKE",
            target = "Lxaero/common/mods/SupportXaeroWorldmap;drawMinimap(Lxaero/hud/minimap/module/MinimapSession;Lcom/mojang/blaze3d/vertex/PoseStack;Lxaero/common/minimap/render/MinimapRendererHelper;IIIIIIZDDLcom/mojang/blaze3d/vertex/VertexConsumer;Lxaero/common/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;)V"))
    private void glowtrack$drawOnMinimap(Object instance, Object session, PoseStack pose, Object helper,
                                          int xFloored, int zFloored, int minX, int minZ, int maxX, int maxZ,
                                          boolean zooming, double zoom, double dimScale,
                                          Object overlayBuf, Object multiTex, Operation<Void> original,
                                          @Local(name = "renderTypeBuffers") MultiBufferSource.BufferSource buffers) {
        // 先让 Xaero 正常画完小地图
        original.call(instance, session, pose, helper, xFloored, zFloored, minX, minZ, maxX, maxZ,
                zooming, zoom, dimScale, overlayBuf, multiTex);
        try {
            if (!ClientSettings.get().xaeroMap) return;
            List<TrackPoint> pts = ClientTrackState.get().pointsView();
            if (pts.size() < 2) return;

            pose.pushPose();
            pose.translate(-xFloored, -zFloored, 0);
            var mat = pose.last().pose();
            var buf = buffers.getBuffer(RenderType.debugLineStrip(2.0f));
            for (int i = 1; i < pts.size(); i++) {
                TrackPoint a = pts.get(i - 1), b = pts.get(i);
                if (!a.inDimension(b.dimension())) continue;
                float[] c = heat(b.speed());
                buf.addVertex(mat, (float) a.x(), 0f, (float) a.z()).setColor(c[0], c[1], c[2], 1f);
                buf.addVertex(mat, (float) b.x(), 0f, (float) b.z()).setColor(c[0], c[1], c[2], 1f);
            }
            pose.popPose();
        } catch (Throwable ignored) {
        }
    }

    private static float[] heat(float speed) {
        float v = Math.max(0f, Math.min(1f, speed / 3.0f));
        float h = (1f - v) * 0.66f, s = 0.9f, l = 0.55f;
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
