package com.glowtrack.network;

import com.glowtrack.GlowTrack;
import com.glowtrack.TrackConfig;
import com.glowtrack.common.Permissions;
import com.glowtrack.server.ServerTrackManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端处理全部 C-&gt;S 包，均做权限校验。
 *
 * <p>线上只有 {@link Envelope}，这里按逻辑包 id 分派回具体的 record。</p>
 */
public final class ServerPayloads {

    private ServerPayloads() {}

    /** 收到一个信封：解析出具体包并按权限处理。 */
    public static void dispatch(Envelope env, ServerPlayer player) {
        ResourceLocation id = env.id();
        try {
            if (Payloads.TRACK_LIST_REQ_ID.equals(id)) {
                handleListReq(Payloads.TrackListReq.decode(env.data()), player);
            } else if (Payloads.START_TRACKING_ID.equals(id)) {
                handleStartTracking(Payloads.StartTracking.decode(env.data()), player);
            } else if (Payloads.STOP_TRACKING_ID.equals(id)) {
                handleStopTracking(Payloads.StopTracking.decode(env.data()), player);
            } else if (Payloads.SYNC_SETTINGS_ID.equals(id)) {
                handleSyncSettings(Payloads.SyncSettings.decode(env.data()), player);
            } else if (Payloads.TELEPORT_TO_ID.equals(id)) {
                handleTeleportTo(Payloads.TeleportTo.decode(env.data()), player);
            }
        } catch (Throwable t) {
            GlowTrack.LOGGER.warn("[GlowTrack] 处理 {} 失败: {}", id, t.toString());
        }
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
        // 采样间隔 / 保留天数影响全服所有玩家，若沿用 canUse，一个被
        // LuckPerms 授予 glowtrack.gui 的普通玩家就能改全服保留天数
        // （改成 1 = 清空所有人的历史轨迹）。
        if (!Permissions.canModifyGlobalSettings(player)) { deny(player); return; }
        // 服务端侧钳制范围（与配置 spec 一致），恶意客户端发任意值也不会污染配置：
        int interval = Math.max(1, Math.min(12000, msg.sampleIntervalTicks()));
        int retention = Math.max(1, Math.min(3650, msg.retentionDays()));
        TrackConfig.SAMPLE_INTERVAL_TICKS.set(interval);
        TrackConfig.RETENTION_DAYS.set(retention);
        GlowTrack.LOGGER.info("[GlowTrack] {} 更新采样设置 interval={} retention={}",
                player.getName().getString(), interval, retention);
    }

    public static void handleTeleportTo(Payloads.TeleportTo msg, ServerPlayer player) {
        if (player == null) return;
        if (!Permissions.canUse(player)) { deny(player); return; }
        ServerTrackManager.get().teleportTo(player, msg.player(), msg.timeMillis());
    }
}
