package com.glowtrack.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Forge 的 SimpleChannel 只能注册有限几种消息类型，而本模组有十来个逻辑包。
 *
 * <p>所以线上只有一种信封：{@code [通道 id][长度][负载字节]}。收端按 id 查表，
 * 交给对应包的 {@code decode} 解析，再分发到处理器。逻辑包本身仍是
 * {@link Packet} 的 record（见 {@link Payloads} / {@link ActionDataPackets}）。</p>
 *
 * <p><b>长度上限</b>：解码时对声明长度做钳制（1 MiB）。轨迹快照最大也就几百 KB；
 * 不钳制的话一个恶意包声明 2 GiB 就能让接收端直接 OOM。</p>
 */
public final class Envelope {

    /** 单包负载上限（字节）。轨迹快照按 65536 点 × 41 字节 ≈ 2.6 MB，留足余量。 */
    static final int MAX_PAYLOAD = 4 * 1024 * 1024;

    private final ResourceLocation id;
    private final FriendlyByteBuf data;

    public Envelope(ResourceLocation id, FriendlyByteBuf data) {
        this.id = id;
        this.data = data;
    }

    public ResourceLocation id() { return id; }

    public FriendlyByteBuf data() { return data; }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeVarInt(data.readableBytes());
        buf.writeBytes(data);
    }

    public static Envelope decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        int n = buf.readVarInt();
        if (n < 0) n = 0;
        if (n > MAX_PAYLOAD) n = MAX_PAYLOAD;
        // 只读声明长度与实际可读长度的较小值，避免读到相邻包的数据
        int len = Math.min(n, buf.readableBytes());
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new Envelope(id, new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));
    }
}
