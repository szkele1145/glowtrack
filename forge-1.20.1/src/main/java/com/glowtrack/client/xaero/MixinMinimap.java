package com.glowtrack.client.xaero;

import com.glowtrack.GlowTrack;
import com.glowtrack.client.ClientSettings;
import com.glowtrack.client.ClientTrackState;
import com.glowtrack.track.TrackPoint;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.MinimapProcessor;

import java.util.List;

/**
 * 注入 Xaero 小地图渲染，在小地图上叠加当前追踪轨迹线。
 *
 * <p><b>v1.2.1：彻底重写。</b>原方案（v1.1.9 逃离 XaeroPlus 冲突时迁移到 onRender TAIL +
 * @Local 捕获局部变量）经真实 jar 反汇编核实 100% 注入失败——onRender 是 135 字节薄委托，
 * 根本没有 PoseStack/BufferSource/xFloored 局部变量，@Local 解析失败导致整个 mixin 静默回滚。</p>
 *
 * <p>新方案与 Xaero 内部实现完全解耦：
 * <ul>
 *   <li>注入点仍是 MinimapProcessor.onRender TAIL（XaeroPlus 不碰此类），但只用【参数捕获】
 *       （只依赖方法描述符，remap=false 下最稳），不碰任何局部变量。</li>
 *   <li>相机坐标用 GlowTrack 自己的 ClientTrackState（玩家自身位置），不依赖 Xaero 内部变量。</li>
 *   <li>缩放从 MinimapProcessor.getMinimapZoom()（public，javap 核实存在）读取换算。</li>
 *   <li>PoseStack/BufferSource 自建（new PoseStack + Minecraft.renderBuffers()），
 *       在屏幕空间直接画线。</li>
 * </ul>
 * 任何 Xaero 26.x 内部变动都不再影响注入——失败面缩小到"方法本身被改名"。</p>
 */
@Mixin(value = MinimapProcessor.class, remap = false)
public abstract class MixinMinimap {

    @Inject(method = "onRender(net.minecraft.client.gui.GuiGraphics;IIIIDIIFLxaero/common/graphics/CustomVertexConsumers;)V", at = @At("TAIL"))
    private void glowtrack$drawOnMinimap(net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, int width, int height,
                                         double scale, int size, int boxSize, float partial,
                                         xaero.common.graphics.CustomVertexConsumers cvc, CallbackInfo ci) {
        try {
            XaeroMixinPlugin.markActivated("MixinMinimap");
            if (!ClientSettings.get().xaeroMap) return;
            List<TrackPoint> pts = ClientTrackState.get().pointsView();
            if (pts.size() < 2) return;

            Minecraft mc = Minecraft.getInstance();
            Player self = mc.player;
            if (self == null) return;

            // 相机坐标：用玩家自身位置（GlowTrack 数据源），与 Xaero 内部解耦
            double camX = self.getX(), camZ = self.getZ();
            // 小地图缩放：读 public getter，1x-8x 区间
            double zoom;
            try {
                zoom = ((MinimapProcessor) (Object) this).getMinimapZoom();
            } catch (Throwable t) {
                zoom = 1.0;
            }
            if (zoom <= 0) zoom = 1.0;

            // 小地图画面中心与半径
            int cx = x + width / 2;
            int cy = y + height / 2;
            int radius = Math.min(width, height) / 2 - 4;
            if (radius <= 4) return;

            // 世界坐标 → 小地图像素：缩小地图每格 = 2^zoom 像素刻度（Xaero 语义）
            double pxPerBlock = Math.pow(2.0, zoom) / 2.0;

            PoseStack pose = new PoseStack();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            // 小地图轨迹线：三角带（真实厚度）——glLineWidth 在现代驱动上被钳成 1px
            var buf = buffers.getBuffer(com.glowtrack.client.render.TrackRibbonTypes.RIBBON);
            // 用户设置的世界线宽按比例映射到地图像素（最低 1.5px，保证远缩放下仍可见）
            float lineWidth = Math.max(1.5f, ClientSettings.get().lineWidth * 0.6f);

            boolean drewAny = false;
            int total = pts.size();
            // 逐段画：只画段内线（不做大范围剔除——段数有限）
            for (int i = 1; i < total; i++) {
                TrackPoint a = pts.get(i - 1), b = pts.get(i);
                if (!a.inDimension(b.dimension())) continue;
                if (!a.inDimension(self.level().dimension().location())) continue;
                // 画面坐标
                float ax = (float) (cx + (a.x() - camX) * pxPerBlock);
                float ay = (float) (cy + (a.z() - camZ) * pxPerBlock);
                float bx = (float) (cx + (b.x() - camX) * pxPerBlock);
                float by = (float) (cy + (b.z() - camZ) * pxPerBlock);
                // 粗剔除：两点都在半径 2 去外则跳过
                if ((Math.abs(ax - cx) > radius * 2 && Math.abs(bx - cx) > radius * 2)
                        || (Math.abs(ay - cy) > radius * 2 && Math.abs(by - cy) > radius * 2)) continue;
                float[] c = com.glowtrack.client.render.TrackGeometry.hsvToRgb(i / (float) total, 0.9f, 0.65f);
                // 段法线（屏幕平面内垂直于线段）
                double ddx = bx - ax, ddy = by - ay;
                double len = Math.sqrt(ddx * ddx + ddy * ddy);
                if (len < 1e-9) continue;
                double nx = -ddy / len, ny = ddx / len;
                float half = lineWidth * 0.5f;
                var mat = pose.last().pose();
                buf.vertex(mat, ax + (float) (nx * half), ay + (float) (ny * half), 0f).color(c[0], c[1], c[2], 1f);
                buf.vertex(mat, ax - (float) (nx * half), ay - (float) (ny * half), 0f).color(c[0], c[1], c[2], 1f);
                buf.vertex(mat, bx - (float) (nx * half), by - (float) (ny * half), 0f).color(c[0], c[1], c[2], 1f);
                buf.vertex(mat, bx + (float) (nx * half), by + (float) (ny * half), 0f).color(c[0], c[1], c[2], 1f);
                drewAny = true;
            }
            if (drewAny) buffers.endBatch();
        } catch (Throwable ignored) {
            // Xaero 内部结构变动时静默跳过（此时注入点已成功，只是运行期数据不可用）
        }
    }
}
