package com.glowtrack.network;

import com.glowtrack.track.ActionEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 行为事件数据包：服务端把白名单玩家的方块操作/交互事件推给观察者客户端。
 *
 * <p>与 {@link Payloads.TrackData} 同语义（uuid=被追踪玩家，actions=增量事件列表），
 * 单独一个包避免与轨迹点包体积互相挤压。</p>
 */
public final class ActionDataPackets {
    private ActionDataPackets() {}

    public static final Identifier ACTION_DATA_ID =
            Identifier.fromNamespaceAndPath(com.glowtrack.fabric.GlowTrackFabric.MOD_ID, "action_data");

    /** 单条事件的网络表示（全字符串/基本类型，编解码最简单）。 */
    public record NetAction(long t, String type, String target, double x, double y, double z) {}

    public record ActionData(UUID player, List<NetAction> actions) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ActionData> TYPE =
                new CustomPacketPayload.Type<>(ACTION_DATA_ID);
        public static final StreamCodec<FriendlyByteBuf, ActionData> CODEC = new StreamCodec<>() {
            @Override
            public ActionData decode(FriendlyByteBuf buf) {
                UUID player = buf.readUUID();
                // 长度钳制：恶意客户端可以填一个巨大的 varint，
                // 直接 new ArrayList<>(n) 会 OOM（见 Payloads.clampCount 注释）
                int n = Payloads.clampCount(buf.readVarInt(), 32768);
                List<NetAction> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new NetAction(buf.readLong(), buf.readUtf(64), buf.readUtf(256),
                            buf.readDouble(), buf.readDouble(), buf.readDouble()));
                }
                return new ActionData(player, list);
            }

            @Override
            public void encode(FriendlyByteBuf buf, ActionData p) {
                buf.writeUUID(p.player());
                buf.writeVarInt(p.actions().size());
                for (NetAction a : p.actions()) {
                    buf.writeLong(a.t());
                    buf.writeUtf(a.type(), 64);
                    buf.writeUtf(a.target(), 256);
                    buf.writeDouble(a.x());
                    buf.writeDouble(a.y());
                    buf.writeDouble(a.z());
                }
            }
        };
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
