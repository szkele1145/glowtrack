package com.glowtrack.network;

import com.glowtrack.GlowTrack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 统一注册网络通道。
 *
 * <p>Forge 1.20.1 用的是 {@link SimpleChannel}：一个通道 + 有限几种消息类型。
 * 本模组的逻辑包有十来个，所以线上只注册 {@link Envelope}（两个方向各一个 id），
 * 由它携带「逻辑包 id + 负载」，收端再按 id 分发。</p>
 *
 * <p><b>为什么接受任意协议版本</b>：{@code newSimpleChannel} 的
 * clientAcceptedVersions / serverAcceptedVersions 都返回 true —— 对端没装本模组
 * 时版本是 {@code ABSENT}，若校验就绪会把玩家踢出（"incompatible mods"）。
 * 允许任意版本后，只有真正发/收包时才需要通道，未装方静默降级。</p>
 */
@Mod.EventBusSubscriber(modid = GlowTrack.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NetworkHandler {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GlowTrack.MOD_ID, "main"),
            () -> PROTOCOL,
            version -> true,
            version -> true);

    private NetworkHandler() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::registerMessages);
    }

    private static void registerMessages() {
        CHANNEL.messageBuilder(Envelope.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Envelope::encode)
                .decoder(Envelope::decode)
                .consumerMainThread((msg, ctx) -> ClientPayloads.dispatch(msg))
                .add();

        CHANNEL.messageBuilder(Envelope.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Envelope::encode)
                .decoder(Envelope::decode)
                .consumerMainThread((msg, ctxSup) -> {
                    // 1.20.1 的 IPacketHandler 第二个参数是 Supplier<NetworkEvent.Context>
                    var ctx = ctxSup.get();
                    var sender = ctx.getSender();
                    if (sender != null) ServerPayloads.dispatch(msg, sender);
                })
                .add();
    }
}
