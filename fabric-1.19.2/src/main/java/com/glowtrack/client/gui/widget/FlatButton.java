package com.glowtrack.client.gui.widget;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 扁平化主题按钮：完全自绘，不使用原版石头纹理。
 *
 * <p>纯色填充 + 细边框 + 居中文本，带 hover/active/disabled 三态，
 * 与深色面板融为一体。</p>
 */
public class FlatButton extends Button {

    // GitHub 深色调色板
    private static final int BG_BASE      = 0xFF21262D;
    private static final int BG_HOVER     = 0xFF30363D;
    private static final int BG_DOWN      = 0xFF161B22;
    private static final int BG_DISABLED  = 0xFF161B22;
    private static final int BG_PRIMARY   = 0xFF238636;
    private static final int BG_PRIMARY_H = 0xFF2EA043;
    private static final int BORDER_BASE  = 0xFF3D444D;
    private static final int BORDER_HOVER = 0xFF58A6FF;
    private static final int TXT_BASE     = 0xFFC9D1D9;
    private static final int TXT_HOVER    = 0xFFFFFFFF;
    private static final int TXT_DISABLED = 0xFF656D76;

    private final int px, py, pw, ph;
    private final boolean primary;
    private java.util.function.BooleanSupplier enabledWhen = () -> true;

    public FlatButton(int x, int y, int w, int h, Component msg, OnPress onPress, boolean primary) {
        super(x, y, w, h, msg, onPress);
        this.px = x; this.py = y; this.pw = w; this.ph = h;
        this.primary = primary;
    }

    /** 禁用条件（返回 false 时按钮置灰不可点；未选中玩家时给视觉反馈）。 */
    public FlatButton enabledWhen(java.util.function.BooleanSupplier cond) {
        this.enabledWhen = cond;
        return this;
    }

    @Override
    public void onPress() {
        if (!enabledWhen.getAsBoolean()) return;
        super.onPress();
    }

    /** 便捷：普通按钮。 */
    public static FlatButton of(int x, int y, int w, int h, Component msg, OnPress onPress) {
        return new FlatButton(x, y, w, h, msg, onPress, false);
    }

    /** 便捷：主按钮（绿色强调）。 */
    public static FlatButton primary(int x, int y, int w, int h, Component msg, OnPress onPress) {
        return new FlatButton(x, y, w, h, msg, onPress, true);
    }

    @Override
    public void renderButton(PoseStack g, int mouseX, int mouseY, float partialTick) {
        boolean disabled = !active || !enabledWhen.getAsBoolean();
        boolean hovered = this.isHovered && !disabled;
        int bg, border, text;
        if (disabled) {
            bg = BG_DISABLED; border = BORDER_BASE; text = TXT_DISABLED;
        } else if (hovered) {
            bg = primary ? BG_PRIMARY_H : BG_HOVER;
            border = BORDER_HOVER; text = TXT_HOVER;
        } else {
            bg = primary ? BG_PRIMARY : BG_BASE;
            border = BORDER_BASE; text = TXT_BASE;
        }

        // 主体 + 细边框
        GuiComponent.fill(g, px, py, px + pw, py + ph, bg);
        GuiComponent.fill(g, px, py, px + pw, py + 1, border);
        GuiComponent.fill(g, px, py + ph - 1, px + pw, py + ph, border);
        GuiComponent.fill(g, px, py, px + 1, py + ph, border);
        GuiComponent.fill(g, px + pw - 1, py, px + pw, py + ph, border);

        // 居中文本
        var font = Minecraft.getInstance().font;
        String s = getMessage().getString();
        int tw = font.width(s);
        font.draw(g, s, px + (pw - tw) / 2, py + (ph - 8) / 2, text);
    }
}
