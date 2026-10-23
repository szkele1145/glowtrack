package com.glowtrack.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端安全发包（Forge 版）。
 *
 * <p>对端没装本模组时，向未协商的通道发包会抛异常（SimpleChannel 的
 * {@code send} 在连接未就绪时抛 {@code IllegalArgumentException}）。
 * 统一在这里捕获并静默跳过，保证不带 GlowTrack 的客户端也能正常进服。</p>
 */
public final class ServerNet {

    private ServerNet() {}

    public static void send(ServerPlayer player, Packet payload) {
        if (player == null || player.connection == null || payload == null) return;
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            payload.write(buf);
            // PacketDistributor.PLAYER：只发给这一个玩家。
            // 不用 SimpleChannel#sendTo —— 它要 NetworkManager，跨版本签名不稳定。
            NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new Envelope(payload.id(), buf));
        } catch (Throwable ignored) {
            // 对端没有该通道（未安装 GlowTrack / 版本不兼容）——静默跳过
        }
    }
}
