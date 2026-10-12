package com.glowtrack.client.gui.widget;

import com.glowtrack.client.ClientTrackState;
import com.glowtrack.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 玩家列表面板：搜索过滤后的白名单条目、在线状态、头像、滚动。
 *
 * <p>从 TrackScreen 拆出。宿主通过 {@link SelectionListener} 接收选中变化，
 * 通过 setter 提供过滤后的数据（数据归 TrackScreen 管，面板只管显示与滚动）。</p>
 */
public class PlayerListPane extends AbstractWidget {

    private static final int ROW = 24;

    /** 选中变化回调。 */
    public interface SelectionListener {
        void onSelected(Payloads.PlayerEntry entry);
    }

    private List<Payloads.PlayerEntry> items = List.of();
    private Payloads.PlayerEntry selected;
    private final SelectionListener listener;
    private int scrollPx = 0;

    public PlayerListPane(int x, int y, int w, int h, SelectionListener listener) {
        super(x, y, w, h, com.glowtrack.client.Text.tr("glowtrack.gui.whitelist"));
        this.listener = listener;
    }

    public void setItems(List<Payloads.PlayerEntry> items) {
        this.items = items;
        clampScroll();
    }

    public void setSelected(Payloads.PlayerEntry e) { this.selected = e; }

    public void resetScroll() { scrollPx = 0; }

    private void clampScroll() {
        int maxScroll = Math.max(0, items.size() * ROW - height);
        scrollPx = Math.max(0, Math.min(scrollPx, maxScroll));
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        int x = getX(), y0 = getY(), w = width, h = height;
        g.enableScissor(x, y0, x + w, y0 + h);
        int y = y0 - scrollPx;
        for (int i = 0; i < items.size(); i++) {
            Payloads.PlayerEntry e = items.get(i);
            if (y + ROW >= y0 && y <= y0 + h) {
                boolean sel = selected != null && selected.id().equals(e.id());
                boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + ROW - 2;
                if (sel) g.fill(x, y, x + w, y + ROW - 2, Palette.SEL_BG);
                else if (hov) g.fill(x, y, x + w, y + ROW - 2, Palette.HOVER_BG);
                drawHead(g, e.id(), e.name(), x + 2, y + 4);
                g.drawString(font, e.name(), x + 24, y + 4, sel ? Palette.ACCENT : Palette.TXT_BRIGHT, false);
                g.fill(x + 24, y + 15, x + 28, y + 19, e.online() ? Palette.ONLINE : Palette.OFFLINE);
                g.drawString(font, e.online() ? com.glowtrack.client.Text.s("glowtrack.gui.online") : com.glowtrack.client.Text.s("glowtrack.gui.offline"), x + 32, y + 14,
                        e.online() ? Palette.ONLINE : Palette.TXT_FAINT, false);
            }
            y += ROW;
        }
        g.disableScissor();
        if (items.isEmpty())
            g.drawString(font, ClientTrackState.get().whitelist().isEmpty() ? com.glowtrack.client.Text.s("glowtrack.gui.no_whitelist") : com.glowtrack.client.Text.s("glowtrack.gui.no_match"),
                    x + 4, y0 + 4, Palette.TXT_FAINT, false);
        int maxScroll = Math.max(0, items.size() * ROW - h);
        if (maxScroll > 0) {
            int barH = Math.max(20, h * h / (items.size() * ROW));
            int barY = y0 + (int) ((h - barH) * (scrollPx / (double) maxScroll));
            g.fill(x + w - 3, barY, x + w - 1, barY + barH, Palette.SCROLLBAR);
        }
    }

    /** 头像纹理缓存：UUID → 最近一次解析到的皮肤。避免每帧构造 GameProfile + 查表。 */
    private static final java.util.Map<UUID, PlayerSkin> SKIN_CACHE = new java.util.HashMap<>();

    private void drawHead(GuiGraphics g, UUID id, String name, int x, int y) {
        PlayerSkin skin = SKIN_CACHE.get(id);
        if (skin == null) {
            try {
                skin = Minecraft.getInstance().getSkinManager()
                        .getInsecureSkin(new com.mojang.authlib.GameProfile(id, name));
                if (skin != null) SKIN_CACHE.put(id, skin);
            } catch (Throwable ignored) {}
        }
        if (skin != null && skin.texture() != null) { g.blit(skin.texture(), x, y, 8, 8, 8, 8, 64, 64); return; }
        g.fill(x, y, x + 16, y + 16, 0xFF30363D);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isMouseOver(mx, my)) return false;
        int idx = (int) ((my - getY() + scrollPx) / ROW);
        if (idx >= 0 && idx < items.size()) {
            selected = items.get(idx);
            listener.onSelected(selected);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!isMouseOver(mx, my)) return false;   // 面板外的滚轮不吞事件（不挡时间轴缩放）
        int maxScroll = Math.max(0, items.size() * ROW - height);
        scrollPx = Math.max(0, Math.min(scrollPx - (int) (sy * 12), maxScroll));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
