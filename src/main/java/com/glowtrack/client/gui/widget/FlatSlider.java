package com.glowtrack.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * 扁平化主题滑块：完全自绘，不使用原版纹理。
 *
 * <p>轨道为细条，滑块为实心小块，左侧填充高亮色，右侧暗色。
 * 值变化时回调 setter 并刷新显示文本。</p>
 */
public class FlatSlider extends AbstractSliderButton {

    private static final int TRACK_BG   = 0xFF21262D;
    private static final int TRACK_FILL = 0xFF1F6FEB;
    private static final int KNOB       = 0xFFC9D1D9;
    private static final int TXT        = 0xFFC9D1D9;

    private final int px, py, pw, ph;
    private final String label;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final Supplier<String> fmt;

    public FlatSlider(int x, int y, int w, int h, String label,
                      DoubleSupplier getter, DoubleConsumer setter, Supplier<String> fmt) {
        super(x, y, w, h, Component.literal(label), clamp(getter.getAsDouble()));
        this.px = x; this.py = y; this.pw = w; this.ph = h;
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        this.fmt = fmt;
        updateMessage();
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label + ": " + fmt.get()));
    }

    @Override
    protected void applyValue() {
        setter.accept(value);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        // 顶部标签文本（超出控件宽度则截断，防溢出到邻栏）
        String msg = getMessage().getString();
        var fitted = font.plainSubstrByWidth(msg, pw - 2);
        g.drawString(font, fitted, px, py, TXT, false);

        // 轨道（下方细条）
        int trackY = py + ph - 4;
        int trackH = 4;
        int midY = trackY + trackH / 2;
        g.fill(px, trackY, px + pw, trackY + trackH, TRACK_BG);

        // 已填充部分
        int fillW = (int) (pw * value);
        g.fill(px, trackY, px + fillW, trackY + trackH, TRACK_FILL);

        // 滑块（实心小块，居中在轨道上）
        int knobW = 6, knobH = 10;
        int knobX = px + fillW - knobW / 2;
        int knobY = midY - knobH / 2;
        g.fill(knobX, knobY, knobX + knobW, knobY + knobH, KNOB);
    }
}
