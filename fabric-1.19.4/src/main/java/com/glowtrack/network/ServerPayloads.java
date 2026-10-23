package com.glowtrack.network;

import com.glowtrack.TrackConfig;
import com.glowtrack.common.Permissions;
import com.glowtrack.fabric.GlowTrackFabric;
import com.glowtrack.server.ServerTrackManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端处理全部 C-&gt;S 包（Fabric 版），均做权限校验。
 *
 * <p>处理逻辑与 NeoForge 母版逐条对应，只是调用入口从
 * {@code IPayloadContext} 换成了 {@code ServerPlayNetworking} 的接收器。</p>
 */
public final class ServerPayloads {

    private ServerPayloads() {}

    /** 注册全部 C-&gt;S 接收器。 */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.TrackListReq.TYPE,
                (payload, player, sender) -> handleListReq(payload, player));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.StartTracking.TYPE,
                (payload, player, sender) -> handleStartTracking(payload, player));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.StopTracking.TYPE,
                (payload, player, sender) -> handleStopTracking(payload, player));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.SyncSettings.TYPE,
                (payload, player, sender) -> handleSyncSettings(payload, player));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.TeleportTo.TYPE,
                (payload, player, sender) -> handleTeleportTo(payload, player));
    }

    private static void deny(ServerPlayer player) {
        if (player != null)
            player.displayClientMessage(Component.translatable("glowtrack.gui.no_permission"), false);
    }

    public static void handleListReq(Payloads.TrackListReq msg, ServerPlayer player) {
        if (player == null) return;
        if (!Permissions.canUse(player)) { deny(player); return; }
        ServerTrackManager.get().sendTrackList(player);
    }

    public static void handleStartTracking(Payloads.StartTracking msg, ServerPlayer player) {
        if (player == null) return;
        if (!Permissions.canUse(player)) { deny(player); return; }
        ServerTrackManager.get().startTracking(player, msg.player(), msg.days());
    }

    public static void handleStopTracking(Payloads.StopTracking msg, ServerPlayer player) {
        if (player == null) return;
        if (!Permissions.canUse(player)) { deny(player); return; }
        ServerTrackManager.get().stopTracking(player, msg.player());
    }

    public static void handleSyncSettings(Payloads.SyncSettings msg, ServerPlayer player) {
        if (player == null) return;
        // 注意：这里查的是 canModifyGlobalSettings 而不是 canUse。
        // 采样间隔 / 保留天数影响全服所有玩家，若用 canUse，一个被权限插件
        // 授予 glowtrack.gui 的普通玩家也能改全服保留天数（改成 1 天等于清空所有人的历史轨迹）。
        if (!Permissions.canModifyGlobalSettings(player)) { deny(player); return; }
        // 服务端侧钳制范围（与配置 spec 一致），恶意客户端改任何值都不会影响配置。
        int interval = Math.max(1, Math.min(12000, msg.sampleIntervalTicks()));
        int retention = Math.max(1, Math.min(3650, msg.retentionDays()));
        TrackConfig.setSampleIntervalTicks(interval);
        TrackConfig.setRetentionDays(retention);
        GlowTrackFabric.LOGGER.info("[GlowTrack] {} 更新采样设置 interval={} retention={}",
                player.getName().getString(), interval, retention);
    }

    public static void handleTeleportTo(Payloads.TeleportTo msg, ServerPlayer player) {
        if (player == null) return;
        if (!Permissions.canUse(player)) { deny(player); return; }
        ServerTrackManager.get().teleportTo(player, msg.player(), msg.timeMillis());
    }
}
