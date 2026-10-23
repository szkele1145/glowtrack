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
import net.minecraft.world.entity.player.PlayerSkin;
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
    private com.glowtrack.client.gui.widget.FlatButton replayBtn;   // 回放按钮（文案随状态切换）

    public TrackScreen() {
        super(com.glowtrack.client.Text.tr("glowtrack.gui.title"));
    }

    @Override
    protected void init() {
        layout();
        search = new EditBox(font, leftX + 6, top + 4, leftW - 12, 16, com.glowtrack.client.Text.tr("glowtrack.gui.search"));
        search.setMaxLength(32);
        search.setHint(com.glowtrack.client.Text.tr("glowtrack.gui.search_hint"));
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
                com.glowtrack.client.Text.tr("glowtrack.gui.start"), b -> startTrack())
                .enabledWhen(() -> selected != null));
        addRenderableWidget(FlatButton.of(x + col2 + 6, yBtn, col2, 20,
                com.glowtrack.client.Text.tr("glowtrack.gui.stop"), b -> stopTrack())
                .enabledWhen(() -> selected != null));

        // 三个渲染模式开关
        addRenderableWidget(new FlatToggle(x, yMode, col3, 16, "3D",
                () -> ClientSettings.get().mode3d, v -> { ClientSettings.get().mode3d = v; ClientSettings.get().save(); }));
        addRenderableWidget(new FlatToggle(x + col3 + 6, yMode, col3, 16, "2D",
                () -> ClientSettings.get().mode2d, v -> { ClientSettings.get().mode2d = v; ClientSettings.get().save(); }));
        addRenderableWidget(new FlatToggle(x + 2 * (col3 + 6), yMode, col3, 16,
                com.glowtrack.client.Text.s("glowtrack.gui.map"),
                () -> ClientSettings.get().xaeroMap, v -> { ClientSettings.get().xaeroMap = v; ClientSettings.get().save(); })
                .enabledWhen(() -> false));   // 本平台暂未实现 Xaero 地图叠加（Xaero 的渲染架构需重写），置灰而非假装可用

        addRenderableWidget(FlatButton.of(x, yTele, col3, 20,
                com.glowtrack.client.Text.tr("glowtrack.gui.teleport"), b -> teleport())
                .enabledWhen(() -> selected != null && !ClientTrackState.get().points().isEmpty()));
        addRenderableWidget(FlatButton.of(x + col3 + 6, yTele, col3, 20,
                com.glowtrack.client.Text.tr("glowtrack.gui.latest"), b -> {
                    ClientTrackState.get().setScrubRatio(1.0);
                    ClientTrackState.get().snapViewToLatest();
                }));
        // 回放按钮：文案随回放状态切换（▶ 回放 / ⏸ 停止回放），每帧在 renderBackground 刷新
        replayBtn = FlatButton.of(x + 2 * (col3 + 6), yTele, col3, 20,
                com.glowtrack.client.Text.tr("glowtrack.gui.replay"), b ->
                        ClientTrackState.get().toggleReplay());
        replayBtn.enabledWhen(() -> !ClientTrackState.get().points().isEmpty());
        addRenderableWidget(replayBtn);

        // 三个滑块并排（时间点间隔 | 保留天数 | 线宽），各占 1/3 宽度
        addRenderableWidget(new FlatSlider(x, ySlider, col3, 16, com.glowtrack.client.Text.s("glowtrack.gui.timepoint"),
                () -> (ClientSettings.get().timePointSeconds - 5) / 295.0,
                v -> { ClientSettings.get().timePointSeconds = (int) Math.round(5 + v * 295); ClientSettings.get().save(); },
                () -> com.glowtrack.client.Text.s("glowtrack.gui.seconds", ClientSettings.get().timePointSeconds)));
        addRenderableWidget(new FlatSlider(x + col3 + 6, ySlider, col3, 16, com.glowtrack.client.Text.s("glowtrack.gui.retention"),
                () -> (ClientSettings.get().retentionDays - 1) / 29.0,
                v -> { ClientSettings.get().retentionDays = (int) Math.round(1 + v * 29); ClientSettings.get().save(); pushSettings(); },
                () -> com.glowtrack.client.Text.s("glowtrack.gui.days", ClientSettings.get().retentionDays)));

        // 线宽滑块（1~12 格）：真实几何宽度，立即生效
        addRenderableWidget(new FlatSlider(x + 2 * (col3 + 6), ySlider, col3, 16, com.glowtrack.client.Text.s("glowtrack.gui.linewidth"),
                () -> (ClientSettings.get().lineWidth - ClientSettings.LINE_WIDTH_MIN)
                        / (double) (ClientSettings.LINE_WIDTH_MAX - ClientSettings.LINE_WIDTH_MIN),
                v -> {
                    ClientSettings.get().lineWidth = (int) Math.round(ClientSettings.LINE_WIDTH_MIN
                            + v * (ClientSettings.LINE_WIDTH_MAX - ClientSettings.LINE_WIDTH_MIN));
                    ClientSettings.get().save();
                },
                () -> com.glowtrack.client.Text.s("glowtrack.gui.blocks", ClientSettings.get().lineWidth)));

        // 底部 footer
        int yFoot = height - 26;
        addRenderableWidget(FlatButton.of(x, yFoot, col2, 18,
                com.glowtrack.client.Text.tr("glowtrack.gui.export"), b -> exportTrack()));
        addRenderableWidget(FlatButton.of(x + col2 + 6, yFoot, col2, 18,
                com.glowtrack.client.Text.tr("glowtrack.gui.close"), b -> onClose()));
    }

    private long lastPushMs = 0;

    private void pushSettings() {
        if (!ClientTrackState.get().isServerMode()) return;
        long now = System.currentTimeMillis();
        if (now - lastPushMs < 800) return;   // 滑块拖动中每帧调用，节流避免包风暴
        lastPushMs = now;
        ClientTrackState.sendToServer(new Payloads.SyncSettings(
                ClientSettings.get().sampleIntervalTicks, 0.0, 25.0, ClientSettings.get().retentionDays));
    }

    // ---- 动作 ----
    private void startTrack() {
        if (selected == null) return;
        // 切换目标时先向服务端停订旧目标（服务端 watchers 不清理会残留推送旧目标）
        UUID prev = ClientTrackState.get().tracked();
        if (prev != null && !prev.equals(selected.id()) && ClientTrackState.get().isServerMode())
            ClientTrackState.sendToServer(new Payloads.StopTracking(prev));
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
        // Shift+点击导出 CSV，普通点击导出 JSON
        String msg = Minecraft.getInstance().hasShiftDown()
                ? ClientTrackState.get().exportSelectedCsv(selected.name())
                : ClientTrackState.get().exportSelected(selected.name());
        // 成功消息后追加保存目录提示（返回串以文件名结尾即成功；失败串不加提示）
        if (msg != null && !msg.startsWith("导出失败") && !msg.equals("无轨迹可导出"))
            msg = msg + com.glowtrack.client.Text.s("glowtrack.export.hint");
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
        String mode = ClientTrackState.get().isServerMode() ? com.glowtrack.client.Text.s("glowtrack.mode.server") : com.glowtrack.client.Text.s("glowtrack.mode.local");
        g.drawString(font, mode, width - font.width(mode) - 10, 9, 0x58A6FF, false);
        // 问题反馈（国外 TG / 国内 QQ）
        String contact = com.glowtrack.client.Text.s("glowtrack.gui.contact");
        g.drawString(font, contact, width - font.width(mode) - 10 - font.width(contact) - 18, 9, 0x6E7681, false);

        // 回放按钮文案随状态切换（▶ 回放 / ⏸ 停止回放）
        if (replayBtn != null)
            replayBtn.setMessage(com.glowtrack.client.Text.tr(ClientTrackState.get().isReplaying()
                    ? "glowtrack.gui.replaying" : "glowtrack.gui.replay"));

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
            g.drawString(font, selected.online() ? com.glowtrack.client.Text.s("glowtrack.gui.online") : com.glowtrack.client.Text.s("glowtrack.gui.offline"), x + 22, yHead + 11,
                    selected.online() ? 0x3FB950 : 0x8B949E, false);
        } else {
            g.drawString(font, com.glowtrack.client.Text.s("glowtrack.gui.select_hint"), x, yHead + 4, 0x8B949E, false);
        }

        List<TrackPoint> pts = ClientTrackState.get().points();
        ClientTrackState cs = ClientTrackState.get();
        // 时间轴由独立组件绘制（窗口视图/标记/游标/悬停预览）
        // 读数区直接解析游标
        if (!pts.isEmpty()) {
            TrackPoint cp = cursor(pts);
            g.drawString(font, com.glowtrack.client.Text.s("glowtrack.gui.readout.time") + com.glowtrack.track.TrackTimes.hms(cp.timeMillis()),
                    x, yRead, 0xD29922, false);
            g.drawString(font, com.glowtrack.client.Text.s("glowtrack.gui.readout.dim") + dimName(cp.dimension() == null ? "?" : cp.dimension().toString()), x, yRead + 12, 0x58A6FF, false);
            String dist = " · " + com.glowtrack.client.Text.s("glowtrack.gui.readout.dist", fmtDist(totalDistance(pts)));
            String ptsStr = " · " + com.glowtrack.client.Text.s("glowtrack.gui.readout.points", fmtPoints(pts.size()));
            g.drawString(font, com.glowtrack.client.Text.s("glowtrack.gui.readout.pos", cp.x(), cp.y(), cp.z()) + dist + ptsStr,
                    x, yRead + 24, 0x8B949E, false);
        } else {
            g.drawString(font, selected == null ? com.glowtrack.client.Text.s("glowtrack.gui.noselection") : com.glowtrack.client.Text.s("glowtrack.gui.waiting"), x, yRead, 0x6E7681, false);
        }
    }

    /** 头像纹理缓存（与 PlayerListPane 同款；两处各自持有，量小无碍）。 */
    private static final java.util.Map<UUID, java.util.concurrent.CompletableFuture<java.util.Optional<PlayerSkin>>> SKIN_CACHE =
            new java.util.HashMap<>();

    private void drawHead(GuiGraphics g, UUID id, String name, int x, int y) {
        // 1.21.9+：皮肤是异步获取的（SkinManager.get 返回 CompletableFuture），
        // 所以这里缓存 Future，拿到就画，没拿到就画占位块。
        java.util.concurrent.CompletableFuture<java.util.Optional<PlayerSkin>> fut = SKIN_CACHE.get(id);
        if (fut == null) {
            try {
                fut = Minecraft.getInstance().getSkinManager().get(new GameProfile(id, name));
            } catch (Throwable t) {
                fut = java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty());
            }
            SKIN_CACHE.put(id, fut);
        }
        java.util.Optional<PlayerSkin> opt = fut.getNow(java.util.Optional.empty());
        if (opt.isPresent()) {
            PlayerSkin skin = opt.get();
            // 1.21.9+：PlayerSkin.texture() -> body()，再 .texturePath() 拿 Identifier。
            net.minecraft.core.ClientAsset.Texture body = skin.body();
            if (body != null) {
                g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                        body.texturePath(), x, y, 8.0f, 8.0f, 8, 8, 64, 64, 8, 8);
                return;
            }
        }
        g.fill(x, y, x + 16, y + 16, 0xFF30363D);
    }

    private String dimName(String d) {
        if (d.contains("nether")) return com.glowtrack.client.Text.s("glowtrack.dim.nether");
        if (d.contains("end")) return com.glowtrack.client.Text.s("glowtrack.dim.end");
        return com.glowtrack.client.Text.s("glowtrack.dim.overworld");
    }

    /** 游标解析：scrubRatio 视为时间比例（与时间轴绘制一致），取最接近该时刻的点。 */
    private TrackPoint cursor(List<TrackPoint> pts) {
        long t0 = pts.get(0).timeMillis(), t1 = pts.get(pts.size() - 1).timeMillis();
        long target = t0 + (long) (ClientTrackState.get().scrubRatio() * (double) (t1 - t0));
        return com.glowtrack.track.TrackTimes.nearestByTime(pts, target);
    }

    /** 总路程（相邻点距离之和，跨维度段不计，与 PlayerTrack.totalDistance 同语义）。 */
    private static double totalDistance(List<TrackPoint> pts) {
        double d = 0;
        for (int i = 1; i < pts.size(); i++) {
            TrackPoint a = pts.get(i - 1), b = pts.get(i);
            if (a.inDimension(b.dimension())) d += a.distanceTo(b);
        }
        return d;
    }

    /** 里程格式：<1000 显示 "xxx m"，≥1000 显示 "x.x km"。 */
    private static String fmtDist(double meters) {
        if (meters < 1000) return String.format(java.util.Locale.ROOT, "%.0f m", meters);
        return String.format(java.util.Locale.ROOT, "%.1f km", meters / 1000.0);
    }

    /** 点数格式：>10000 显示 "x.xk"，其余原样。 */
    private static String fmtPoints(int n) {
        if (n > 10000) return String.format(java.util.Locale.ROOT, "%.1fk", n / 1000.0);
        return String.valueOf(n);
    }

    // ---- 交互（时间轴/列表交互已由各自 widget 处理）----

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        return super.mouseReleased(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
