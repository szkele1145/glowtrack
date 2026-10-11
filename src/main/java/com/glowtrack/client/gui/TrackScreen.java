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

import java.util.ArrayList;
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

    private int leftX, leftW, listX, listY, listW, listH;
    private int panelX, panelW, top;
    private int yHead, yBtn, yMode, yTl, yTele, yRead, ySlider;
    private int tlX, tlW;

    private EditBox search;
    private final List<Payloads.PlayerEntry> filtered = new ArrayList<>();
    private Payloads.PlayerEntry selected;
    private com.glowtrack.client.gui.widget.TimelineWidget timeline;
    private com.glowtrack.client.gui.widget.PlayerListPane listPane;

    public TrackScreen() {
        super(Component.literal("玩家轨迹追踪"));
    }

    @Override
    protected void init() {
        layout();
        search = new EditBox(font, leftX + 6, top + 4, leftW - 12, 16, Component.literal("搜索玩家"));
        search.setMaxLength(32);
        search.setHint(Component.literal("🔍 搜索玩家..."));
        search.setResponder(s -> { refreshList(); listPane.resetScroll(); });
        addRenderableWidget(search);
        listPane = new com.glowtrack.client.gui.widget.PlayerListPane(
                listX, listY, listW, listH, e -> { selected = e; });
        addRenderableWidget(listPane);
        timeline = new com.glowtrack.client.gui.widget.TimelineWidget(tlX, yTl, tlW, TL_H);
        addRenderableWidget(timeline);
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
        yTl     = y; y += TL_H + 14; // 时间轴（含滚动条与范围标签空间）
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
        if (listPane != null) { listPane.setItems(filtered); listPane.setSelected(selected); }
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
                () -> ClientSettings.get().xaeroMap, v -> { ClientSettings.get().xaeroMap = v; ClientSettings.get().save(); })
                .enabledWhen(() -> com.glowtrack.client.XaeroDetect.anyXaero()));

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
        // 问题反馈群
        String qq = "反馈Q群 1129543042";
        g.drawString(font, qq, width - font.width(mode) - 10 - font.width(qq) - 18, 9, 0x6E7681, false);

        drawRightContent(g);
        if (timeline != null)
            timeline.updateHover(mx, my, !ClientTrackState.get().points().isEmpty());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float p) {
        super.render(g, mx, my, p);
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
        // 时间轴由独立组件绘制（窗口视图/标记/游标/悬停预览）
        // 读数区直接解析游标
        if (!pts.isEmpty()) {
            TrackPoint cp = cursor(pts);
            g.drawString(font, "时刻 " + com.glowtrack.track.TrackTimes.hms(cp.timeMillis()),
                    x, yRead, 0xD29922, false);
            g.drawString(font, "维度 " + dimName(cp.dimension().toString()), x, yRead + 12, 0x58A6FF, false);
            g.drawString(font, String.format("坐标 %.1f  %.1f  %.1f", cp.x(), cp.y(), cp.z()),
                    x, yRead + 24, 0x8B949E, false);
        } else {
            g.drawString(font, selected == null ? "未选择玩家" : "等待轨迹数据…", x, yRead, 0x6E7681, false);
        }
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

    /** 游标解析：scrubRatio 视为时间比例（与时间轴绘制一致），取最接近该时刻的点。 */
    private TrackPoint cursor(List<TrackPoint> pts) {
        long t0 = pts.get(0).timeMillis(), t1 = pts.get(pts.size() - 1).timeMillis();
        long target = t0 + (long) (ClientTrackState.get().scrubRatio() * (double) (t1 - t0));
        return com.glowtrack.track.TrackTimes.nearestByTime(pts, target);
    }

    // ---- 交互（时间轴/列表交互已由各自 widget 处理）----

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        return super.mouseReleased(mx, my, b);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
