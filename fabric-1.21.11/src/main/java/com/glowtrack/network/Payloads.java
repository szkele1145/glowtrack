package com.glowtrack.network;

import com.glowtrack.fabric.GlowTrackFabric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 全部网络包定义。每个包是一个 record + StreamCodec。
 *
 * <p>方向在 {@link NetworkHandler#register} 中统一注册。</p>
 */
public final class Payloads {
    private Payloads() {}

    public static final Identifier HELLO_ID = id("hello");
    public static final Identifier TRACK_LIST_REQ_ID = id("track_list_req");
    public static final Identifier TRACK_LIST_ID = id("track_list");
    public static final Identifier START_TRACKING_ID = id("start_tracking");
    public static final Identifier STOP_TRACKING_ID = id("stop_tracking");
    public static final Identifier TRACK_SNAPSHOT_ID = id("track_snapshot");
    public static final Identifier SYNC_SETTINGS_ID = id("sync_settings");
    public static final Identifier TELEPORT_TO_ID = id("teleport_to");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GlowTrackFabric.MOD_ID, path);
    }

    /**
     * 反序列化列表长度钳制。
     *
     * <p>长度字段是客户端/网络可随意填的 varint；若直接 {@code new ArrayList<>(n)}，
     * 一个 {@code n=0x7FFFFFFF} 的恶意包就能让解码端分配 17GB 的引用数组
     * （OOM 崩服/崩端）——根本不需要包体里真有那么多数据。</p>
     *
     * <p>钳制到 max 后，即使攻击者声称 n 很大，也只会分配 max 大小的列表；
     * 循环在 buf 真实数据耗尽时自然抛 DecoderException 断连。</p>
     */
    public static int clampCount(int n, int max) {
        if (n < 0) return 0;
        return Math.min(n, max);
    }

    /** 手写 UUID 编解码（ByteBufCodecs 无 UUID 常量）。 */
    private static final StreamCodec<FriendlyByteBuf, UUID> UUID_CODEC = new StreamCodec<>() {
        @Override public UUID decode(FriendlyByteBuf buf) { return buf.readUUID(); }
        @Override public void encode(FriendlyByteBuf buf, UUID id) { buf.writeUUID(id); }
    };

    // ==================== S->C ====================

    /** 握手：服务端装有本模组时在进服时下发，客户端据此判定服务端模式。 */
    public record Hello(int protocol) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Hello> TYPE = new CustomPacketPayload.Type<>(HELLO_ID);
        public static final StreamCodec<FriendlyByteBuf, Hello> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Hello::protocol, Hello::new);
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 白名单玩家条目。 */
    public record PlayerEntry(UUID id, String name, boolean online) {}

    /** 白名单玩家列表。 */
    public record TrackList(List<PlayerEntry> players) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TrackList> TYPE = new CustomPacketPayload.Type<>(TRACK_LIST_ID);
        public static final StreamCodec<FriendlyByteBuf, TrackList> CODEC = new StreamCodec<>() {
            @Override public TrackList decode(FriendlyByteBuf buf) {
                int n = clampCount(buf.readVarInt(), 4096);
                List<PlayerEntry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++)
                    list.add(new PlayerEntry(buf.readUUID(), buf.readUtf(32767), buf.readBoolean()));
                return new TrackList(list);
            }
            @Override public void encode(FriendlyByteBuf buf, TrackList p) {
                buf.writeVarInt(p.players.size());
                for (PlayerEntry e : p.players) {
                    buf.writeUUID(e.id()); buf.writeUtf(e.name()); buf.writeBoolean(e.online());
                }
            }
        };
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 轨迹点网络表示（维度用字符串，避免 Identifier codec 冗长）。 */
    public record NetPoint(long t, double x, double y, double z, String dim, float v, float yaw, float pitch) {}

    /** 轨迹快照/增量。isSnapshot=true 表示全量历史，false 表示增量追加。 */
    public record TrackData(UUID player, boolean isSnapshot, List<NetPoint> points)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TrackData> TYPE = new CustomPacketPayload.Type<>(TRACK_SNAPSHOT_ID);
        public static final StreamCodec<FriendlyByteBuf, TrackData> CODEC = new StreamCodec<>() {
            @Override public TrackData decode(FriendlyByteBuf buf) {
                UUID id = buf.readUUID();
                boolean snap = buf.readBoolean();
                int n = clampCount(buf.readVarInt(), 65536);
                List<NetPoint> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++)
                    list.add(new NetPoint(buf.readVarLong(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readUtf(256), buf.readFloat(),
                            buf.readFloat(), buf.readFloat()));
                return new TrackData(id, snap, list);
            }
            @Override public void encode(FriendlyByteBuf buf, TrackData p) {
                buf.writeUUID(p.player()); buf.writeBoolean(p.isSnapshot());
                buf.writeVarInt(p.points.size());
                for (NetPoint np : p.points) {
                    buf.writeVarLong(np.t()); buf.writeDouble(np.x()); buf.writeDouble(np.y());
                    buf.writeDouble(np.z()); buf.writeUtf(np.dim()); buf.writeFloat(np.v());
                    buf.writeFloat(np.yaw()); buf.writeFloat(np.pitch());
                }
            }
        };
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ==================== C->S ====================

    /** 请求白名单列表。 */
    public record TrackListReq() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TrackListReq> TYPE = new CustomPacketPayload.Type<>(TRACK_LIST_REQ_ID);
        public static final StreamCodec<FriendlyByteBuf, TrackListReq> CODEC = StreamCodec.unit(new TrackListReq());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 开始追踪某玩家。 */
    public record StartTracking(UUID player, int days) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StartTracking> TYPE = new CustomPacketPayload.Type<>(START_TRACKING_ID);
        public static final StreamCodec<FriendlyByteBuf, StartTracking> CODEC = StreamCodec.composite(
                UUID_CODEC, StartTracking::player,
                ByteBufCodecs.VAR_INT, StartTracking::days, StartTracking::new);
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 停止追踪。 */
    public record StopTracking(UUID player) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StopTracking> TYPE = new CustomPacketPayload.Type<>(STOP_TRACKING_ID);
        public static final StreamCodec<FriendlyByteBuf, StopTracking> CODEC = StreamCodec.composite(
                UUID_CODEC, StopTracking::player, StopTracking::new);
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 同步采样/保留设置（OP 在 GUI 内调）。 */
    public record SyncSettings(int sampleIntervalTicks, double minMoveDistance,
                               double turnAngle, int retentionDays) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncSettings> TYPE = new CustomPacketPayload.Type<>(SYNC_SETTINGS_ID);
        public static final StreamCodec<FriendlyByteBuf, SyncSettings> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SyncSettings::sampleIntervalTicks,
                ByteBufCodecs.DOUBLE, SyncSettings::minMoveDistance,
                ByteBufCodecs.DOUBLE, SyncSettings::turnAngle,
                ByteBufCodecs.VAR_INT, SyncSettings::retentionDays, SyncSettings::new);
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 请求传送到某玩家在某时刻的位置。 */
    public record TeleportTo(UUID player, long timeMillis) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TeleportTo> TYPE = new CustomPacketPayload.Type<>(TELEPORT_TO_ID);
        public static final StreamCodec<FriendlyByteBuf, TeleportTo> CODEC = StreamCodec.composite(
                UUID_CODEC, TeleportTo::player,
                ByteBufCodecs.VAR_LONG, TeleportTo::timeMillis, TeleportTo::new);
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
