package com.glowtrack.network;

import com.glowtrack.client.ClientTrackState;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端处理全部 S->C 包（均在主线程执行）。 */
public final class ClientPayloads {
    private ClientPayloads() {}

    public static void handleHello(Payloads.Hello msg, IPayloadContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ClientTrackState.get().onServerHello(msg.protocol()));
    }

    public static void handleTrackList(Payloads.TrackList msg, IPayloadContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ClientTrackState.get().onTrackList(msg.players()));
    }

    public static void handleTrackData(Payloads.TrackData msg, IPayloadContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ClientTrackState.get().onTrackData(msg));
    }
}
