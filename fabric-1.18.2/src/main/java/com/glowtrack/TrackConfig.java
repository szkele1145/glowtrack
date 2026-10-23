package com.glowtrack;

import com.glowtrack.fabric.GlowTrackFabric;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局配置（Fabric 版）。
 *
 * <p>与 NeoForge 母版的 {@code TrackConfig} 语义完全一致：同样的键名、同样的默认值、
 * 同样的取值范围。区别只在存储方式——</p>
 * <ul>
 *   <li>NeoForge：{@code ModConfigSpec}（Forge 自动管理生命周期）</li>
 *   <li>Fabric：自己读写 {@code config/glowtrack.toml}</li>
 * </ul>
 *
 * <p>两个平台的配置文件同名同格式，用户在 NeoForge / Fabric 之间迁移时配置可直接复用。
 * 这里刻意不引入 NightConfig 等第三方库——Fabric API 虽自带 NightConfig，
 * 但它的传递依赖在不同 MC 版本上并不一致，自己写几十行更稳。</p>
 */
public final class TrackConfig {

    private static final Path CONFIG_PATH = FabricPaths.configDir().resolve("glowtrack.toml");

    // ---- 内存中的当前值 ----
    private static int sampleIntervalTicks = 20;
    private static double minMoveDistance = 0.0D;
    private static double turnAngleThreshold = 25.0D;
    private static int retentionDays = 7;
    private static int maxPointsPerPlayer = 50000;
    private static int requiredOpLevel = 4;
    private static int settingsOpLevel = 2;
    private static String permissionNode = "glowtrack.gui";

    private TrackConfig() {}

