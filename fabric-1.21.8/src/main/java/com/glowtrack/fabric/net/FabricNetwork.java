package com.glowtrack.fabric.net;

import com.glowtrack.network.ActionDataPackets;
import com.glowtrack.network.Payloads;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Fabric 网络包注册（1.21.11）。
 *
 * <p>1.20.5+ 的 Minecraft 用 {@code CustomPacketPayload} + {@code StreamCodec} 描述数据包，
 * Fabric 侧用 {@code PayloadTypeRegistry} 把类型登记到 play 通道。
 * 客户端/服务端的消息处理器则用
 * {@code ServerPlayNetworking.registerGlobalReceiver} /
 * {@code ClientPlayNetworking.registerGlobalReceiver} 注册。</p>
 *
 * <p>协议字节序列与 NeoForge 母版完全一致（同一套 {@code StreamCodec}），
 * 所以同一版本下 NeoForge 客户端连 Fabric 服务端也能互通。</p>
 */
public final class FabricNetwork {

    private FabricNetwork() {}

    /** 注册所有 play 阶段的数据包类型。 */
    public static void register() {
        // S -> C
        PayloadTypeRegistry.playS2C().register(Payloads.Hello.TYPE, Payloads.Hello.CODEC);
        PayloadTypeRegistry.playS2C().register(Payloads.TrackList.TYPE, Payloads.TrackList.CODEC);
        PayloadTypeRegistry.playS2C().register(ActionDataPackets.ActionData.TYPE, ActionDataPackets.ActionData.CODEC);
        PayloadTypeRegistry.playS2C().register(Payloads.TrackData.TYPE, Payloads.TrackData.CODEC);

        // C -> S
        PayloadTypeRegistry.playC2S().register(Payloads.TrackListReq.TYPE, Payloads.TrackListReq.CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.StartTracking.TYPE, Payloads.StartTracking.CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.StopTracking.TYPE, Payloads.StopTracking.CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.SyncSettings.TYPE, Payloads.SyncSettings.CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.TeleportTo.TYPE, Payloads.TeleportTo.CODEC);
    }
}
