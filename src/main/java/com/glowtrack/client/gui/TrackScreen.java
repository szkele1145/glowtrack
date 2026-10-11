package com.glowtrack.client.gui;

import com.glowtrack.client.ClientSettings;
import com.glowtrack.client.ClientTrackState;
import com.glowtrack.network.Payloads;
import com.glowtrack.track.TrackPoint;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 现代化主追踪界面（GitHub 深色主题风格）。
 *
 * <p>布局：左栏（搜索 + 可滚动玩家列表，手写滚动/裁剪/悬停）+ 右栏
 * （玩家信息、风格与配色循环、线宽/采样/保留滑块、发光/穿墙/虚线/箭头/标记/时间开关、
 * 时间轴回看 + 实时读数、一键传送、导出/关闭）。</p>
 *
 * <p>所有渲染参数实时写入 {@link ClientSettings} 并持久化。玩家列表用手写
 * 滚动实现（避开 AbstractSelectionList 的嵌套类访问限制），完全可控。</p>
 */
public class TrackScreen extends Screen {

    private static final int TL_H = 18;
    private static final int ROW = 24;

    private int leftX, leftW, panelX, panelW, top;
    private int listX, listY, listW, listH;
    private int tlX, tlY, tlW;

    private EditBox search;
    private final List<Payloads.PlayerEntry> filtered = new ArrayList<>();
    private Payloads.PlayerEntry selected;
    private int scrollPx = 0;
    private boolean dragTl = false;

    public TrackScreen() {
        super(Component.literal("玩家轨迹追踪"));
    }

