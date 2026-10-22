package com.glowtrack.network;

import com.glowtrack.client.ClientTrackState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * 客户端处理全部 S-&gt;C 包（Fabric 版，均在主线程执行）。
 *
 * <p>处理逻辑与 NeoForge 母版逐条对应，只是入口从 {@code IPayloadContext}
 * 换成了 Fabric 的全局接收器。Fabric 的接收器回调已经在主线程，
 * 但这里仍然包一层 {@code mc.execute(...)}，与母版保持一致的时序语义
 * （避免在网络线程里改客户端状态）。</p>
 */
public final class ClientPayloads {

    private ClientPayloads() {}

    /** 注册全部 S-&gt;C 接收器。 */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Hello.TYPE,
                (payload, context) -> handleHello(payload));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.TrackList.TYPE,
                (payload, context) -> handleTrackList(payload));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.TrackData.TYPE,
                (payload, context) -> handleTrackData(payload));
        ClientPlayNetworking.registerGlobalReceiver(ActionDataPackets.ActionData.TYPE,
                (payload, context) -> handleActionData(payload));
    }

    public static void handleHello(Payloads.Hello msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ClientTrackState.get().onServerHello(msg.protocol()));
    }

    public static void handleTrackList(Payloads.TrackList msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ClientTrackState.get().onTrackList(msg.players()));
    }

    public static void handleTrackData(Payloads.TrackData msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ClientTrackState.get().onTrackData(msg));
    }

    public static void handleActionData(ActionDataPackets.ActionData msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ClientTrackState.get().onActionData(msg.player(), msg.actions()));
    }
}
