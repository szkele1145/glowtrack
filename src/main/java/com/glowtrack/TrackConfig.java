package com.glowtrack;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 全局配置（COMMON 型：两端各读本地，不跨端同步）。 */
public final class TrackConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SAMPLE_INTERVAL_TICKS = B
            .comment("采样间隔（tick，20=1秒）")
            .defineInRange("sampleIntervalTicks", 20, 1, 12000);

    public static final ModConfigSpec.DoubleValue MIN_MOVE_DISTANCE = B
            .comment("最小记录位移（格），0=仅按时间采样")
            .defineInRange("minMoveDistance", 0.0D, 0.0D, 64.0D);

    public static final ModConfigSpec.DoubleValue TURN_ANGLE_THRESHOLD = B
            .comment("拐弯补点的角度阈值（度），方向变化超过此值立即补记一个点")
            .defineInRange("turnAngleThreshold", 25.0D, 1.0D, 180.0D);

    public static final ModConfigSpec.IntValue RETENTION_DAYS = B
            .comment("历史轨迹保留天数")
            .defineInRange("retentionDays", 7, 1, 3650);

    public static final ModConfigSpec.IntValue MAX_POINTS_PER_PLAYER = B
            .comment("单玩家内存中轨迹点数上限")
            .defineInRange("maxPointsPerPlayer", 50000, 1000, 1000000);

    public static final ModConfigSpec.IntValue REQUIRED_OP_LEVEL = B
            .comment("打开追踪界面所需的最低 OP 等级")
            .defineInRange("requiredOpLevel", 4, 0, 4);

    public static final ModConfigSpec.ConfigValue<String> PERMISSION_NODE = B
            .comment("额外权限节点：OP 等级不足的玩家只要拥有该 LuckPerms 节点也可使用。节点 ID 固定为 glowtrack:gui（在 LuckPerms 中授权 glowtrack.gui 即可）；此开关只控制是否启用节点检查，留空 = 仅按 OP 等级判定")
            .define("permissionNode", "glowtrack.gui");

    public static final ModConfigSpec SPEC = B.build();

    private TrackConfig() {}
}
