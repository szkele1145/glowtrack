package com.glowtrack.client.gui.widget;

/**
 * GUI 统一调色板（GitHub 深色主题）。
 *
 * <p>所有 GUI 类的颜色常量集中于此，改主题只动这一个文件。</p>
 */
public final class Palette {
    private Palette() {}

    // 背景
    public static final int SCREEN_BG   = 0xF2080B12;  // 全屏遮罩
    public static final int TITLE_BAR   = 0xFF0D1117;  // 顶栏
    public static final int PANEL_LEFT  = 0xFF11161F;  // 左栏
    public static final int PANEL_RIGHT = 0xFF141B26;  // 右栏
    public static final int TIMELINE_BG = 0xFF0D1117;  // 时间轴底

    // 文本
    public static final int TXT_BRIGHT  = 0xFFE6EDF3;  // 主文本
    public static final int TXT_NORMAL  = 0xFFC9D1D9;  // 常规
    public static final int TXT_MUTED   = 0xFF8B949E;  // 次要
    public static final int TXT_FAINT   = 0xFF6E7681;  // 提示
    public static final int TXT_GHOST   = 0xFF484F58;  // 极弱/占位

    // 语义色
    public static final int ACCENT      = 0xFF58A6FF;  // 蓝（链接/选中/模式）
    public static final int WARN        = 0xFFD29922;  // 黄（时刻读数）
    public static final int ONLINE      = 0xFF3FB950;  // 绿（在线）
    public static final int OFFLINE     = 0xFF484F58;  // 灰（离线）

    // 控件
    public static final int SEL_BG      = 0x331F6FEB;  // 列表选中底
    public static final int HOVER_BG    = 0x26FFFFFF;  // 列表悬停底
    public static final int SCROLLBAR   = 0x66FFFFFF;  // 列表滚动条
}
