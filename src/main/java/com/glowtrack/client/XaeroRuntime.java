package com.glowtrack.client;

/**
 * Xaero 运行期探测（mod 构造完成后才可调用——与 Mixin 插件阶段的资源探测分离）。
 *
 * <p>运行期允许 {@code Class.forName}：此时 mod 类加载器已就绪，
 * 探测到的类被正常加载无副作用。结果做静态缓存（运行期只调用一次的场景）。 </p>
 */
public final class XaeroRuntime {
    private static Boolean worldMap = null;
    private static Boolean minimap = null;

    private XaeroRuntime() {}

    /** 是否安装了 Xaero's World Map（运行期）。 */
    public static boolean hasWorldMap() {
        if (worldMap == null) {
            try {
                Class.forName("xaero.map.gui.GuiMap");
                worldMap = true;
            } catch (Throwable t) {
                worldMap = false;
            }
        }
        return worldMap;
    }

    /** 是否安装了 Xaero's Minimap（运行期）。 */
    public static boolean hasMinimap() {
        if (minimap == null) {
            try {
                Class.forName("xaero.common.minimap.render.MinimapFBORenderer");
                minimap = true;
            } catch (Throwable t) {
                minimap = false;
            }
        }
        return minimap;
    }

    /** 两个图至少装了一个（地图开关可用性的运行期判定）。 */
    public static boolean anyXaero() {
        return hasWorldMap() || hasMinimap();
    }
}
