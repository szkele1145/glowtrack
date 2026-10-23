package com.glowtrack.network;

import com.glowtrack.fabric.GlowTrackFabric;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 全部网络包定义。每个包实现 {@link FabricPacket}，自带 {@link PacketType}。
 *
 * <p><b>为什么不是 CustomPacketPayload</b>：1.20.5+ 的 Minecraft 才有
 * {@code CustomPacketPayload} + {@code StreamCodec}；1.19.4 走 Fabric API 的
 * {@code FabricPacket} + {@code PacketType}。字段顺序和编解码方式与母版完全一致，
 * 所以同一版本下 NeoForge 客户端连 Fabric 服务端也能互通。</p>
 *
 * <p><b>为什么用静态工厂而不是 record 构造器</b>：Java record 一旦显式声明了
 * 非规范构造器（这里是 {@code (FriendlyByteBuf)}），它必须把 {@code this(...)}
 * 放在第一条语句；把解析逻辑写进去很别扭。改用静态工厂 {@code decode(buf)}，
 * record 只保留规范构造器，逻辑更清晰。</p>
 */
public final class Payloads {
    private Payloads() {}

    public static final ResourceLocation HELLO_ID = id("hello");
    public static final ResourceLocation TRACK_LIST_REQ_ID = id("track_list_req");
    public static final ResourceLocation TRACK_LIST_ID = id("track_list");
    public static final ResourceLocation START_TRACKING_ID = id("start_tracking");
    public static final ResourceLocation STOP_TRACKING_ID = id("stop_tracking");
    public static final ResourceLocation TRACK_SNAPSHOT_ID = id("track_snapshot");
    public static final ResourceLocation SYNC_SETTINGS_ID = id("sync_settings");
    public static final ResourceLocation TELEPORT_TO_ID = id("teleport_to");

    private static ResourceLocation id(String path) {
        return new ResourceLocation(GlowTrackFabric.MOD_ID, path);
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

    // ==================== S->C ====================

    /** 握手：服务端装有本模组时在进服时下发，客户端据此判定服务端模式。 */
    public record Hello(int protocol) implements FabricPacket {
        public static final PacketType<Hello> TYPE =
                PacketType.create(HELLO_ID, Hello::decode);
        public static Hello decode(FriendlyByteBuf buf) {
            return new Hello(buf.readVarInt());
        }
        @Override public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(protocol);
        }
        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** 白名单玩家条目。 */
    public record PlayerEntry(UUID id, String name, boolean online) {}

    /** 白名单玩家列表。 */
    public record TrackList(List<PlayerEntry> players) implements FabricPacket {
        public static final PacketType<TrackList> TYPE =
                PacketType.create(TRACK_LIST_ID, TrackList::decode);
        public static TrackList decode(FriendlyByteBuf buf) {
            int n = clampCount(buf.readVarInt(), 4096);
            List<PlayerEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++)
                list.add(new PlayerEntry(buf.readUUID(), buf.readUtf(32767), buf.readBoolean()));
            return new TrackList(list);
        }
        @Override public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(players.size());
            for (PlayerEntry e : players) {
                buf.writeUUID(e.id()); buf.writeUtf(e.name()); buf.writeBoolean(e.online());
            }
        }
        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** 轨迹点网络表示（维度用字符串，避免 ResourceLocation codec 冗长）。 */
    public record NetPoint(long t, double x, double y, double z, String dim, float v, float yaw, float pitch) {}

    /** 轨迹快照/增量。isSnapshot=true 表示全量历史，false 表示增量追加。 */
    public record TrackData(UUID player, boolean isSnapshot, List<NetPoint> points)
            implements FabricPacket {
        public static final PacketType<TrackData> TYPE =
                PacketType.create(TRACK_SNAPSHOT_ID, TrackData::decode);
        public static TrackData decode(FriendlyByteBuf buf) {
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
        @Override public void write(FriendlyByteBuf buf) {
            buf.writeUUID(player()); buf.writeBoolean(isSnapshot());
            buf.writeVarInt(points.size());
            for (NetPoint np : points) {
                buf.writeVarLong(np.t()); buf.writeDouble(np.x()); buf.writeDouble(np.y());
                buf.writeDouble(np.z()); buf.writeUtf(np.dim()); buf.writeFloat(np.v());
                buf.writeFloat(np.yaw()); buf.writeFloat(np.pitch());
            }
        }
        @Override public PacketType<?> getType() { return TYPE; }
    }

    // ==================== C->S ====================

    /** 请求白名单列表。 */
    public record TrackListReq() implements FabricPacket {
        public static final PacketType<TrackListReq> TYPE =
                PacketType.create(TRACK_LIST_REQ_ID, buf -> new TrackListReq());
        @Override public void write(FriendlyByteBuf buf) { }
        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** 开始追踪某玩家。 */
    public record StartTracking(UUID player, int days) implements FabricPacket {
        public static final PacketType<StartTracking> TYPE =
                PacketType.create(START_TRACKING_ID, StartTracking::decode);
        public static StartTracking decode(FriendlyByteBuf buf) {
            return new StartTracking(buf.readUUID(), buf.readVarInt());
        }
        @Override public void write(FriendlyByteBuf buf) {
            buf.writeUUID(player); buf.writeVarInt(days);
        }
        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** 停止追踪。 */
    public record StopTracking(UUID player) implements FabricPacket {
        public static final PacketType<StopTracking> TYPE =
                PacketType.create(STOP_TRACKING_ID, StopTracking::decode);
        public static StopTracking decode(FriendlyByteBuf buf) {
            return new StopTracking(buf.readUUID());
        }
        @Override public void write(FriendlyByteBuf buf) {
            buf.writeUUID(player);
        }
        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** 同步采样/保留设置（OP 在 GUI 内调）。 */
    public record SyncSettings(int sampleIntervalTicks, double minMoveDistance,
                               double turnAngle, int retentionDays) implements FabricPacket {
        public static final PacketType<SyncSettings> TYPE =
                PacketType.create(SYNC_SETTINGS_ID, SyncSettings::decode);
        public static SyncSettings decode(FriendlyByteBuf buf) {
            return new SyncSettings(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
        }
        @Override public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(sampleIntervalTicks);
            buf.writeDouble(minMoveDistance);
            buf.writeDouble(turnAngle);
            buf.writeVarInt(retentionDays);
        }
        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** 请求传送到某玩家在某时刻的位置。 */
    public record TeleportTo(UUID player, long timeMillis) implements FabricPacket {
        public static final PacketType<TeleportTo> TYPE =
                PacketType.create(TELEPORT_TO_ID, TeleportTo::decode);
        public static TeleportTo decode(FriendlyByteBuf buf) {
            return new TeleportTo(buf.readUUID(), buf.readVarLong());
        }
        @Override public void write(FriendlyByteBuf buf) {
            buf.writeUUID(player); buf.writeVarLong(timeMillis);
        }
        @Override public PacketType<?> getType() { return TYPE; }
    }
}
