package com.glowtrack.client.gui.widget;

import com.glowtrack.client.ClientSettings;
import com.glowtrack.client.ClientTrackState;
import com.glowtrack.track.TrackPoint;
import com.glowtrack.track.TrackTimes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 时间轴组件：窗口化轨迹条 + 时间点标记 + 游标 + 滚动条 + 悬停预览。
 *
 * <p>交互约定：</p>
 * <ul>
 *   <li>拖动 = 平移窗口（默认视口锁定最新端，往左拖看历史）</li>
 *   <li>单击（未拖动）= 在该位置设传送游标</li>
 *   <li>滚轮 = 以鼠标位置为锚点缩放窗口</li>
 * </ul>
 *
 * <p>从 TrackScreen 拆出，绘制与交互自包含，宿主只负责布局与转发事件。</p>
 */
public class TimelineWidget extends AbstractWidget {

    private static final int BG       = 0xFF0D1117;
    private static final int BAR_BG   = 0xFF222831;
    private static final int BAR_THUMB = 0xFF6E7681;
    private static final int CURSOR   = 0xFFFFFFFF;
    private static final int TICK     = 0xCCFFFFFF;
    private static final int DIM_MARK = 0xFFFFFFFF;
    private static final int TXT_DIM  = 0x484F58;
    private static final int TIP_BG   = 0xE01C2129;
    private static final int TIP_TXT  = 0xC9D1D9;

