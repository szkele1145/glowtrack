package com.glowtrack.network;

import com.glowtrack.client.ClientTrackState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端处理全部 S-&gt;C 包（均在主线程执行）。
 *
 * <p>线上只有 {@link Envelope}，这里按逻辑包 id 分派回具体的 record。</p>
 */
public final class ClientPayloads {

    private ClientPayloads() {}

    /** 收到一个信封：解析出具体包并处理。异常静默（对端可能发来未知 id）。 */
    public static void dispatch(Envelope env) {
        ResourceLocation id = env.id();
        try {
            if (Payloads.HELLO_ID.equals(id)) {
                handleHello(Payloads.Hello.decode(env.data()));
            } else if (Payloads.TRACK_LIST_ID.equals(id)) {
                handleTrackList(Payloads.TrackList.decode(env.data()));
            } else if (Payloads.TRACK_SNAPSHOT_ID.equals(id)) {
                handleTrackData(Payloads.TrackData.decode(env.data()));
            } else if (ActionDataPackets.ACTION_DATA_ID.equals(id)) {
                handleActionData(ActionDataPackets.ActionData.decode(env.data()));
            }
        } catch (Throwable ignored) {
            // 未知 id / 负载损坏：丢弃这一帧，不影响其它包
        }
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
