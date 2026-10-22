package com.glowtrack.client;

import com.glowtrack.fabric.GlowTrackFabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端设置（持久化到磁盘）。
 *
 * <p>渲染效果（热力/发光/穿墙/时间标签/虚线）按选品既定、固定无开关。
 * 此处只保留三个渲染模式开关（3D / 2D / Xaero 地图）与采样、保留设置。</p>
 */
public final class ClientSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientSettings instance;

    // ---- 三种渲染模式（三个开关）----
    /** 3D 立体线：在轨迹真实位置画线。 */
    public boolean mode3d = true;
    /** 2D 平面线：取 X/Z，在观察者高度下方 5 格的固定平面画线（高空巡逻用）。 */
    public boolean mode2d = false;
    /** Xaero 地图联动：小地图 + 世界地图画线 + 时间点。 */
    public boolean xaeroMap = false;

    // ---- 采样与保留 ----
    public int sampleIntervalTicks = 20;
    public int retentionDays = 7;

    /**
     * 追踪线宽度（世界格），范围 1~12。
     *
     * <p>线宽是真实几何宽度（三角带按法线向两侧偏移），不是 glLineWidth——
     * 后者在现代驱动上被钳到 1~2 像素，调了也没用。</p>
     */
    public int lineWidth = 5;

    /** 线宽下限/上限（GUI 滑块与设置加载时统一夹取）。 */
    public static final int LINE_WIDTH_MIN = 1;
    public static final int LINE_WIDTH_MAX = 12;

    /** 夹取线宽到合法范围（设置文件被手改过也不会越界）。 */
    public void clampLineWidth() {
        this.lineWidth = Math.max(LINE_WIDTH_MIN, Math.min(LINE_WIDTH_MAX, this.lineWidth));
    }

    /** 时间点间隔（秒），最低 5 秒。 */
    public int timePointSeconds = 30;
    /** 融合阈值：某区域内点数超过此值即融合成范围标签。 */
    public int mergeThreshold = 1;

    public static ClientSettings get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("glowtrack").resolve("settings.json");
    }

    private static ClientSettings load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                String json = Files.readString(f, StandardCharsets.UTF_8);
                ClientSettings s = GSON.fromJson(json, ClientSettings.class);
                if (s != null) {
                    s.clampLineWidth();   // 老配置没这个字段时用默认值，手改过的值也夹到合法区间
                    return s;
                }
            }
        } catch (Exception e) {
            GlowTrackFabric.LOGGER.warn("[GlowTrack] 读取客户端设置失败: {}", e.getMessage());
        }
        return new ClientSettings();
    }

    private long lastSaveMs = 0;

    /**
     * 保存（节流：距上次落盘不足 500ms 时跳过，滑动条连续拖动时不会每帧写盘）。
     * 被跳过的保存由下一次调用兜底（拖动结束时必然触发最后一次）。
     */
    public void save() {
        long now = System.currentTimeMillis();
        if (now - lastSaveMs < 500) return;
        lastSaveMs = now;
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            GlowTrackFabric.LOGGER.warn("[GlowTrack] 保存客户端设置失败: {}", e.getMessage());
        }
    }

    /** 强制落盘（无视节流，用于 GUI 关闭/退出存档）。 */
    public void saveNow() {
        lastSaveMs = 0;
        save();
    }
}
