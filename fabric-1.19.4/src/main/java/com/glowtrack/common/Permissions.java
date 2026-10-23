package com.glowtrack.common;

import com.glowtrack.TrackConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * 权限判定（Fabric / 1.19.4 版）。
 *
 * <p>母版（NeoForge 1.21.1）用 {@code CommandSourceStack.hasPermission(int)} 直接比较
 * OP 等级。1.21.8 与母版一致，Fabric 侧没有 PermissionAPI，所以只按 OP 等级判定；
 * 配置项 {@code permissionNode} 保留但只用于日志提示。</p>
 */
public final class Permissions {

    private Permissions() {}

    /**
     * 能否使用追踪界面：只看 OP 等级。
     */
    public static boolean canUse(ServerPlayer player) {
        if (player == null) return false;
        CommandSourceStack src = player.createCommandSourceStack();
        return src.hasPermission(TrackConfig.requiredOpLevel());
    }

    /**
     * 能否修改全局配置（改采样间隔 / 保留天数）。
     */
    public static boolean canModifyGlobalSettings(ServerPlayer player) {
        if (player == null) return false;
        CommandSourceStack src = player.createCommandSourceStack();
        return src.hasPermission(TrackConfig.settingsOpLevel());
    }
}
