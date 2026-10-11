package com.glowtrack.server;

import com.glowtrack.GlowTrack;
import com.glowtrack.TrackConfig;
import com.glowtrack.network.Payloads;
import com.glowtrack.track.PlayerTrack;
import com.glowtrack.track.Sampler;
import com.glowtrack.track.TrackPoint;
import com.glowtrack.track.TrackStore;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端轨迹管理器：单例。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>服务端 tick 对所有白名单在线玩家采样</li>
 *   <li>维护"谁在追踪谁"的订阅关系，被追踪玩家移动时增量推送</li>
 *   <li>响应开始/停止追踪、下发快照、历史传送</li>
 *   <li>定期落盘与保留清理</li>
 * </ul>
 */
@EventBusSubscriber(modid = GlowTrack.MOD_ID)
public final class ServerTrackManager {
    private static ServerTrackManager instance;

    private final TrackStore store;
    private final Map<UUID, Sampler> samplers = new ConcurrentHashMap<>();
    /** 观察者 UUID -> 正在追踪的目标 UUID 集合 */
    private final Map<UUID, Set<UUID>> watchers = new ConcurrentHashMap<>();
    private int saveCooldown = 0;

    private ServerTrackManager(MinecraftServer server) {
        Path root = server.getServerDirectory().resolve("glowtrack");
        this.store = new TrackStore(root);
    }

    public static ServerTrackManager get() {
        return instance;
    }

    public static void initIfNeeded(MinecraftServer server) {
        if (instance == null) instance = new ServerTrackManager(server);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        initIfNeeded(event.getServer());
        int removed = instance.store.enforceRetention(TrackConfig.RETENTION_DAYS.get());
        GlowTrack.LOGGER.info("[GlowTrack] 服务端轨迹存储就绪，清理过期文件 {} 个", removed);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (instance != null) {
            instance.store.saveAll();
            GlowTrack.LOGGER.info("[GlowTrack] 服务端轨迹已全部落盘");
        }
        instance = null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (instance == null) return;
        instance.tick(event.getServer());
    }

    private void tick(MinecraftServer server) {
        Set<String> whitelist = whitelistNames(server);
        long tick = server.getTickCount();
        long now = System.currentTimeMillis();

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            UUID id = sp.getUUID();
            String name = sp.getName().getString();
            if (!whitelist.contains(name)) continue;

            Sampler sampler = samplers.computeIfAbsent(id, k -> new Sampler());
            ResourceLocation dim = sp.level().dimension().location();
            double x = sp.getX(), y = sp.getY(), z = sp.getZ();
            if (!sampler.shouldSample(tick, now, x, y, z, dim.toString())) continue;

            float speed = (float) sp.getDeltaMovement().horizontalDistance();
            TrackPoint p = new TrackPoint(now, x, y, z, dim, speed, sp.getYRot(), sp.getXRot());
            store.append(id, name, p);

            // 若正被他人追踪，增量推送给所有观察者
            for (Map.Entry<UUID, Set<UUID>> e : watchers.entrySet()) {
                if (e.getValue().contains(id)) {
                    ServerPlayer watcher = server.getPlayerList().getPlayer(e.getKey());
                    if (watcher != null) {
                        PacketDistributor.sendToPlayer(watcher, new Payloads.TrackData(
                                id, false, List.of(net(id, p))));
                    }
                }
            }
        }

        // 周期落盘
        if (++saveCooldown >= 6000) { // 约 5 分钟
            saveCooldown = 0;
            store.saveAll();
        }
    }

    /** 读取白名单玩家名集合。 */
    private Set<String> whitelistNames(MinecraftServer server) {
        Set<String> set = new HashSet<>();
        try {
            String[] names = server.getPlayerList().getWhiteListNames();
            for (String n : names) set.add(n);
        } catch (Exception e) {
            GlowTrack.LOGGER.warn("[GlowTrack] 读取白名单失败: {}", e.getMessage());
        }
        return set;
    }

    private Payloads.NetPoint net(UUID id, TrackPoint p) {
        return new Payloads.NetPoint(p.timeMillis(), p.x(), p.y(), p.z(),
                p.dimension().toString(), p.speed(), p.yaw(), p.pitch());
    }

    // ==================== 对外接口 ====================

    /** 下发白名单玩家列表（含在线状态与今日轨迹点数）。 */
    public void sendTrackList(ServerPlayer watcher) {
        MinecraftServer server = watcher.getServer();
        Set<String> whitelist = whitelistNames(server);
        Map<String, UUID> onlineIds = new HashMap<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers())
            onlineIds.put(sp.getName().getString(), sp.getUUID());

        List<Payloads.PlayerEntry> entries = new java.util.ArrayList<>();
        for (String name : whitelist) {
            UUID id = onlineIds.get(name);
            boolean online = id != null;
            if (!online) id = legacyId(name);
            entries.add(new Payloads.PlayerEntry(id, name, online));
        }
        PacketDistributor.sendToPlayer(watcher, new Payloads.TrackList(entries));
    }

    /** 离线白名单玩家用 OfflinePlayer:name 推导 UUID。 */
    private UUID legacyId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 开始追踪：加载历史快照 + 加入订阅。 */
    public void startTracking(ServerPlayer watcher, UUID target, int days) {
        int d = Math.max(1, Math.min(days, TrackConfig.RETENTION_DAYS.get()));
        List<TrackPoint> history = store.loadRange(target, "", d);
        List<Payloads.NetPoint> net = new java.util.ArrayList<>();
        for (TrackPoint p : history) net.add(net(target, p));
        PacketDistributor.sendToPlayer(watcher, new Payloads.TrackData(target, true, net));
        watchers.computeIfAbsent(watcher.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(target);
        GlowTrack.LOGGER.info("[GlowTrack] {} 开始追踪 {}（{} 点）", watcher.getName().getString(), target, net.size());
    }

    public void stopTracking(ServerPlayer watcher, UUID target) {
        Set<UUID> set = watchers.get(watcher.getUUID());
        if (set != null) set.remove(target);
    }

    /** 玩家退出时清理其订阅关系。 */
    public void removeWatcher(UUID watcher) {
        watchers.remove(watcher);
    }

    /** 历史传送：把观察者送到目标玩家在某时刻的位置（含跨维度）。 */
    public void teleportTo(ServerPlayer watcher, UUID target, long timeMillis) {
        List<TrackPoint> history = store.loadRange(target, "", TrackConfig.RETENTION_DAYS.get());
        if (history.isEmpty()) {
            watcher.displayClientMessage(Component.literal("[GlowTrack] 该玩家暂无轨迹数据"), false);
            return;
        }
        TrackPoint nearest = nearest(history, timeMillis);
        ServerLevel level = watcher.getServer().getLevel(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, nearest.dimension()));
        if (level == null) level = watcher.serverLevel();
        watcher.teleportTo(level, nearest.x(), nearest.y(), nearest.z(), nearest.yaw(), nearest.pitch());
        watcher.displayClientMessage(Component.literal(
                "[GlowTrack] 已传送至 " + fmt(nearest.timeMillis()) + " 的位置 [" +
                String.format("%.1f, %.1f, %.1f", nearest.x(), nearest.y(), nearest.z()) + "]"), false);
    }

    private TrackPoint nearest(List<TrackPoint> pts, long t) {
        TrackPoint best = pts.get(0);
        long bd = Math.abs(best.timeMillis() - t);
        for (TrackPoint p : pts) {
            long d = Math.abs(p.timeMillis() - t);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    private String fmt(long ms) {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(ms));
    }
}
