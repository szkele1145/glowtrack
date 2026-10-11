package com.glowtrack.client.gui;

import com.glowtrack.client.ClientSettings;
import com.glowtrack.client.ClientTrackState;
import com.glowtrack.client.gui.widget.FlatButton;
import com.glowtrack.client.gui.widget.FlatSlider;
import com.glowtrack.client.gui.widget.FlatToggle;
import com.glowtrack.network.Payloads;
import com.glowtrack.track.TrackPoint;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
 * 主追踪界面（扁平主题，极简布局）。
 *
 * <p>渲染效果按选品既定、固定无开关。GUI 只保留核心功能：
 * 左栏搜索+玩家列表；右栏玩家信息、开始/停止追踪、时间轴回看、
 * 一键传送、实时读数、采样/保留设置、导出/关闭。</p>
 *
 * <p>右栏用单向 Y 流布局，控件与绘制内容共用锚点，保证不重叠、不溢出。</p>
 */
public class TrackScreen extends Screen {

    private static final int TL_H = 16;
    private static final int ROW = 24;

    private int leftX, leftW, listX, listY, listW, listH;
    private int panelX, panelW, top;
    private int yHead, yBtn, yMode, yTl, yTele, yRead, ySlider;
    private int tlX, tlW;

    private EditBox search;
    private final List<Payloads.PlayerEntry> filtered = new ArrayList<>();
    private Payloads.PlayerEntry selected;
    private int scrollPx = 0;
    private boolean dragTl = false;
    private boolean tlDragMoved = false;
    private double tlDragStartX = 0;
    private double tlDragStartViewEnd = 1.0;

    public TrackScreen() {
        super(Component.literal("玩家轨迹追踪"));
    }

    @Override
    protected void init() {
        layout();
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

    /** 单向 Y 流布局：内容自上而下排，底部留出 footer 空间，保证不溢出。 */
    private void layout() {
        leftW = clamp(width / 3, 150, 210);
        leftX = 10;
        panelX = leftX + leftW + 8;
        panelW = width - panelX - 10;
        top = 30;
        listX = leftX + 4; listY = top + 24; listW = leftW - 8;
        listH = height - 30 - listY;
        tlX = panelX + 8; tlW = panelW - 16;

        int y = top + 4;
        yHead   = y; y += 22;        // 玩家头像/名
        yBtn    = y; y += 26;        // 开始/停止
        yMode   = y; y += 22;        // 三个渲染模式开关
        yTl     = y; y += TL_H + 8;  // 时间轴
        yTele   = y; y += 24;        // 传送
        yRead   = y; y += 36;        // 读数（3 行）
        ySlider = y; y += 20;        // 时间点间隔 | 保留天数（一行两滑块）
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

        addRenderableWidget(FlatButton.primary(x, yBtn, col2, 20,
                Component.literal("▶ 开始追踪"), b -> startTrack()));
        addRenderableWidget(FlatButton.of(x + col2 + 6, yBtn, col2, 20,
                Component.literal("■ 停止"), b -> stopTrack()));

        // 三个渲染模式开关
        addRenderableWidget(new FlatToggle(x, yMode, col3, 16, "3D",
                () -> ClientSettings.get().mode3d, v -> { ClientSettings.get().mode3d = v; ClientSettings.get().save(); }));
        addRenderableWidget(new FlatToggle(x + col3 + 6, yMode, col3, 16, "2D",
                () -> ClientSettings.get().mode2d, v -> { ClientSettings.get().mode2d = v; ClientSettings.get().save(); }));
        addRenderableWidget(new FlatToggle(x + 2 * (col3 + 6), yMode, col3, 16, "地图",
                () -> ClientSettings.get().xaeroMap, v -> { ClientSettings.get().xaeroMap = v; ClientSettings.get().save(); }));

        addRenderableWidget(FlatButton.of(x, yTele, col2, 20,
                Component.literal("📍 传送至游标时刻"), b -> teleport()));
        addRenderableWidget(FlatButton.of(x + col2 + 6, yTele, col2, 20,
                Component.literal("⏮ 回到最新"), b -> {
                    ClientTrackState.get().setScrubRatio(1.0);
                    ClientTrackState.get().snapViewToLatest();
                }));

        addRenderableWidget(new FlatSlider(x, ySlider, col2, 16, "时间点",
                () -> (ClientSettings.get().timePointSeconds - 5) / 295.0,
                v -> { ClientSettings.get().timePointSeconds = (int) Math.round(5 + v * 295); ClientSettings.get().save(); },
                () -> ClientSettings.get().timePointSeconds + "s"));
        addRenderableWidget(new FlatSlider(x + col2 + 6, ySlider, col2, 16, "保留",
                () -> (ClientSettings.get().retentionDays - 1) / 29.0,
                v -> { ClientSettings.get().retentionDays = (int) Math.round(1 + v * 29); ClientSettings.get().save(); pushSettings(); },
                () -> ClientSettings.get().retentionDays + "天"));

        // 底部 footer
        int yFoot = height - 26;
        addRenderableWidget(FlatButton.of(x, yFoot, col2, 18,
                Component.literal("⬇ 导出"), b -> exportTrack()));
        addRenderableWidget(FlatButton.of(x + col2 + 6, yFoot, col2, 18,
                Component.literal("关闭"), b -> onClose()));
    }

    private void pushSettings() {
        if (!ClientTrackState.get().isServerMode()) return;
        ClientTrackState.sendToServer(new Payloads.SyncSettings(
                ClientSettings.get().sampleIntervalTicks, 0.0, 25.0, ClientSettings.get().retentionDays));
    }

    // ---- 动作 ----
    private void startTrack() {
        if (selected == null) return;
        ClientTrackState.get().setLocalTracked(selected.id(), selected.name());
        if (ClientTrackState.get().isServerMode())
            ClientTrackState.sendToServer(new Payloads.StartTracking(selected.id(), ClientSettings.get().retentionDays));
        ClientTrackState.get().setScrubRatio(1.0);
        ClientTrackState.get().snapViewToLatest();
    }

    private void stopTrack() {
        if (selected == null) return;
        if (ClientTrackState.get().isServerMode())
            ClientTrackState.sendToServer(new Payloads.StopTracking(selected.id()));
        ClientTrackState.get().clearTracking();
    }

    private void teleport() {
        List<TrackPoint> pts = ClientTrackState.get().points();
        if (selected == null || pts.isEmpty()) return;
        TrackPoint target = cursor(pts);
        ClientTrackState.sendToServer(new Payloads.TeleportTo(selected.id(), target.timeMillis()));
    }

    private void exportTrack() {
        if (selected == null) return;
        String msg = ClientTrackState.get().exportSelected(selected.name());
        if (minecraft != null && minecraft.player != null)
            minecraft.player.displayClientMessage(Component.literal("[GlowTrack] " + msg), false);
    }

    // ---- 背景层 ----
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float p) {
        g.fillGradient(0, 0, width, height, 0xF2080B12, 0xF2080B12);
        g.fill(0, 0, width, 26, 0xFF0D1117);
        g.fill(leftX - 2, top - 4, leftX + leftW + 2, height - 24, 0xFF11161F);
        g.fill(panelX, top - 4, panelX + panelW, height - 24, 0xFF141B26);
        g.drawString(font, title, 10, 9, 0xE6EDF3, false);
        String mode = ClientTrackState.get().isServerMode() ? "● 服务端模式" : "○ 本地观察";
        g.drawString(font, mode, width - font.width(mode) - 10, 9, 0x58A6FF, false);

        drawList(g, mx, my);
        drawRightContent(g);
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
        int maxScroll = Math.max(0, filtered.size() * ROW - listH);
        if (maxScroll > 0) {
            int barH = Math.max(20, listH * listH / (filtered.size() * ROW));
            int barY = listY + (int) ((listH - barH) * (scrollPx / (double) maxScroll));
            g.fill(listX + listW - 3, barY, listX + listW - 1, barY + barH, 0x66FFFFFF);
        }
    }

