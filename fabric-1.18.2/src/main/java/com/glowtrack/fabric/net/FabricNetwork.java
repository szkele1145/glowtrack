package com.glowtrack.fabric.net;

/**
 * Fabric 网络包注册（1.18.2）。
 *
 * <p>1.18.2 还没有 {@code PayloadTypeRegistry}（1.20.5+ 才有）。1.19.x 的
 * {@code FabricPacket} + {@code PacketType} 体系在注册接收器时就把通道
 * {@code ResourceLocation} 带上了，所以这里不需要额外登记类型，
 * 只要在 {@code ServerPayloads} / {@code ClientPayloads} 里
 * {@code registerGlobalReceiver} 即可。</p>
 *
 * <p>保留这个空壳类，是为了让入口类的调用顺序与其它平台保持一致
 * （先注册网络，再注册服务端事件）。</p>
 */
public final class FabricNetwork {

    private FabricNetwork() {}

    /** 1.18.2 无需预注册包类型（1.20.5+ 才需要 PayloadTypeRegistry）。 */
    public static void register() {
        // no-op
    }
}
