package com.glowtrack.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 服务端安全发包：对端未安装本模组（optional 连接）时，向未协商通道发包会抛异常。
 * 统一在此捕获并静默跳过，保证不带 GlowTrack 的客户端也能正常进服。
 */
public final class ServerNet {
    private ServerNet() {}

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player == null || player.connection == null) return;
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (Throwable ignored) {
            // 对端没有该通道（未安装 GlowTrack / 旧版本不含此包）——静默跳过
        }
    }
}
