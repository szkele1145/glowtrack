package com.glowtrack.client.render;

import net.minecraft.client.renderer.RenderType;

/**
 * 轨迹带专用的两种 RenderType。
 *
 * <p><b>为什么分两类</b>：主线与发光层要用不同的混合模式，而
 * {@code MultiBufferSource} 按 RenderType 分桶——共用实例会让两层落进同一个
 * 顶点缓冲，混合状态互相覆盖。</p>
 *
 * <p><b>1.19.4 的折中</b>：这一版 {@code RenderType} 的
 * {@code POSITION_COLOR_SHADER} / {@code TRANSLUCENT_TRANSPARENCY} /
 * {@code NO_CULL} / {@code COLOR_WRITE} 都还是 {@code RenderStateShard} 的
 * 包内可见常量，外部类无法构造自定义 {@code CompositeState}；
 * 同时也没有 {@code debugQuads()}（1.19.3+ 才有）。
 * 所以两层统一用 {@link RenderType#lightning()}
 * （POSITION_COLOR + QUADS + 加色混合 + 不剔除），
 * 轨迹在深色天空与洞穴里都清晰可见。</p>
 *
 * <p>代价：失去「主线半透明、光晕更亮」的层次分离，叠加后整体更亮；
 * 绘制、宽度、颜色等核心表现不受影响。</p>
 */
public final class TrackRibbonTypes {

    private TrackRibbonTypes() {}

    /** 主线层。 */
    public static final RenderType RIBBON = RenderType.lightning();

    /** 发光层。 */
    public static final RenderType RIBBON_GLOW = RenderType.lightning();
}