    private void drawRightContent(GuiGraphics g) {
        int x = panelX + 8;
        if (selected != null) {
            drawHead(g, selected.id(), selected.name(), x, yHead);
            g.drawString(font, selected.name(), x + 22, yHead + 1, 0xE6EDF3, false);
            g.drawString(font, selected.online() ? "在线" : "离线", x + 22, yHead + 11,
                    selected.online() ? 0x3FB950 : 0x8B949E, false);
        } else {
            g.drawString(font, "← 左侧选择玩家", x, yHead + 4, 0x8B949E, false);
        }

        List<TrackPoint> pts = ClientTrackState.get().points();
        ClientTrackState cs = ClientTrackState.get();
        g.fill(tlX, yTl, tlX + tlW, yTl + TL_H, 0xFF0D1117);
        if (pts.isEmpty()) {
            g.drawString(font, "点开始追踪后此处回看时间轴", tlX + 4, yTl + 4, 0x484F58, false);
        } else {
            double vStart = cs.viewStartRatio(), vEnd = cs.viewEndRatio();
            long t0 = pts.get(0).timeMillis(), t1 = pts.get(pts.size() - 1).timeMillis();
            long span = Math.max(1, t1 - t0);

            // 窗口内的轨迹条（按时间比例映射到 x）
            for (int i = 0; i < pts.size(); i++) {
                TrackPoint p = pts.get(i);
                double r = (p.timeMillis() - t0) / (double) span;
                if (r < vStart || r > vEnd) continue;
                int px = tlX + (int) ((r - vStart) / (vEnd - vStart) * (tlW - 1));
                g.fill(px, yTl + 2, px + 1, yTl + TL_H - 2, heat(p.speed()));
            }
            // 维度切换标记（窗口内）
            for (int i = 1; i < pts.size(); i++) {
                if (!pts.get(i).inDimension(pts.get(i - 1).dimension())) {
                    double r = (pts.get(i).timeMillis() - t0) / (double) span;
                    if (r < vStart || r > vEnd) continue;
                    int dx = tlX + (int) ((r - vStart) / (vEnd - vStart) * (tlW - 1));
                    g.fill(dx, yTl, dx + 1, yTl + TL_H, 0xFFFFFFFF);
                }
            }
            // 时间点标记（每 N 秒一个白色小竖线）
            int sec = Math.max(5, com.glowtrack.client.ClientSettings.get().timePointSeconds);
            for (long t = (t0 / (sec * 1000L)) * sec * 1000L; t <= t1; t += sec * 1000L) {
                double r = (t - t0) / (double) span;
                if (r < vStart || r > vEnd) continue;
                int px = tlX + (int) ((r - vStart) / (vEnd - vStart) * (tlW - 1));
                g.fill(px, yTl, px + 1, yTl + 4, 0xCCFFFFFF);
            }
            // 游标（若在窗口内）
            double sc = cs.scrubRatio();
            if (sc >= vStart && sc <= vEnd) {
                int cur = tlX + (int) ((sc - vStart) / (vEnd - vStart) * (tlW - 1));
                g.fill(cur - 1, yTl - 1, cur + 1, yTl + TL_H + 1, 0xFFFFFFFF);
            }
            // 窗口下方滚动条（指示当前窗口在全长中的位置）
            int sbY = yTl + TL_H + 2;
            g.fill(tlX, sbY, tlX + tlW, sbY + 2, 0xFF222831);
            int thumbX = tlX + (int) (vStart * (tlW - 1));
            int thumbW = Math.max(6, (int) ((vEnd - vStart) * (tlW - 1)));
            g.fill(thumbX, sbY, thumbX + thumbW, sbY + 2, 0xFF6E7681);
        }

        if (pts.isEmpty()) {
            g.drawString(font, selected == null ? "未选择玩家" : "等待轨迹数据…", x, yRead, 0x6E7681, false);
        } else {
            TrackPoint cp = cursor(pts);
            g.drawString(font, "时刻 " + new SimpleDateFormat("HH:mm:ss").format(new Date(cp.timeMillis())),
                    x, yRead, 0xD29922, false);
            g.drawString(font, "维度 " + dimName(cp.dimension().toString()), x, yRead + 12, 0x58A6FF, false);
            g.drawString(font, String.format("坐标 %.1f  %.1f  %.1f", cp.x(), cp.y(), cp.z()),
                    x, yRead + 24, 0x8B949E, false);
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

    /** 游标解析：scrubRatio 视为时间比例（与时间轴绘制一致），二分查找最接近该时刻的点。 */
    private TrackPoint cursor(List<TrackPoint> pts) {
        long t0 = pts.get(0).timeMillis(), t1 = pts.get(pts.size() - 1).timeMillis();
        long target = t0 + (long) (ClientTrackState.get().scrubRatio() * (double) (t1 - t0));
        int lo = 0, hi = pts.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (pts.get(mid).timeMillis() < target) lo = mid + 1; else hi = mid;
        }
        if (lo > 0 && Math.abs(pts.get(lo - 1).timeMillis() - target)
                <= Math.abs(pts.get(lo).timeMillis() - target)) lo--;
        return pts.get(lo);
    }

    // ---- 交互 ----
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            int idx = (int) ((my - listY + scrollPx) / ROW);
            if (idx >= 0 && idx < filtered.size()) { selected = filtered.get(idx); return true; }
        }
        if (mx >= tlX - 2 && mx <= tlX + tlW + 2 && my >= yTl - 2 && my <= yTl + TL_H + 6
                && !ClientTrackState.get().points().isEmpty()) {
            // 开始拖动：记录起点，用于区分「点击设游标」与「拖动平移」
            dragTl = true; tlDragMoved = false;
            tlDragStartX = mx;
            tlDragStartViewEnd = ClientTrackState.get().viewEndRatio();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (dragTl) {
            if (Math.abs(mx - tlDragStartX) > 3) tlDragMoved = true;
            // 拖动平移窗口（左拖=看历史）
            if (tlDragMoved) {
                double span = ClientTrackState.get().viewSpanRatio();
                double delta = -(mx - tlDragStartX) / tlW * span;
                ClientTrackState.get().panView(tlDragStartViewEnd + delta - ClientTrackState.get().viewEndRatio());
            }
            return true;
        }
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        // 点击（未拖动）= 在窗口内设置传送游标
        if (dragTl && !tlDragMoved && !ClientTrackState.get().points().isEmpty()) {
            double vStart = ClientTrackState.get().viewStartRatio();
            double vEnd = ClientTrackState.get().viewEndRatio();
            double r = vStart + (mx - tlX) / tlW * (vEnd - vStart);
            ClientTrackState.get().setScrubRatio(r);
        }
        dragTl = false;
        return super.mouseReleased(mx, my, b);
    }

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
