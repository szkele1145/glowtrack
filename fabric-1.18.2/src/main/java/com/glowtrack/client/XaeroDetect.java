package com.glowtrack.client;

/**
 * @deprecated 运行期探测请改用 {@link XaeroRuntime}（避免在 mod 构造完成前被调用）。
 *             保留此类仅为兼容既有调用点；全部方法委托 XaeroRuntime。
 */
@Deprecated
public final class XaeroDetect {
    private XaeroDetect() {}

    /** 是否安装了 Xaero's World Map。 */
    public static boolean hasWorldMap() { return XaeroRuntime.hasWorldMap(); }

    /** 是否安装了 Xaero's Minimap。 */
    public static boolean hasMinimap() { return XaeroRuntime.hasMinimap(); }

    /** 两个图至少装了一个（地图开关可用性的判定）。 */
    public static boolean anyXaero() { return XaeroRuntime.anyXaero(); }
}