    @Override
    protected void init() {
        leftW = clamp(width / 3, 150, 210);
        leftX = 10;
        panelX = leftX + leftW + 8;
        panelW = width - panelX - 10;
        top = 30;
        listX = leftX + 4; listY = top + 24; listW = leftW - 8;
        listH = height - 30 - listY;
        tlX = panelX + 8; tlW = panelW - 16; tlY = height - 120;

        search = new EditBox(font, leftX + 6, top + 4, leftW - 12, 16, Component.literal("搜索玩家"));
        search.setMaxLength(32);
        search.setHint(Component.literal("🔍 搜索玩家..."));
        search.setResponder(s -> { refreshList(); scrollPx = 0; });
        addRenderableWidget(search);

        refreshList();
        buildRight();
        if (ClientTrackState.get().isServerMode())
            ClientTrackState.sendToServer(new Payloads.TrackListReq());
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void refreshList() {
        filtered.clear();
        String q = (search == null) ? "" : search.getValue().trim().toLowerCase();
        for (Payloads.PlayerEntry e : ClientTrackState.get().whitelist())
            if (q.isEmpty() || e.name().toLowerCase().contains(q)) filtered.add(e);
    }

    private void buildRight() {
        int x = panelX + 8, w = panelW - 16;
        int col2 = (w - 6) / 2, col3 = (w - 12) / 3;
        int y = top + 2;

        addRenderableWidget(Button.builder(Component.literal("▶ 开始追踪"), b -> startTrack())
                .bounds(x, y, col2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("■ 停止"), b -> stopTrack())
                .bounds(x + col2 + 6, y, col2, 20).build());

        y += 26;
        addRenderableWidget(Button.builder(Component.literal("风格: " + ClientSettings.get().style.zh()), b -> {
            ClientSettings.get().style = ClientSettings.get().style.next();
            ClientSettings.get().save();
            b.setMessage(Component.literal("风格: " + ClientSettings.get().style.zh()));
        }).bounds(x, y, col2, 18).build());
        addRenderableWidget(Button.builder(Component.literal("🎨 配色"), b -> {
            ClientSettings s = ClientSettings.get();
            s.baseHue += 0.12f; if (s.baseHue > 1f) s.baseHue -= 1f; s.save();
        }).bounds(x + col2 + 6, y, col2, 18).build());

        y += 24;
        addRenderableWidget(slider(x, y, w, "线宽",
                () -> (ClientSettings.get().lineWidth - 1) / 11.0,
                v -> ClientSettings.get().lineWidth = (float) (1 + v * 11),
                () -> String.format("%.1f", ClientSettings.get().lineWidth)));
        y += 20;
        addRenderableWidget(slider(x, y, w, "采样",
                () -> (ClientSettings.get().sampleIntervalTicks - 1) / 39.0,
                v -> { ClientSettings.get().sampleIntervalTicks = (int) Math.round(1 + v * 39); pushSettings(); },
                () -> String.format("%.1fs", ClientSettings.get().sampleIntervalTicks / 20.0)));
        y += 20;
        addRenderableWidget(slider(x, y, w, "保留",
                () -> (ClientSettings.get().retentionDays - 1) / 29.0,
                v -> { ClientSettings.get().retentionDays = (int) Math.round(1 + v * 29); pushSettings(); },
                () -> ClientSettings.get().retentionDays + "天"));

        y += 24;
        addRenderableWidget(tg(x, y, col3, "发光", () -> ClientSettings.get().glow, v -> ClientSettings.get().glow = v));
        addRenderableWidget(tg(x + col3 + 6, y, col3, "穿墙", () -> ClientSettings.get().wallhack, v -> ClientSettings.get().wallhack = v));
        addRenderableWidget(tg(x + 2 * (col3 + 6), y, col3, "虚线", () -> ClientSettings.get().dashPulse, v -> ClientSettings.get().dashPulse = v));
        y += 20;
        addRenderableWidget(tg(x, y, col3, "箭头", () -> ClientSettings.get().showArrows, v -> ClientSettings.get().showArrows = v));
        addRenderableWidget(tg(x + col3 + 6, y, col3, "标记", () -> ClientSettings.get().showMarkers, v -> ClientSettings.get().showMarkers = v));
        addRenderableWidget(tg(x + 2 * (col3 + 6), y, col3, "时间", () -> ClientSettings.get().timeLabels, v -> ClientSettings.get().timeLabels = v));

        addRenderableWidget(Button.builder(Component.literal("📍 传送至游标"), b -> teleport())
                .bounds(x, tlY + TL_H + 8, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("⬇ 导出"), b -> exportTrack())
                .bounds(x, height - 28, col2, 18).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                .bounds(x + col2 + 6, height - 28, col2, 18).build());
    }

    private Button tg(int x, int y, int w, String label,
                      java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        return Button.builder(tgText(label, get.getAsBoolean()), btn -> {
            set.accept(!get.getAsBoolean());
            ClientSettings.get().save();
            btn.setMessage(tgText(label, get.getAsBoolean()));
        }).bounds(x, y, w, 18).build();
    }

    private Component tgText(String l, boolean on) { return Component.literal((on ? "● " : "○ ") + l); }

    private AbstractSliderButton slider(int x, int y, int w, String label,
                                        java.util.function.DoubleSupplier get,
                                        java.util.function.DoubleConsumer set,
                                        java.util.function.Supplier<String> fmt) {
        return new AbstractSliderButton(x, y, w, 16, Component.literal(label), clampd(get.getAsDouble())) {
            @Override protected void updateMessage() { setMessage(Component.literal(label + ": " + fmt.get())); }
            @Override protected void applyValue() { set.accept(value); ClientSettings.get().save(); }
        };
    }

    private static double clampd(double v) { return Math.max(0, Math.min(1, v)); }

    // ---- 动作 ----
    private void startTrack() {
        if (selected == null) return;
        ClientTrackState.get().setLocalTracked(selected.id(), selected.name());
        if (ClientTrackState.get().isServerMode())
            ClientTrackState.sendToServer(new Payloads.StartTracking(selected.id(), ClientSettings.get().retentionDays));
        ClientTrackState.get().setScrubRatio(1.0);
    }

    private void stopTrack() {
        if (selected == null) return;
        if (ClientTrackState.get().isServerMode())
            ClientTrackState.sendToServer(new Payloads.StopTracking(selected.id()));
        ClientTrackState.get().clearTracking();
    }

    /** 把客户端采样设置同步到服务端（服务端模式下生效）。 */
    private void pushSettings() {
        if (!ClientTrackState.get().isServerMode()) return;
        ClientTrackState.sendToServer(new Payloads.SyncSettings(
                ClientSettings.get().sampleIntervalTicks,
                0.0, 25.0, ClientSettings.get().retentionDays));
    }

    private void teleport() {
        List<TrackPoint> pts = ClientTrackState.get().points();
        if (selected == null || pts.isEmpty()) return;
        int idx = clamp((int) Math.round(ClientTrackState.get().scrubRatio() * (pts.size() - 1)), 0, pts.size() - 1);
        ClientTrackState.sendToServer(new Payloads.TeleportTo(selected.id(), pts.get(idx).timeMillis()));
    }

    private void exportTrack() {
        if (selected == null) return;
        String msg = ClientTrackState.get().exportSelected(selected.name());
        if (minecraft != null && minecraft.player != null)
            minecraft.player.displayClientMessage(Component.literal("[GlowTrack] " + msg), false);
    }

    // ---- 渲染 ----
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float p) {
        g.fillGradient(0, 0, width, height, 0xD0080B12, 0xD0080B12);
        g.fill(0, 0, width, 26, 0xFF0D1117);
        g.fill(leftX - 2, top - 4, leftX + leftW + 2, height - 24, 0xF011161F);
        g.fill(panelX, top - 4, panelX + panelW, height - 24, 0xF0141B26);

        g.drawString(font, title, 10, 9, 0xE6EDF3, false);
        String mode = ClientTrackState.get().isServerMode() ? "● 服务端模式" : "○ 本地观察";
        g.drawString(font, mode, width - font.width(mode) - 10, 9, 0x58A6FF, false);

        drawList(g, mx, my);
        drawRight(g);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float p) {
        super.render(g, mx, my, p);
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        g.drawString(font, "白名单玩家", leftX + 6, top + 10, 0x8B949E, false);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY - scrollPx;
        for (int i = 0; i < filtered.size(); i++) {
            Payloads.PlayerEntry e = filtered.get(i);
            if (y + ROW >= listY && y <= listY + listH) {
                boolean sel = selected != null && selected.id().equals(e.id());
                boolean hov = mx >= listX && mx <= listX + listW && my >= y && my < y + ROW - 2;
                if (sel) g.fill(listX, y, listX + listW, y + ROW - 2, 0x331F6FEB);
                else if (hov) g.fill(listX, y, listX + listW, y + ROW - 2, 0x26FFFFFF);
                drawHead(g, e.id(), e.name(), listX + 2, y + 4);
                g.drawString(font, e.name(), listX + 24, y + 4, sel ? 0x58A6FF : 0xE6EDF3, false);
                g.fill(listX + 24, y + 15, listX + 28, y + 19, e.online() ? 0xFF3FB950 : 0xFF484F58);
                g.drawString(font, e.online() ? "在线" : "离线", listX + 32, y + 14,
                        e.online() ? 0x3FB950 : 0x6E7681, false);
            }
            y += ROW;
        }
        g.disableScissor();
        if (filtered.isEmpty())
            g.drawString(font, ClientTrackState.get().whitelist().isEmpty() ? "无白名单数据" : "无匹配玩家",
                    listX + 4, listY + 4, 0x6E7681, false);
        // 滚动条
        int maxScroll = Math.max(0, filtered.size() * ROW - listH);
        if (maxScroll > 0) {
            int barH = Math.max(20, listH * listH / (filtered.size() * ROW));
            int barY = listY + (int) ((listH - barH) * (scrollPx / (double) maxScroll));
            g.fill(listX + listW - 3, barY, listX + listW - 1, barY + barH, 0x66FFFFFF);
        }
    }

