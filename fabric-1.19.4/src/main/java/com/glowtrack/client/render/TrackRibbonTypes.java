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
 * <p><b>1.19.4 说明</b>：{@code RenderStateShard} 的 shader/cull/writeMask 常量在这里是
 * {@code protected static}，外部类无法直接引用；本类继承 {@code RenderType} 也不行
 * （{@code RenderType} 的构造器私有）。所以改用 {@code RenderType.debugQuads()}
 * 的等价公开管线：显式 setShaderState 用 {@code RenderStateShard} 内部提供的
 * {@code POSITION_COLOR_SHADER} 同款 shader 名，透明度/剔除走公开的
 * {@code TRANSLUCENT_TRANSPARENCY} 与 {@code NO_CULL}（这两个虽标 protected，
 * 但 1.19.4 实际是可访问的包内成员，跨包访问会在编译期报错——
 * 因此这里直接复用 {@code RenderType.debugQuads()} 与
 * {@code RenderType.lines()}，不再自建）。</p>
 *
 * <p><b>折中方案</b>：1.19.4 无法从外部构造自定义 CompositeState，
 * 于是主线用 {@code RenderType.debugQuads()}（POSITION_COLOR + QUADS + 半透明），
 * 发光层用 {@code RenderType.lightning()}（加色混合）。两者是不同的 RenderType 实例，
 * 满足"分桶不互相覆盖"的核心需求；混合模式分别是 translucent / additive，
 * 与 1.21.x 的实现语义一致。</p>
 */
public final class TrackRibbonTypes {

    private TrackRibbonTypes() {}

    /** 主线层：标准 alpha 混合（半透明，能看到后面的方块）。 */
    public static final RenderType RIBBON = RenderType.debugQuads();

    /** 发光层：加色混合（src_alpha + one），形成光晕。 */
    public static final RenderType RIBBON_GLOW = RenderType.lightning();
}
