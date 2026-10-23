package com.glowtrack.network;

import com.glowtrack.client.ClientTrackState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * 客户端处理全部 S-&gt;C 包（Fabric 版，均在主线程执行）。
 *
 * <p>处理逻辑与 NeoForge 母版逐条对应，入口换成了 Fabric 的通道接收器。
 * 1.19.2 的接收器回调跑在网络线程，所以统一包一层
 * {@code mc.execute(...)} 切回主线程（避免在网络线程里改客户端状态）。</p>
 */
public final class ClientPayloads {

    private ClientPayloads() {}

    /** 注册全部 S-&gt;C 接收器。 */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Payloads.HELLO_ID,
                (client, handler, buf, sender) -> {
                    Payloads.Hello msg = Payloads.Hello.decode(buf);
                    handleHello(msg);
                });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.TRACK_LIST_ID,
                (client, handler, buf, sender) -> {
                    Payloads.TrackList msg = Payloads.TrackList.decode(buf);
                    handleTrackList(msg);
                });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.TRACK_SNAPSHOT_ID,
                (client, handler, buf, sender) -> {
                    Payloads.TrackData msg = Payloads.TrackData.decode(buf);
                    handleTrackData(msg);
                });
        ClientPlayNetworking.registerGlobalReceiver(ActionDataPackets.ACTION_DATA_ID,
                (client, handler, buf, sender) -> {
                    ActionDataPackets.ActionData msg = ActionDataPackets.ActionData.decode(buf);
                    handleActionData(msg);
                });
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
