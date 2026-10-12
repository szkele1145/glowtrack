package com.glowtrack.network;

import com.glowtrack.GlowTrack;
import com.glowtrack.TrackConfig;
import com.glowtrack.common.Permissions;
import com.glowtrack.server.ServerTrackManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 服务端处理全部 C->S 包，均做权限校验。 */
public final class ServerPayloads {
    private ServerPayloads() {}

    private static ServerPlayer sender(IPayloadContext ctx) {
        return ctx.player() instanceof ServerPlayer sp ? sp : null;
    }

    private static void deny(IPayloadContext ctx) {
        ServerPlayer sp = sender(ctx);
        if (sp != null)
            sp.displayClientMessage(Component.literal("[GlowTrack] 没有权限使用追踪功能"), false);
    }

    public static void handleListReq(Payloads.TrackListReq msg, IPayloadContext ctx) {
        ServerPlayer player = sender(ctx);
        if (player == null) return;
        if (!Permissions.canUse(player)) { deny(ctx); return; }
        ServerTrackManager.get().sendTrackList(player);
    }

    public static void handleStartTracking(Payloads.StartTracking msg, IPayloadContext ctx) {
        ServerPlayer player = sender(ctx);
        if (player == null) return;
        if (!Permissions.canUse(player)) { deny(ctx); return; }
        ServerTrackManager.get().startTracking(player, msg.player(), msg.days());
    }

    public static void handleStopTracking(Payloads.StopTracking msg, IPayloadContext ctx) {
        ServerPlayer player = sender(ctx);
        if (player == null) return;
        if (!Permissions.canUse(player)) { deny(ctx); return; }
        ServerTrackManager.get().stopTracking(player, msg.player());
    }

    public static void handleSyncSettings(Payloads.SyncSettings msg, IPayloadContext ctx) {
        ServerPlayer player = sender(ctx);
        if (player == null) return;
        if (!Permissions.canUse(player)) { deny(ctx); return; }
        // 只同步 GUI 可控的两项：采样间隔、保留天数。
        // minMoveDistance / turnAngle 由服务端配置文件决定，不被客户端覆盖。
        // 服务端侧钳制范围（与配置 spec 一致），恶意客户端发任意值也不会污染配置：
        int interval = Math.max(1, Math.min(12000, msg.sampleIntervalTicks()));
        int retention = Math.max(1, Math.min(3650, msg.retentionDays()));
        TrackConfig.SAMPLE_INTERVAL_TICKS.set(interval);
        TrackConfig.RETENTION_DAYS.set(retention);
        GlowTrack.LOGGER.info("[GlowTrack] {} 更新采样设置 interval={} retention={}",
                player.getName().getString(), msg.sampleIntervalTicks(), msg.retentionDays());
    }

    public static void handleTeleportTo(Payloads.TeleportTo msg, IPayloadContext ctx) {
        ServerPlayer player = sender(ctx);
        if (player == null) return;
        if (!Permissions.canUse(player)) { deny(ctx); return; }
        ServerTrackManager.get().teleportTo(player, msg.player(), msg.timeMillis());
    }
}
