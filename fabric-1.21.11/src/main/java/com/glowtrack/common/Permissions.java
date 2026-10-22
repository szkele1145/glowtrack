package com.glowtrack.common;

import com.glowtrack.TrackConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * 权限判定（Fabric / 1.21.9+ 版）。
 *
 * <p>母版（NeoForge 1.21.1）用 {@code CommandSourceStack.hasPermission(int)} 直接比较
 * OP 等级。1.21.9+ 的 Mojmap 把这套换成了 {@code PermissionSet} 体系：
 * 玩家的权限集来自 {@code ServerPlayer.permissions()}，比较用
 * {@code LevelBasedPermissionSet}。语义不变：仍是按 OP 等级（0~4）判定。</p>
 *
 * <p>Fabric 侧没有 PermissionAPI，所以 {@code permissionNode} 配置项依然只是
 * 占位（读出来仅用于日志提示），判定完全走 OP 等级。</p>
 */
public final class Permissions {

    private Permissions() {}

    /** 配置里的 OP 等级数字（0~4）转成 1.21.9+ 的 PermissionLevel。 */
    private static PermissionLevel levelOf(int opLevel) {
        return PermissionLevel.byId(Math.max(0, Math.min(4, opLevel)));
    }

    /**
     * 能否使用追踪界面：只看 OP 等级。
     */
    public static boolean canUse(ServerPlayer player) {
        if (player == null) return false;
        return hasLevel(player, TrackConfig.requiredOpLevel());
    }

    /**
     * 能否修改全局配置（改采样间隔 / 保留天数）。
     */
    public static boolean canModifyGlobalSettings(ServerPlayer player) {
        if (player == null) return false;
        return hasLevel(player, TrackConfig.settingsOpLevel());
    }

    /** 玩家权限等级 >= 要求等级。 */
    private static boolean hasLevel(ServerPlayer player, int required) {
        try {
            net.minecraft.server.permissions.PermissionSet ps = player.permissions();
            if (ps instanceof LevelBasedPermissionSet lbps) {
                return lbps.level().isEqualOrHigherThan(levelOf(required));
            }
            // 未知权限集实现：兜底判 ALL_PERMISSIONS（单机/OP 默认就是全量）
            return ps == net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS && required <= 4;
        } catch (Throwable t) {
            return false;
        }
    }
}
