package com.glowtrack;

import net.minecraftforge.common.ForgeConfigSpec;

/** 全局配置（COMMON 型：两端各读本地，不跨端同步）。 */
public final class TrackConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue SAMPLE_INTERVAL_TICKS = B
            .comment("采样间隔（tick，20=1秒）")
            .defineInRange("sampleIntervalTicks", 20, 1, 12000);

    public static final ForgeConfigSpec.DoubleValue MIN_MOVE_DISTANCE = B
            .comment("最小记录位移（格），0=仅按时间采样")
            .defineInRange("minMoveDistance", 0.0D, 0.0D, 64.0D);

    public static final ForgeConfigSpec.DoubleValue TURN_ANGLE_THRESHOLD = B
            .comment("拐弯补点的角度阈值（度），方向变化超过此值立即补记一个点")
            .defineInRange("turnAngleThreshold", 25.0D, 1.0D, 180.0D);

    public static final ForgeConfigSpec.IntValue RETENTION_DAYS = B
            .comment("历史轨迹保留天数")
            .defineInRange("retentionDays", 7, 1, 3650);

    public static final ForgeConfigSpec.IntValue MAX_POINTS_PER_PLAYER = B
            .comment("单玩家内存中轨迹点数上限")
            .defineInRange("maxPointsPerPlayer", 50000, 1000, 1000000);

    public static final ForgeConfigSpec.IntValue REQUIRED_OP_LEVEL = B
            .comment("打开追踪界面所需的最低 OP 等级")
            .defineInRange("requiredOpLevel", 4, 0, 4);

    public static final ForgeConfigSpec.IntValue SETTINGS_OP_LEVEL = B
            .comment("修改全局采样/保留设置所需的最低 OP 等级（独立于 requiredOpLevel：这些设置影响全服，不能被 LuckPerms 追踪权限顺带授予）")
            .defineInRange("settingsOpLevel", 2, 0, 4);

    public static final ForgeConfigSpec.ConfigValue<String> PERMISSION_NODE = B
            .comment("额外权限节点：OP 等级不足的玩家只要拥有该 LuckPerms 节点也可使用。节点 ID 固定为 glowtrack:gui（在 LuckPerms 中授权 glowtrack.gui 即可）；此开关只控制是否启用节点检查，留空 = 仅按 OP 等级判定")
            .define("permissionNode", "glowtrack.gui");

    public static final ForgeConfigSpec SPEC = B.build();

    private TrackConfig() {}

    /**
     * 安全的配置读取：配置尚未加载时回退到 spec 里的默认值。
     *
     * <p>{@link ForgeConfigSpec.ConfigValue#get()} 在配置未加载时抛
     * {@code IllegalStateException}。服务端跑起来之后配置总是加载好的，
     * 但纯 JVM 环境的自检（无 NeoForge 运行时）会撞上这个异常——
     * 采样器这类纯逻辑类必须能在没有游戏运行时的环境里被测试。</p>
     */
    private static <T> T safe(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** 采样间隔（tick）。 */
    public static int sampleIntervalTicks() {
        return safe(SAMPLE_INTERVAL_TICKS, 20);
    }

    /** 最小记录位移（格）。 */
    public static double minMoveDistance() {
        return safe(MIN_MOVE_DISTANCE, 0.0D);
    }

    /** 拐弯补点的角度阈值（度）。 */
    public static double turnAngleThreshold() {
        return safe(TURN_ANGLE_THRESHOLD, 25.0D);
    }

    /** 历史轨迹保留天数。 */
    public static int retentionDays() {
        return safe(RETENTION_DAYS, 7);
    }

    /** 单玩家内存中轨迹点数上限。 */
    public static int maxPointsPerPlayer() {
        return safe(MAX_POINTS_PER_PLAYER, 50000);
    }

    /** 打开追踪界面所需的最低 OP 等级。 */
    public static int requiredOpLevel() {
        return safe(REQUIRED_OP_LEVEL, 4);
    }

    /** 修改全局设置所需的最低 OP 等级。 */
    public static int settingsOpLevel() {
        return safe(SETTINGS_OP_LEVEL, 2);
    }

    /** 额外权限节点 ID（留空 = 仅按 OP 等级判定）。 */
    public static String permissionNode() {
        return safe(PERMISSION_NODE, "glowtrack.gui");
    }
}
