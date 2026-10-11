package com.glowtrack.client;

/**
 * Xaero 模组检测（客户端）。世界地图与小地图分别探测，
 * 供 GUI 判断「地图」开关是否可用，也供 mixin 插件参考。
 */
public final class XaeroDetect {
    private static Boolean worldMap = null;
    private static Boolean minimap = null;

    private XaeroDetect() {}

    /** 是否安装了 Xaero's World Map。 */
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

    /** 是否安装了 Xaero's Minimap。 */
    public static boolean hasMinimap() {
        if (minimap == null) {
            try {
                Class.forName("xaero.common.minimap.MinimapClientEvents");
                minimap = true;
            } catch (Throwable t) {
                // 再试一个常见入口类
                try {
                    Class.forName("xaero.common.minimap.render.MinimapFBORenderer");
                    minimap = true;
                } catch (Throwable t2) {
                    minimap = false;
                }
            }
        }
        return minimap;
    }

    /** 两个图至少装了一个（地图开关可用的前提）。 */
    public static boolean anyXaero() {
        return hasWorldMap() || hasMinimap();
    }
}
