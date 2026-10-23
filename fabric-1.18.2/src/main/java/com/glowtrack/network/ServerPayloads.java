package com.glowtrack.network;

import com.glowtrack.TrackConfig;
import com.glowtrack.common.Permissions;
import com.glowtrack.fabric.GlowTrackFabric;
import com.glowtrack.server.ServerTrackManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

/**
 * 服务端处理全部 C-&gt;S 包（Fabric 版），均做权限校验。
 *
 * <p>处理逻辑与 NeoForge 母版逐条对应，只是调用入口换成了
 * {@code ServerPlayNetworking} 的通道接收器。</p>
 *
 * <p><b>1.18.2 注意</b>：这一版 Fabric 的接收器回调跑在 <b>网络线程</b>，
 * 而本模组的处理器会直接读写世界/玩家状态，所以一律先
 * {@code server.execute(...)} 切回主线程再处理（1.20.5+ 那套 FabricPacket
 * 接收器也一样是网络线程，行为一致）。</p>
 */
public final class ServerPayloads {

    private ServerPayloads() {}

    /** 注册全部 C-&gt;S 接收器。 */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.TRACK_LIST_REQ_ID,
                (server, player, handler, buf, sender) -> {
                    Payloads.TrackListReq msg = Payloads.TrackListReq.decode(buf);
                    server.execute(() -> handleListReq(msg, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(Payloads.START_TRACKING_ID,
                (server, player, handler, buf, sender) -> {
                    Payloads.StartTracking msg = Payloads.StartTracking.decode(buf);
                    server.execute(() -> handleStartTracking(msg, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(Payloads.STOP_TRACKING_ID,
                (server, player, handler, buf, sender) -> {
                    Payloads.StopTracking msg = Payloads.StopTracking.decode(buf);
                    server.execute(() -> handleStopTracking(msg, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(Payloads.SYNC_SETTINGS_ID,
                (server, player, handler, buf, sender) -> {
                    Payloads.SyncSettings msg = Payloads.SyncSettings.decode(buf);
                    server.execute(() -> handleSyncSettings(msg, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(Payloads.TELEPORT_TO_ID,
                (server, player, handler, buf, sender) -> {
                    Payloads.TeleportTo msg = Payloads.TeleportTo.decode(buf);
                    server.execute(() -> handleTeleportTo(msg, player));
                });
    }

    private static void deny(ServerPlayer player) {
        if (player != null)
            player.displayClientMessage(new net.minecraft.network.chat.TranslatableComponent("glowtrack.gui.no_permission"), false);
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
