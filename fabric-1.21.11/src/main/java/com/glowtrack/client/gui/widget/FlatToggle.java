package com.glowtrack.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 扁平化主题开关：左侧胶囊滑块 + 右侧标签。
 *
 * <p>点击切换布尔值，滑块在开/关间滑动，颜色随状态变化。
 * 纯自绘，不使用原版纹理。</p>
 */
public class FlatToggle extends Button {

    private static final int TRACK_OFF = 0xFF3D444D;
    private static final int TRACK_ON  = 0xFF238636;
    private static final int KNOB_ON   = 0xFFFFFFFF;
    private static final int KNOB_OFF  = 0xFF8B949E;
    private static final int TXT       = 0xFFC9D1D9;

    private final int px, py, pw, ph;
    private final BooleanSupplier get;
    private final Consumer<Boolean> set;
    private final String label;
    private BooleanSupplier enabled = () -> true;

    public FlatToggle(int x, int y, int w, int h, String label,
                      BooleanSupplier get, Consumer<Boolean> set) {
        super(x, y, w, h, Component.literal(label), b -> {}, DEFAULT_NARRATION);
        this.px = x; this.py = y; this.pw = w; this.ph = h;
        this.get = get; this.set = set; this.label = label;
    }

    /** 禁用条件（返回 false 时控件变灰且不可点击）。 */
    public FlatToggle enabledWhen(BooleanSupplier cond) {
        this.enabled = cond;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (!enabled.getAsBoolean()) return;
        set.accept(!get.getAsBoolean());
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        boolean on = get.getAsBoolean();
        boolean en = enabled.getAsBoolean();
        int trackW = 22, trackH = 10;
        int trackX = px;
        int trackY = py + (ph - trackH) / 2;

        // 胶囊轨道
        g.fill(trackX, trackY, trackX + trackW, trackY + trackH,
                !en ? 0xFF22272E : (on ? TRACK_ON : TRACK_OFF));
        // 滑块圆点（方形近似）
        int knob = 8;
        int knobX = on ? trackX + trackW - knob - 1 : trackX + 1;
        int knobY = trackY + 1;
        g.fill(knobX, knobY, knobX + knob, knobY + knob,
                !en ? 0xFF484F58 : (on ? KNOB_ON : KNOB_OFF));

        // 标签（控件宽度不足以容纳时省略，防小分辨率溢出）
        if (pw >= trackW + 8 + font.width(label))
            g.drawString(font, label, trackX + trackW + 6, py + (ph - 8) / 2,
                    !en ? 0xFF484F58 : TXT, false);
    }
}
