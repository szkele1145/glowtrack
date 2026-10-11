package com.glowtrack.client;

/**
 * 轨迹线的着色风格。每种风格对应一种独立的颜色映射策略，
 * 在 GUI 里可实时切换。
 */
public enum RenderStyle {
    /** 单色：整条线用基准色相的单一颜色（最简洁）。 */
    SOLID("单色"),
    /** 彩虹：沿路径位置平滑过渡整段色环（最炫）。 */
    RAINBOW("彩虹"),
    /** 速度热力：慢=蓝 → 快=红（看清走位快慢）。 */
    HEAT("速度热力"),
    /** 时间渐变：旧段暗、新段亮，颜色随时间前进（看清走向）。 */
    TIME("时间渐变"),
    /** 脉冲流动：基准色 + 沿路径流动的高亮脉冲波。 */
    PULSE("脉冲流动");

    private final String zh;

    RenderStyle(String zh) { this.zh = zh; }

    public String zh() { return zh; }

    /** 切到下一种风格（GUI 循环按钮用）。 */
    public RenderStyle next() {
        RenderStyle[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
