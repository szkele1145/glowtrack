package com.glowtrack.network;

import com.glowtrack.GlowTrack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 统一注册全部网络包的编解码与处理器。 */
@EventBusSubscriber(modid = GlowTrack.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NetworkHandler {
    private NetworkHandler() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // S -> C（仅客户端 dist 注册：服务端永不加载 ClientPayloads 及其 client 依赖，彻底隔离）
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            registrar.playToClient(Payloads.Hello.TYPE, Payloads.Hello.CODEC,
                    ClientPayloads::handleHello);
            registrar.playToClient(Payloads.TrackList.TYPE, Payloads.TrackList.CODEC,
                    ClientPayloads::handleTrackList);
            registrar.playToClient(Payloads.TrackData.TYPE, Payloads.TrackData.CODEC,
                    ClientPayloads::handleTrackData);
        }

        // C -> S
        registrar.playToServer(Payloads.TrackListReq.TYPE, Payloads.TrackListReq.CODEC,
                ServerPayloads::handleListReq);
        registrar.playToServer(Payloads.StartTracking.TYPE, Payloads.StartTracking.CODEC,
                ServerPayloads::handleStartTracking);
        registrar.playToServer(Payloads.StopTracking.TYPE, Payloads.StopTracking.CODEC,
                ServerPayloads::handleStopTracking);
        registrar.playToServer(Payloads.SyncSettings.TYPE, Payloads.SyncSettings.CODEC,
                ServerPayloads::handleSyncSettings);
        registrar.playToServer(Payloads.TeleportTo.TYPE, Payloads.TeleportTo.CODEC,
                ServerPayloads::handleTeleportTo);
    }
}
