package com.glowtrack.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * 轨迹带专用的两种 RenderType。
 *
 * <p><b>为什么要自定义</b>：主线与发光层需要不同的混合模式（普通 alpha vs 加色），
 * 而 {@code RenderType.debugQuads()} 只有一个实例——两个层次若共用同一 RenderType，
 * 就会落进同一个顶点缓冲，混合状态互相覆盖，发光层会被主线层的 alpha 吃掉。
 * Minecraft 的 {@code MultiBufferSource} 按 RenderType 分桶，所以必须用两个不同的实例。</p>
 *
 * <p><b>1.21.5+ 的变化</b>：深度测试 / 混合 / 剔除不再靠 {@code RenderSystem} 的
 * 全局开关，而是写进 {@code RenderPipeline}。1.21.8 还保留
 * {@code RenderType.CompositeState}，1.21.9+ 才换成 {@code RenderSetup}。</p>
 */
public final class TrackRibbonTypes {

    private TrackRibbonTypes() {}

    /** 主线层：标准 alpha 混合（半透明，能看到后面的方块），不写深度、不测深度。 */
    public static final RenderType RIBBON = RenderType.create(
            "glowtrack_ribbon",
            1536,
            RenderPipeline.builder()
                    .withLocation("glowtrack/ribbon")
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .build(),
            RenderType.CompositeState.builder().createCompositeState(false));

    /** 发光层：加色混合（src_alpha + one），形成光晕。 */
    public static final RenderType RIBBON_GLOW = RenderType.create(
            "glowtrack_ribbon_glow",
            1536,
            RenderPipeline.builder()
                    .withLocation("glowtrack/ribbon_glow")
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.ADDITIVE)
                    .withCull(false)
                    .build(),
            RenderType.CompositeState.builder().createCompositeState(false));
}
