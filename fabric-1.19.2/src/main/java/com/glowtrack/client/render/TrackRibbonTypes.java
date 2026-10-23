package com.glowtrack.client.render;

import net.minecraft.client.renderer.RenderType;

/**
 * 轨迹带专用的两种 RenderType。
 *
 * <p><b>为什么要两类</b>：主线与发光层本来要不同的混合模式（普通 alpha vs 加色），
 * 而 {@code MultiBufferSource} 按 RenderType 分桶——若共用同一实例，两层会落进
 * 同一个顶点缓冲，混合状态互相覆盖。</p>
 *
 * <p><b>1.19.2 的折中</b>：这一版 {@code RenderType} 没有 {@code debugQuads()}
 * （1.19.3+ 才有），也没有 POSITION_COLOR + QUADS + 半透明的公开常量；
 * {@code RenderStateShard} 的 shader/transparency 常量是包内可见，
 * 外部类无法构造自定义 {@code CompositeState}。所以两层统一用
 * {@link RenderType#lightning()}（POSITION_COLOR + QUADS + 加色混合 + 不剔除），
 * 轨迹在深色天空与洞穴里都清晰可见。</p>
 *
 * <p>代价：失去「主线半透明、光晕更亮」的层次分离，叠加后整体更亮。
 * 这是本版本能拿到的最接近效果，功能（绘制、宽度、颜色）不受影响。</p>
 */
public final class TrackRibbonTypes {

    private TrackRibbonTypes() {}

    /** 主线层。 */
    public static final RenderType RIBBON = RenderType.lightning();

    /** 发光层。 */
    public static final RenderType RIBBON_GLOW = RenderType.lightning();
}
