package com.glowtrack.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.glowtrack.GlowTrack;
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

    /** 时间点间隔（分钟）。 */
    public int timePointMinutes = 5;
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
                if (s != null) return s;
            }
        } catch (Exception e) {
            GlowTrack.LOGGER.warn("[GlowTrack] 读取客户端设置失败: {}", e.getMessage());
        }
        return new ClientSettings();
    }

    public void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            GlowTrack.LOGGER.warn("[GlowTrack] 保存客户端设置失败: {}", e.getMessage());
        }
    }
}