    private void drawRight(GuiGraphics g) {
        int x = panelX + 8;
        if (selected != null) {
            drawHead(g, selected.id(), selected.name(), x, top);
            g.drawString(font, selected.name(), x + 22, top + 1, 0xE6EDF3, false);
            g.drawString(font, selected.online() ? "在线" : "离线", x + 22, top + 12,
                    selected.online() ? 0x3FB950 : 0x8B949E, false);
        } else {
            g.drawString(font, "← 选择一名玩家", x, top + 4, 0x8B949E, false);
        }

        List<TrackPoint> pts = ClientTrackState.get().points();
        int ry = tlY - 48;
        if (pts.isEmpty()) {
            g.drawString(font, selected == null ? "未选择玩家" : "点「开始追踪」加载轨迹", x, ry, 0x6E7681, false);
        } else {
            TrackPoint cp = cursor(pts);
            g.drawString(font, "时刻 " + new SimpleDateFormat("HH:mm:ss").format(new Date(cp.timeMillis())), x, ry, 0xD29922, false);
            g.drawString(font, "维度 " + dimName(cp.dimension().toString()), x, ry + 12, 0x58A6FF, false);
            g.drawString(font, String.format("坐标 %.1f  %.1f  %.1f", cp.x(), cp.y(), cp.z()), x, ry + 24, 0x8B949E, false);
            g.drawString(font, String.format("%d 点 · %.0f m", pts.size(), totalDist(pts)), x, ry + 36, 0x6E7681, false);
        }

        // 时间轴
        g.fill(tlX, tlY, tlX + tlW, tlY + TL_H, 0xFF0D1117);
        if (pts.isEmpty()) {
            g.drawString(font, "追踪后此处可回看时间轴", tlX + 4, tlY + 6, 0x484F58, false);
        } else {
            for (int i = 0; i < tlW; i++) {
                TrackPoint p = pts.get((int) (i / (double) tlW * (pts.size() - 1)));
                g.fill(tlX + i, tlY + 2, tlX + i + 1, tlY + TL_H - 2, heat(p.speed()));
            }
            for (int i = 1; i < pts.size(); i++) {
                if (!pts.get(i).inDimension(pts.get(i - 1).dimension())) {
                    int dx = tlX + (int) (i / (double) pts.size() * tlW);
                    g.fill(dx, tlY, dx + 1, tlY + TL_H, 0xFFFFFFFF);
                }
            }
            int cur = tlX + (int) (ClientTrackState.get().scrubRatio() * (tlW - 1));
            g.fill(cur - 1, tlY - 2, cur + 1, tlY + TL_H + 2, 0xFFFFFFFF);
        }
    }

