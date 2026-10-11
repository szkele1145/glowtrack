package com.glowtrack.server;

import com.glowtrack.GlowTrack;
import com.glowtrack.network.Payloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** 玩家进服时下发握手包，让客户端进入服务端模式。 */
@EventBusSubscriber(modid = GlowTrack.MOD_ID)
public final class ServerConnectionEvents {
    private ServerConnectionEvents() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ServerTrackManager.initIfNeeded(sp.server);
            PacketDistributor.sendToPlayer(sp, new Payloads.Hello(1));
            if (com.glowtrack.TrackConfig.PERMISSION_NODE.get() != null) {
                // 提示权限节点（便于 LuckPerms 管理员配置）
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && ServerTrackManager.get() != null) {
            ServerTrackManager.get().removeWatcher(sp.getUUID());
        }
    }
}
