package com.glowtrack.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * 轨迹带专用的两种 RenderType。
 *
 * <p><b>为什么要自定义</b>：主线与发光层需要不同的混合模式（普通 alpha vs 加色），
 * 而 {@code RenderType.debugQuads()} 只有一个实例——两个层次若共用同一 RenderType，
 * 就会落进同一个顶点缓冲，混合状态互相覆盖，发光层会被主线层的 alpha 吃掉。
 * Minecraft 的 {@code MultiBufferSource} 按 RenderType 分桶，所以必须用两个不同的实例。</p>
 *
 * <p>这里用 {@code POSITION_COLOR} + {@code QUADS} 模式自建，与 debugQuads 同规格，
 * 但混合方式可以各自独立配置。</p>
 */
public final class TrackRibbonTypes {

    private TrackRibbonTypes() {}

    /** 主线层：标准 alpha 混合（半透明，能看到后面的方块）。 */
    public static final RenderType RIBBON = RenderType.create(
            "glowtrack_ribbon",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderType.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    /** 发光层：加色混合（src_alpha + one），形成光晕。 */
    public static final RenderType RIBBON_GLOW = RenderType.create(
            "glowtrack_ribbon_glow",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderType.POSITION_COLOR_SHADER)
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard("glowtrack_additive", () -> {
                        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                        com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                    }, () -> {
                        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                    }))
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
}
