package com.glowtrack.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 本模组自定义包的统一接口（1.19.2 专用）。
 *
 * <p><b>为什么不用 {@code FabricPacket}</b>：{@code FabricPacket} + {@code PacketType}
 * 依赖 Minecraft 1.19.3 才引入的「自定义 payload」机制；Fabric API 0.76.0+1.19.2
 * 里的网络 API 仍是老的 {@code ResourceLocation} 通道 + {@code FriendlyByteBuf} 直传。
 * 所以这里只保留两个能力：通道 id 和写入 buf。</p>
 *
 * <p>解码走各 record 的静态 {@code decode(FriendlyByteBuf)}，编码走 {@link #write}，
 * 字段顺序与 1.19.4 / NeoForge 版完全一致 —— 同版本下跨加载器互联不会错位。</p>
 */
public interface Packet {

    /** 通道 id（{@code glowtrack:xxx}）。 */
    ResourceLocation id();

    /** 把自身字段按固定顺序写入 buf。 */
    void write(FriendlyByteBuf buf);
}
