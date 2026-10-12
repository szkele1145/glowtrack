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
        // optional：对端未安装本模组时不阻断连接（客户端降级为本地观察，服务端发包自动跳过）。
        // 注意：S->C 通道两端都必须注册——专用服务器只有注册了 client-bound 通道才能声明并下发数据包；
        // 若按 dist 门控，服务端不声明通道，装了模组的客户端反而会被"服务端缺少通道"踢出
        // （v1.1.6/1.1.7 的回归，v1.1.8 修复）。handler 是惰性方法引用，
        // 服务端加载 ClientPayloads 类的风险由 ServerNet 的 try/catch 兜底（收不到 S->C 包则永不触发）。
        final PayloadRegistrar registrar = event.registrar("1").optional();

        // S -> C（两端都注册；服务端实际只在 handler 被调用时才加载 ClientPayloads）
        registrar.playToClient(Payloads.Hello.TYPE, Payloads.Hello.CODEC,
                ClientPayloads::handleHello);
        registrar.playToClient(Payloads.TrackList.TYPE, Payloads.TrackList.CODEC,
                ClientPayloads::handleTrackList);
        registrar.playToClient(Payloads.TrackData.TYPE, Payloads.TrackData.CODEC,
                ClientPayloads::handleTrackData);

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