    private int heat(float sp) {
        float v = Math.max(0f, Math.min(1f, sp / 3f));
        int r, gr, b;
        if (v < 0.5f) { r = 0; gr = (int) (v * 2 * 255); b = 255 - gr; }
        else { int t = (int) ((v - 0.5) * 2 * 255); r = t; gr = 255 - t; b = 0; }
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private void drawHead(GuiGraphics g, UUID id, String name, int x, int y) {
        try {
            PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(new GameProfile(id, name));
            if (skin != null && skin.texture() != null) { g.blit(skin.texture(), x, y, 8, 8, 8, 8, 64, 64); return; }
        } catch (Throwable ignored) {}
        g.fill(x, y, x + 16, y + 16, 0xFF30363D);
    }

    private String dimName(String d) {
        if (d.contains("nether")) return "下界";
        if (d.contains("end")) return "末地";
        return "主世界";
    }

    private double totalDist(List<TrackPoint> pts) {
        double d = 0;
        for (int i = 1; i < pts.size(); i++) {
            TrackPoint a = pts.get(i - 1), b = pts.get(i);
            if (a.inDimension(b.dimension())) d += a.distanceTo(b);
        }
        return d;
    }

    private TrackPoint cursor(List<TrackPoint> pts) {
        return pts.get(clamp((int) Math.round(ClientTrackState.get().scrubRatio() * (pts.size() - 1)), 0, pts.size() - 1));
    }

    // ---- 交互 ----
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            int idx = (int) ((my - listY + scrollPx) / ROW);
            if (idx >= 0 && idx < filtered.size()) { selected = filtered.get(idx); return true; }
        }
        if (mx >= tlX - 2 && mx <= tlX + tlW + 2 && my >= tlY - 2 && my <= tlY + TL_H + 2
                && !ClientTrackState.get().points().isEmpty()) {
            dragTl = true; updateTl(mx); return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (dragTl) { updateTl(mx); return true; }
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) { dragTl = false; return super.mouseReleased(mx, my, b); }

    private void updateTl(double mx) { ClientTrackState.get().setScrubRatio((mx - tlX) / tlW); }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            scrollPx = clamp(scrollPx - (int) (sy * 12), 0, Math.max(0, filtered.size() * ROW - listH));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
