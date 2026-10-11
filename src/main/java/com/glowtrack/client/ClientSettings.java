package com.glowtrack.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.glowtrack.GlowTrack;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端渲染设置（持久化到磁盘）。
 *
 * <p>所有渲染样式参数集中于此，改动即刻生效并写盘，
 * 重启后保留。存于 {@code .minecraft/glowtrack/settings.json}。</p>
 */
public final class ClientSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientSettings instance;

    // ---- 渲染 ----
    public RenderStyle style = RenderStyle.RAINBOW;
    /** 单色/彩虹主题的基准色相（0..1），RAINBOW 时作为起始色相。 */
    public float baseHue = 0.55f;
    public float lineWidth = 4.0f;
    public boolean glow = true;
    public boolean wallhack = true;
    public boolean timeLabels = true;
    public boolean dashPulse = true;
    public boolean showArrows = true;
    public boolean showMarkers = true;   // 起点/终点/当前点标记

    // ---- 采样（同步到服务端）----
    public int sampleIntervalTicks = 20;
    public int retentionDays = 7;

    public static ClientSettings get() {
        if (instance == null) {
            instance = load();
        }
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

    /** 立即写盘。 */
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