    /** 速度热力色（蓝→绿→红）。 */
    public static int heat(float sp) {
        float v = Math.max(0f, Math.min(1f, sp / 3f));
        int r, gr, b;
        if (v < 0.5f) { r = 0; gr = (int) (v * 2 * 255); b = 255 - gr; }
        else { int t = (int) ((v - 0.5) * 2 * 255); r = t; gr = 255 - t; b = 0; }
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private boolean dragging = false;
    private boolean dragMoved = false;
    private double dragStartX = 0;
    private double dragStartViewEnd = 1.0;
    private int hoverX = -1, hoverY = -1;

    public TimelineWidget(int x, int y, int w, int h) {
        super(x, y, w, h, com.glowtrack.client.Text.tr("glowtrack.gui.timepoint"));
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<TrackPoint> pts = ClientTrackState.get().points();
        ClientTrackState cs = ClientTrackState.get();
        int tlX = getX(), tlW = width, yTl = getY();
        g.fill(tlX, yTl, tlX + tlW, yTl + height, BG);
        var font = Minecraft.getInstance().font;

        if (pts.isEmpty()) {
            g.drawString(font, com.glowtrack.client.Text.s("glowtrack.gui.timeline_hint"), tlX + 4, yTl + 4, TXT_DIM, false);
            return;
        }

        double vStart = cs.viewStartRatio(), vEnd = cs.viewEndRatio();
        long t0 = pts.get(0).timeMillis(), t1 = pts.get(pts.size() - 1).timeMillis();
        long span = Math.max(1, t1 - t0);
        double win = Math.max(1e-9, vEnd - vStart);

        // 窗口内的轨迹条（按时间比例映射到 x）
        for (TrackPoint p : pts) {
            double r = (p.timeMillis() - t0) / (double) span;
            if (r < vStart || r > vEnd) continue;
            int px = tlX + (int) ((r - vStart) / win * (tlW - 1));
            g.fill(px, yTl + 2, px + 1, yTl + height - 2, heat(p.speed()));
        }
        // 维度切换标记（窗口内）
        for (int i = 1; i < pts.size(); i++) {
            if (!pts.get(i).inDimension(pts.get(i - 1).dimension())) {
                double r = (pts.get(i).timeMillis() - t0) / (double) span;
                if (r < vStart || r > vEnd) continue;
                int dx = tlX + (int) ((r - vStart) / win * (tlW - 1));
                g.fill(dx, yTl, dx + 1, yTl + height, DIM_MARK);
            }
        }
        // 时间点标记（每 N 秒一个白色小竖线）
        int sec = Math.max(5, ClientSettings.get().timePointSeconds);
        for (long t = (t0 / (sec * 1000L)) * sec * 1000L; t <= t1; t += sec * 1000L) {
            double r = (t - t0) / (double) span;
            if (r < vStart || r > vEnd) continue;
            int px = tlX + (int) ((r - vStart) / win * (tlW - 1));
            g.fill(px, yTl, px + 1, yTl + 4, TICK);
        }
        // 游标（若在窗口内）
        double sc = cs.scrubRatio();
        if (sc >= vStart && sc <= vEnd) {
            int cur = tlX + (int) ((sc - vStart) / win * (tlW - 1));
            g.fill(cur - 1, yTl - 1, cur + 1, yTl + height + 1, CURSOR);
        }
        // 滚动条（指示当前窗口在全长中的位置）
        int sbY = yTl + height + 2;
        g.fill(tlX, sbY, tlX + tlW, sbY + 2, BAR_BG);
        int thumbX = tlX + (int) (vStart * (tlW - 1));
        int thumbW = Math.max(6, (int) (win * (tlW - 1)));
        g.fill(thumbX, sbY, thumbX + thumbW, sbY + 2, BAR_THUMB);

        // 窗口起止时刻（滚动条右侧），末尾附当前回放速率
        String range = TrackTimes.hms(t0 + (long) (vStart * span))
                + " → " + TrackTimes.hms(t0 + (long) (vEnd * span))
                + " · " + com.glowtrack.client.Text.s("glowtrack.gui.replayspeed",
                        fmtSpeed(ClientTrackState.getReplaySpeed()));
        g.drawString(font, range, tlX + tlW - font.width(range), sbY + 5, TXT_DIM, false);

        // 悬停预览：该时刻 时间+坐标 提示框
        if (hoverX >= 0 && hoverY >= yTl - 2 && hoverY <= yTl + height + 8) {
            double hr = vStart + (hoverX - tlX) / (double) tlW * win;
            if (hr >= 0 && hr <= 1) {
                TrackPoint hp = TrackTimes.nearestByTime(pts, t0 + (long) (hr * span));
                if (hp != null) {
                    String tip = TrackTimes.hms(hp.timeMillis())
                            + String.format("  %.0f,%.0f,%.0f", hp.x(), hp.y(), hp.z());
                    int tw = font.width(tip) + 8;
                    int tx = Math.min(hoverX + 6, tlX + tlW - tw);
                    int ty = yTl - 14;
                    g.fill(tx, ty, tx + tw, ty + 12, TIP_BG);
                    g.drawString(font, tip, tx + 4, ty + 2, TIP_TXT, false);
                }
            }
        }
    }

    /** 速率格式：<10 显示一位小数（如 4.5x），否则取整（如 30x）。 */
    private static String fmtSpeed(double v) {
        if (v < 10) return String.format(java.util.Locale.ROOT, "%.1f", v);
        return String.format(java.util.Locale.ROOT, "%.0f", v);
    }

    // ---- 交互 ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isMouseOver(mx, my)) return false;   // 面板外不吞点击
        dragging = true;
        dragMoved = false;
        dragStartX = mx;
        dragStartViewEnd = ClientTrackState.get().viewEndRatio();
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return false;
        if (Math.abs(mx - dragStartX) > 3) dragMoved = true;
        if (dragMoved) {
            // 拖动平移窗口（左拖=看历史）
            double span = ClientTrackState.get().viewSpanRatio();
            double delta = -(mx - dragStartX) / width * span;
            ClientTrackState.get().panView(dragStartViewEnd + delta - ClientTrackState.get().viewEndRatio());
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        // 点击（未拖动）= 在窗口内设置传送游标
        if (dragging && !dragMoved && !ClientTrackState.get().points().isEmpty()) {
            ClientTrackState cs = ClientTrackState.get();
            double r = cs.viewStartRatio() + (mx - getX()) / width * (cs.viewEndRatio() - cs.viewStartRatio());
            cs.setScrubRatio(r);
        }
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double xScroll, double yScroll) {
        if (!isMouseOver(mx, my)) return false;   // 面板外滚轮不吞（留给玩家列表滚动）
        ClientTrackState cs = ClientTrackState.get();
        // Ctrl+滚轮 = 调回放速率（×1.5 / ÷1.5，clamp 1..120）；普通滚轮 = 缩放窗口（锚点=鼠标位置）
        if (Screen.hasControlDown()) {
            ClientTrackState.setReplaySpeed(
                    ClientTrackState.getReplaySpeed() * (yScroll > 0 ? 1.5 : 1 / 1.5));
            return true;
        }
        double anchor = cs.viewStartRatio()
                + (mx - getX()) / (double) width * (cs.viewEndRatio() - cs.viewStartRatio());
        cs.zoomView(yScroll > 0 ? 0.8 : 1.25, anchor);
        return true;
    }

    /** 宿主每帧转发鼠标位置（悬停预览用）。 */
    public void updateHover(double mx, double my, boolean hasPoints) {
        hoverX = (mx >= getX() && mx <= getX() + width && hasPoints) ? (int) mx : -1;
        hoverY = (int) my;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
