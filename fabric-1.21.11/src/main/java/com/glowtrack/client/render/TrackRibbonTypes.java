package com.glowtrack.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * 轨迹带专用的两种 RenderType（1.21.9+ / 1.21.11）。
 *
 * <p><b>为什么要自定义</b>：主线与发光层需要不同的混合模式（普通 alpha vs 加色），
 * 而 {@code RenderType.debugQuads()} 只有一个实例——两个层次若共用同一 RenderType，
 * 就会落进同一个顶点缓冲，混合状态互相覆盖，发光层会被主线层的 alpha 吃掉。
 * Minecraft 的 {@code MultiBufferSource} 按 RenderType 分桶，所以必须用两个不同的实例。</p>
 *
 * <p>这里用 {@code POSITION_COLOR} + {@code QUADS} 模式自建，与 debugQuads 同规格，
 * 但混合方式可以各自独立配置。</p>
 *
 * <p><b>1.21.9+ 的变化</b>：Mojang 把 {@code RenderType.CompositeState} / {@code RenderStateShard}
 * 整套换成了 {@code RenderPipeline} + {@code RenderSetup}。管线在构建期声明顶点格式、
 * 深度测试、混合等状态；{@code RenderSetup} 再补上纹理、光照贴图、分层等运行时信息。</p>
 */
public final class TrackRibbonTypes {

    private TrackRibbonTypes() {}

    /** 主线层：标准 alpha 混合（半透明，能看到后面的方块）。 */
    public static final RenderType RIBBON = RenderType.create(
            "glowtrack_ribbon",
            RenderSetup.builder(
                    RenderPipeline.builder()
                            .withLocation("glowtrack/ribbon")
                            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                            // 半透明：写颜色不写深度，避免轨迹互相遮挡
                            .withDepthWrite(false)
                            .withColorWrite(true)
                            .withBlend(BlendFunction.TRANSLUCENT)
                            .withCull(false)
                            .build())
                    .createRenderSetup());

    /** 发光层：加色混合（src_alpha + one），形成光晕。 */
    public static final RenderType RIBBON_GLOW = RenderType.create(
            "glowtrack_ribbon_glow",
            RenderSetup.builder(
                    RenderPipeline.builder()
                            .withLocation("glowtrack/ribbon_glow")
                            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                            .withDepthWrite(false)
                            .withColorWrite(true)
                            // 加色混合：src_alpha + one，形成光晕
                            .withBlend(BlendFunction.ADDITIVE)
                            .withCull(false)
                            .build())
                    .createRenderSetup());
}
