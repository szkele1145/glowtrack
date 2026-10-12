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
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.ArrayList;
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
    /** 白名单缓存：原每 tick 重建 Set + 拷贝数组，现 20 秒刷新一次（白名单基本不变）。 */
    private Set<String> whitelistCache = null;
    private long whitelistCacheAt = 0;

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

        // 每个观察者一个攒批列表：本 tick 内多个被追踪玩家的点合并进一个包
        // key = watcher UUID，value = (target UUID -> 点列表)。按目标分组，
        // 因为 TrackData.player 是单值——多目标混在一个包里会被客户端按 id 过滤丢弃。
        Map<UUID, Map<UUID, List<Payloads.NetPoint>>> batch = new java.util.HashMap<>();

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

            // 若正被他人追踪，按 (观察者, 目标) 攒批
            for (Map.Entry<UUID, Set<UUID>> e : watchers.entrySet()) {
                if (e.getValue().contains(id)) {
                    batch.computeIfAbsent(e.getKey(), k -> new java.util.HashMap<>())
                         .computeIfAbsent(id, k -> new ArrayList<>()).add(net(id, p));
                }
            }
        }

        // 每观察者每目标每 tick 至多一个包（观察者同时追多个玩家时各收各的包）
        for (Map.Entry<UUID, Map<UUID, List<Payloads.NetPoint>>> e : batch.entrySet()) {
            ServerPlayer watcher = server.getPlayerList().getPlayer(e.getKey());
            if (watcher == null) continue;
            for (Map.Entry<UUID, List<Payloads.NetPoint>> t : e.getValue().entrySet()) {
                com.glowtrack.network.ServerNet.send(watcher, new Payloads.TrackData(
                        t.getKey(), false, t.getValue()));
            }
        }

        // 周期落盘（异步：磁盘 IO 不阻塞主线程 tick；TrackStore 内部已锁，快照后写盘安全）
        if (++saveCooldown >= 6000) { // 约 5 分钟
            saveCooldown = 0;
            java.util.List<PlayerTrack> snapshot = store.allToday();
            Thread worker = new Thread(() -> {
                for (PlayerTrack t : snapshot) store.save(t.player());
                GlowTrack.LOGGER.debug("[GlowTrack] 异步落盘 {} 个玩家轨迹", snapshot.size());
            }, "GlowTrack-Save");
            worker.setDaemon(true);
            worker.start();
        }
    }

    /** 读取白名单玩家名集合（带 20s 缓存；tick 热路径每秒调用 20 次，重建成本 O(n) 数组拷贝）。 */
    private Set<String> whitelistNames(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (whitelistCache != null && now - whitelistCacheAt < 20_000L) return whitelistCache;
        Set<String> set = new HashSet<>();
        try {
            String[] names = server.getPlayerList().getWhiteListNames();
            for (String n : names) set.add(n);
        } catch (Exception e) {
            GlowTrack.LOGGER.warn("[GlowTrack] 读取白名单失败: {}", e.getMessage());
        }
        whitelistCache = set;
        whitelistCacheAt = now;
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
            if (!online) id = resolveOfflineId(server, name);
            entries.add(new Payloads.PlayerEntry(id, name, online));
        }
        com.glowtrack.network.ServerNet.send(watcher, new Payloads.TrackList(entries));
    }

    /**
     * 解析离线白名单玩家的真实 UUID。
     *
     * <p>轨迹数据是按玩家真实 UUID 记录的。优先从服务器 usercache
     * （{@link GameProfileCache}）解析在线模式下的 Mojang UUID；
     * 缓存未命中时回退到 {@code OfflinePlayer:name} 推导（离线模式服务器）。</p>
     */
    private UUID resolveOfflineId(MinecraftServer server, String name) {
        try {
            var cache = server.getProfileCache();
            if (cache != null) {
                var profile = cache.get(name);
                if (profile.isPresent()) return profile.get().getId();
            }
        } catch (Throwable ignored) { }
        return legacyId(name);
    }

    /** 离线白名单玩家用 OfflinePlayer:name 推导 UUID。 */
    private UUID legacyId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 开始追踪：加载历史快照 + 加入订阅。 */
    public void startTracking(ServerPlayer watcher, UUID target, int days) {
        int d = Math.max(1, Math.min(days, TrackConfig.RETENTION_DAYS.get()));
        List<TrackPoint> history = store.loadRange(target, d);
        List<Payloads.NetPoint> net = new java.util.ArrayList<>();
        for (TrackPoint p : history) net.add(net(target, p));
        com.glowtrack.network.ServerNet.send(watcher, new Payloads.TrackData(target, true, net));
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
        List<TrackPoint> history = store.loadRange(target, TrackConfig.RETENTION_DAYS.get());
        if (history.isEmpty()) {
            watcher.displayClientMessage(Component.translatable("glowtrack.teleport.empty"), false);
            return;
        }
        TrackPoint nearest = com.glowtrack.track.TrackTimes.nearestByTime(history, timeMillis);
        if (nearest == null) { watcher.displayClientMessage(Component.translatable("glowtrack.teleport.empty"), false); return; }
        ServerLevel level = watcher.getServer().getLevel(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, nearest.dimension()));
        if (level == null) level = watcher.serverLevel();
        watcher.teleportTo(level, nearest.x(), nearest.y(), nearest.z(), nearest.yaw(), nearest.pitch());
        watcher.displayClientMessage(Component.translatable("glowtrack.teleport.done",
                com.glowtrack.track.TrackTimes.hms(nearest.timeMillis()),
                String.format("%.1f, %.1f, %.1f", nearest.x(), nearest.y(), nearest.z())), false);
    }



}
