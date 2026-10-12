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
import xaero.common.minimap.MinimapProcessor;

import java.util.List;

/**
 * 注入 Xaero 小地图渲染，在小地图上叠加当前追踪轨迹线。
 *
 * <p><b>v1.1.9：注入点从 MinimapFBORenderer.renderChunksToFBO 移到 MinimapProcessor.onRender。</b>
 * 原注入点与 XaeroPlus 在同一目标类上竞争 mixin 应用，实测导致 XaeroPlus 的
 * CustomMinimapFBORenderer 接口注入失效（ClassCastException 崩渲染线程）。
 * MinimapProcessor 在 Xaero 渲染链的最外层，XaeroPlus 未注入此类，零冲突。</p>
 *
 * <p>画线时机在 Xaero 自己的 FBO 渲染完成后（onRender 尾部），直接向
 * Tesselator 提交一个 debugLineStrip 批次叠加在屏幕空间上。</p>
 *
 * <p><b>防御式</b>：required=false + defaultRequire=0 + 整体 try/catch，
 * 任何局部变量缺失（Xaero 版本差异）静默跳过。</p>
 */
@Mixin(value = MinimapProcessor.class, remap = false)
public abstract class MixinMinimap {

    @Inject(method = "onRender", at = @At("TAIL"))
    private void glowtrack$drawOnMinimap(CallbackInfo ci,
            @Local(ordinal = 0) PoseStack pose,
            @Local(ordinal = 0) MultiBufferSource.BufferSource buffers,
            @Local(name = "xFloored") int xFloored,
            @Local(name = "zFloored") int zFloored) {
        try {
            if (!ClientSettings.get().xaeroMap) return;
            List<TrackPoint> pts = ClientTrackState.get().pointsView();
            if (pts.size() < 2) return;

            pose.pushPose();
            pose.translate(-xFloored, -zFloored, 0);
            var mat = pose.last().pose();
            var buf = buffers.getBuffer(RenderType.debugLineStrip(2.0f));
            int total = pts.size();
            for (int i = 1; i < total; i++) {
                TrackPoint a = pts.get(i - 1), b = pts.get(i);
                if (!a.inDimension(b.dimension())) continue;
                float[] c = com.glowtrack.client.render.TrackGeometry.hsvToRgb(i / (float) total, 0.9f, 0.65f);
                buf.addVertex(mat, (float) a.x(), 0f, (float) a.z()).setColor(c[0], c[1], c[2], 1f);
                buf.addVertex(mat, (float) b.x(), 0f, (float) b.z()).setColor(c[0], c[1], c[2], 1f);
            }
            buffers.endBatch();
            pose.popPose();
        } catch (Throwable ignored) {
            // Xaero 版本差异导致局部变量缺失时静默跳过
        }
    }
}