    /** 读取配置文件；失败时保持默认值（不抛异常，避免开局直接崩）。 */
    public static void load() {
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (!Files.exists(CONFIG_PATH)) {
                writeAll();   // 首次运行：写一份带注释的默认配置
                return;
            }
            List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = stripComment(line.substring(eq + 1).trim());
                if (value.isEmpty()) continue;
                switch (key) {
                    case "sampleIntervalTicks" -> sampleIntervalTicks = clampInt(parseInt(value, 20), 1, 12000, 20);
                    case "minMoveDistance" -> minMoveDistance = clampDouble(parseDouble(value, 0.0D), 0.0D, 64.0D, 0.0D);
                    case "turnAngleThreshold" -> turnAngleThreshold = clampDouble(parseDouble(value, 25.0D), 1.0D, 180.0D, 25.0D);
                    case "retentionDays" -> retentionDays = clampInt(parseInt(value, 7), 1, 3650, 7);
                    case "maxPointsPerPlayer" -> maxPointsPerPlayer = clampInt(parseInt(value, 50000), 1000, 1000000, 50000);
                    case "requiredOpLevel" -> requiredOpLevel = clampInt(parseInt(value, 4), 0, 4, 4);
                    case "settingsOpLevel" -> settingsOpLevel = clampInt(parseInt(value, 2), 0, 4, 2);
                    case "permissionNode" -> permissionNode = unquote(value);
                    default -> { /* 未知键忽略，兼容新版配置 */ }
                }
            }
        } catch (Throwable t) {
            GlowTrackFabric.LOGGER.warn("[GlowTrack] 读取配置失败，使用默认值：{}", t.toString());
        }
    }

    /** 写回配置文件。 */
    public static void save() {
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) Files.createDirectories(parent);
            writeAll();
        } catch (Throwable t) {
            GlowTrackFabric.LOGGER.warn("[GlowTrack] 保存配置失败：{}", t.toString());
        }
    }

    private static void writeAll() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("sampleIntervalTicks", String.valueOf(sampleIntervalTicks));
        out.put("minMoveDistance", String.valueOf(minMoveDistance));
        out.put("turnAngleThreshold", String.valueOf(turnAngleThreshold));
        out.put("retentionDays", String.valueOf(retentionDays));
        out.put("maxPointsPerPlayer", String.valueOf(maxPointsPerPlayer));
        out.put("requiredOpLevel", String.valueOf(requiredOpLevel));
        out.put("settingsOpLevel", String.valueOf(settingsOpLevel));
        out.put("permissionNode", quote(permissionNode));

        StringBuilder sb = new StringBuilder();
        sb.append("# GlowTrack 配置文件\n");
        sb.append("# 与 NeoForge 版同名同格式，可直接互换。\n\n");
        sb.append("# 采样间隔（tick，20=1秒）\n");
        sb.append("sampleIntervalTicks = ").append(sampleIntervalTicks).append('\n');
        sb.append("# 最小记录位移（格），0=仅按时间采样\n");
        sb.append("minMoveDistance = ").append(minMoveDistance).append('\n');
        sb.append("# 拐弯补点的角度阈值（度）\n");
        sb.append("turnAngleThreshold = ").append(turnAngleThreshold).append('\n');
        sb.append("# 历史轨迹保留天数\n");
        sb.append("retentionDays = ").append(retentionDays).append('\n');
        sb.append("# 单玩家内存中轨迹点数上限\n");
        sb.append("maxPointsPerPlayer = ").append(maxPointsPerPlayer).append('\n');
        sb.append("# 打开追踪界面所需的最低 OP 等级\n");
        sb.append("requiredOpLevel = ").append(requiredOpLevel).append('\n');
        sb.append("# 修改全局采样/保留设置所需的最低 OP 等级\n");
        sb.append("settingsOpLevel = ").append(settingsOpLevel).append('\n');
        sb.append("# 额外权限节点 ID，留空 = 仅按 OP 等级判定\n");
        sb.append("permissionNode = ").append(quote(permissionNode)).append('\n');

        Files.writeString(CONFIG_PATH, sb.toString(), StandardCharsets.UTF_8);
    }

    // ==================== 解析辅助 ====================

    /** 去掉行尾注释（# 之后），但保留引号内的 #。 */
    private static String stripComment(String s) {
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inQuote = !inQuote;
            else if (c == '#' && !inQuote) return s.substring(0, i).trim();
        }
        return s;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return fallback; }
    }

    private static double parseDouble(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Throwable t) { return fallback; }
    }

    private static int clampInt(int v, int min, int max, int fallback) {
        return (v < min || v > max) ? fallback : v;
    }

    private static double clampDouble(double v, double min, double max, double fallback) {
        return (Double.isNaN(v) || v < min || v > max) ? fallback : v;
    }

    // ==================== 读取接口 ====================

    /** 采样间隔（tick）。 */
    public static int sampleIntervalTicks() { return sampleIntervalTicks; }

    /** 最小记录位移（格）。 */
    public static double minMoveDistance() { return minMoveDistance; }

    /** 拐弯补点的角度阈值（度）。 */
    public static double turnAngleThreshold() { return turnAngleThreshold; }

    /** 历史轨迹保留天数。 */
    public static int retentionDays() { return retentionDays; }

    /** 单玩家内存中轨迹点数上限。 */
    public static int maxPointsPerPlayer() { return maxPointsPerPlayer; }

    /** 打开追踪界面所需的最低 OP 等级。 */
    public static int requiredOpLevel() { return requiredOpLevel; }

    /** 修改全局采样/保留设置所需的最低 OP 等级。 */
    public static int settingsOpLevel() { return settingsOpLevel; }

    /** 额外权限节点 ID（留空 = 仅按 OP 等级判定）。 */
    public static String permissionNode() { return permissionNode; }

    // ==================== 写入接口 ====================

    /** 设置采样间隔并落盘。 */
    public static void setSampleIntervalTicks(int v) {
        sampleIntervalTicks = clampInt(v, 1, 12000, 20);
        save();
    }

    /** 设置保留天数并落盘。 */
    public static void setRetentionDays(int v) {
        retentionDays = clampInt(v, 1, 3650, 7);
        save();
    }
}
