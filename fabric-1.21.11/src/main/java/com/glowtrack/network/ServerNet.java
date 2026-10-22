package com.glowtrack.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端安全发包（Fabric 版）。
 *
 * <p>对端没装本模组时，向未协商的协议通道发包会抛异常。
 * 统一在这里捕获并静默跳过，保证不带 GlowTrack 的客户端也能正常进服。</p>
 */
public final class ServerNet {

    private ServerNet() {}

    /**
     * 给玩家发一个数据包。
     *
     * <p>Fabric 的 {@code ServerPlayNetworking.send} 在通道未注册时会直接抛
     * {@code IllegalStateException}；这里吞掉它。</p>
     */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player == null || player.connection == null) return;
        try {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        } catch (Throwable ignored) {
            // 对端没有该通道（未装 GlowTrack / 版本不兼容）——静默跳过
        }
    }
}
