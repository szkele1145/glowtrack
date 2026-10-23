package com.glowtrack.server;

import com.glowtrack.fabric.GlowTrackFabric;

import com.glowtrack.TrackConfig;
import com.glowtrack.network.Payloads;
import com.glowtrack.track.ActionEvent;
import com.glowtrack.track.PlayerTrack;
import com.glowtrack.track.Sampler;
import com.glowtrack.track.TrackPoint;
import com.glowtrack.track.TrackStore;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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
 * 服务端轨迹管理器（单例）。
 *
 * <p>职责：
 * <ul>
 *   <li>每个 tick 对所有白名单在线玩家采样</li>
 *   <li>维护"正在追踪谁"的订阅关系，被追踪玩家移动时增量推送</li>
 *   <li>响应开始/停止追踪、发送快照、历史传送</li>
 *   <li>定期落盘与保留清理</li>
 * </ul>
 *
 * <p>与 NeoForge 母版的差异只有两处：
 * <ol>
 *   <li>母版用 {@code @EventBusSubscriber} + {@code ServerTickEvent} 收 tick，
 *       这里改成由 {@link FabricServerEvents} 显式调用 {@link #tick(MinecraftServer)}。</li>
 *   <li>母版的 {@code ResourceLocation} 在 1.21.9+ 被 Mojang 改名为 {@code ResourceLocation}。</li>
 * </ol>
 */
public final class ServerTrackManager {

    private static ServerTrackManager instance;

    private final TrackStore store;
    private final MinecraftServer minecraftServer;
    private final Map<UUID, Sampler> samplers = new ConcurrentHashMap<>();
    /** 观察者 UUID -> 正在追踪的目标 UUID 集合。 */
    private final Map<UUID, Set<UUID>> watchers = new ConcurrentHashMap<>();
    private int saveCooldown = 0;
    /** 行为事件缓冲：playerId -> 事件列表（主线程事件写入，攒批推送后清空）。 */
    private final Map<UUID, List<ActionEvent>> actionBuffer = new ConcurrentHashMap<>();
    /** 白名单缓存：20 秒刷新一次，避免每 tick 读磁盘。 */
    private Set<String> whitelistCache = null;
    private long whitelistCacheAt = 0;

    private ServerTrackManager(MinecraftServer server) {
        Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("glowtrack");
        this.store = new TrackStore(root);
        this.minecraftServer = server;
    }

    public static ServerTrackManager get() {
        return instance;
    }

    public static void initIfNeeded(MinecraftServer server) {
        if (instance == null) instance = new ServerTrackManager(server);
    }

    /** 服务端启动完成：初始化存储并做一次保留期清理。 */
    public static void onServerStarted(MinecraftServer server) {
        initIfNeeded(server);
        int removed = instance.store.enforceRetention(TrackConfig.retentionDays());
        com.glowtrack.fabric.GlowTrackFabric.LOGGER.info(
                "[GlowTrack] track storage ready, cleaned {} expired files", removed);
    }

    /** 服务端停止：全部落盘。 */
    public static void onServerStopping() {
        if (instance != null) {
            instance.store.saveAll();
            com.glowtrack.fabric.GlowTrackFabric.LOGGER.info("[GlowTrack] 服务端轨迹已全部落盘");
        }
        instance = null;
    }

    /** 每 tick 调用（由 FabricServerEvents 挂到 ServerTickEvents.END_SERVER_TICK）。 */
    public static void onServerTick(MinecraftServer server) {
        if (instance == null) return;
        instance.tick(server);
    }

    private void tick(MinecraftServer server) {
        Set<String> whitelist = whitelistNames(server);
        long tick = server.getTickCount();
        long now = System.currentTimeMillis();

        // 每个观察者一个攒批列表：每 tick 内多个被追踪玩家的点合并进一个包
        // key = watcher UUID，value = (target UUID -> 点列表)。按目标分组。
        // 因为 TrackData.player 是单值，多目标混在同一个包里会被客户端按 id 过滤丢弃。
        Map<UUID, Map<UUID, List<Payloads.NetPoint>>> batch = new HashMap<>();

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            UUID id = sp.getUUID();
            String name = sp.getName().getString();
            if (!whitelist.contains(name)) continue;

            Sampler sampler = samplers.computeIfAbsent(id, k -> new Sampler());
            ResourceLocation dim = sp.getLevel().dimension().location();
            double x = sp.getX(), y = sp.getY(), z = sp.getZ();
            if (!sampler.shouldSample(tick, now, x, y, z, dim.toString())) continue;

            float speed = (float) sp.getDeltaMovement().horizontalDistance();
            TrackPoint p = new TrackPoint(now, x, y, z, dim, speed, sp.getYRot(), sp.getXRot());
            store.append(id, name, p);

            // 若正被他人追踪，按 (观察者 -> 目标) 攒批
            for (Map.Entry<UUID, Set<UUID>> e : watchers.entrySet()) {
                if (e.getValue().contains(id)) {
                    batch.computeIfAbsent(e.getKey(), k -> new HashMap<>())
                         .computeIfAbsent(id, k -> new ArrayList<>()).add(net(id, p));
                }
            }
        }

        // 推送增量：每个观察者一个包，遍历其所有目标
        for (Map.Entry<UUID, Map<UUID, List<Payloads.NetPoint>>> e : batch.entrySet()) {
            ServerPlayer watcher = server.getPlayerList().getPlayer(e.getKey());
            if (watcher == null) continue;
            for (Map.Entry<UUID, List<Payloads.NetPoint>> t : e.getValue().entrySet()) {
                com.glowtrack.network.ServerNet.send(watcher, new Payloads.TrackData(
                        t.getKey(), false, t.getValue()));
                // 行为事件随轨迹增量一起推送（对端没装时静默跳过）
                var acts = drainActions(t.getKey());
                if (!acts.isEmpty()) {
                    var netActs = new ArrayList<com.glowtrack.network.ActionDataPackets.NetAction>();
                    for (var ae : acts) netActs.add(new com.glowtrack.network.ActionDataPackets.NetAction(
                            ae.t(), ae.type(), ae.target(), ae.x(), ae.y(), ae.z()));
                    com.glowtrack.network.ServerNet.send(watcher,
                            new com.glowtrack.network.ActionDataPackets.ActionData(t.getKey(), netActs));
                }
            }
        }

        // 定期落盘：每 5 分钟一次，异步线程做 IO（TrackStore 逐玩家写文件，放主线程会卡）
        if (++saveCooldown >= 6000) {
            saveCooldown = 0;
            List<PlayerTrack> snapshot = store.allToday();
            Thread worker = new Thread(() -> {
                for (PlayerTrack t : snapshot) store.save(t.player());
                com.glowtrack.fabric.GlowTrackFabric.LOGGER.debug(
                        "[GlowTrack] async save {} player tracks", snapshot.size());
            }, "GlowTrack-Save");
            worker.setDaemon(true);
            worker.start();
        }
    }

    /** 读取白名单（缓存 20s，避免每 tick 读磁盘）。 */
    private Set<String> whitelistNames(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (whitelistCache != null && now - whitelistCacheAt < 20_000L) return whitelistCache;
        Set<String> set = new HashSet<>();
        try {
            String[] names = server.getPlayerList().getWhiteListNames();
            for (String n : names) set.add(n);
        } catch (Exception e) {
            com.glowtrack.fabric.GlowTrackFabric.LOGGER.warn("[GlowTrack] 读取白名单失败: {}", e.getMessage());
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

    /** 下发白名单玩家列表（含在线状态）。 */
    public void sendTrackList(ServerPlayer watcher) {
        MinecraftServer server = watcher.getLevel().getServer();
        Set<String> whitelist = whitelistNames(server);
        Map<String, UUID> onlineIds = new HashMap<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers())
            onlineIds.put(sp.getName().getString(), sp.getUUID());

        List<Payloads.PlayerEntry> entries = new ArrayList<>();
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
     * <p>轨迹数据是按玩家真实 UUID 记录的，优先从服务端的用户名→UUID 缓存
     * （1.21.9+ 是 {@code Services.nameToIdCache()}；更早的版本是
     * {@code GameProfileCache}）里查；查不到再退回离线 UUID 规则
     * （{@code OfflinePlayer:name} 派生的离线 UUID）。</p>
     */
    private UUID resolveOfflineId(MinecraftServer server, String name) {
        try {
            // 1.21.8 仍有 GameProfileCache（MinecraftServer.getProfileCache()）。
            var cache = server.getProfileCache();
            if (cache != null) {
                var found = cache.get(name);
                if (found.isPresent()) return found.get().getId();
            }
        } catch (Throwable ignored) { }
        return legacyId(name);
    }

    /** 离线玩家的兜底 UUID（OfflinePlayer:name 派生的离线 UUID）。 */
    private UUID legacyId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 开始追踪：下发历史快照并登记订阅关系。 */
    public void startTracking(ServerPlayer watcher, UUID target, int days) {
        int d = Math.max(1, Math.min(days, TrackConfig.retentionDays()));
        List<TrackPoint> history = store.loadRange(target, d);
        List<Payloads.NetPoint> net = new ArrayList<>();
        for (TrackPoint p : history) net.add(net(target, p));
        com.glowtrack.network.ServerNet.send(watcher, new Payloads.TrackData(target, true, net));
        // 快照附带当日历史行为事件（回放可见他今天干过什么）
        var hist = store.loadActions(target, TrackStore.todayKey());
        if (!hist.isEmpty()) {
            var netActs = new ArrayList<com.glowtrack.network.ActionDataPackets.NetAction>();
            for (var ae : hist) netActs.add(new com.glowtrack.network.ActionDataPackets.NetAction(
                    ae.t(), ae.type(), ae.target(), ae.x(), ae.y(), ae.z()));
            com.glowtrack.network.ServerNet.send(watcher,
                    new com.glowtrack.network.ActionDataPackets.ActionData(target, netActs));
        }
        watchers.computeIfAbsent(watcher.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(target);
        com.glowtrack.fabric.GlowTrackFabric.LOGGER.info(
                "[GlowTrack] {} started tracking {} ({} points)",
                watcher.getName().getString(), target, net.size());
    }

    public void stopTracking(ServerPlayer watcher, UUID target) {
        Set<UUID> set = watchers.get(watcher.getUUID());
        if (set != null) set.remove(target);
    }

    /** 玩家退出时清理其订阅关系与采样器（该玩家可能同时是观察者和被追踪者）。 */
    public void removeWatcher(UUID watcher) {
        watchers.remove(watcher);
        samplers.remove(watcher);
    }

    /** 玩家是否在白名单里（在白名单才会被采样）。 */
    public boolean isTracked(String name) {
        return whitelistNames(minecraftServer).contains(name);
    }

    /** 记录行为事件（放置/破坏/交互），双写：内存 + 落盘缓冲。 */
    public void recordAction(UUID id, String name, ActionEvent e) {
        var list = actionBuffer.computeIfAbsent(id, k -> new ArrayList<>());
        if (list.size() < 10000) list.add(e);
        store.appendAction(id, e);   // 双写：内存推送 + 落盘缓冲（重启不丢）
    }

    /** 取出并清空某玩家的行为事件（攒批推送用）。 */
    public List<ActionEvent> drainActions(UUID id) {
        var list = actionBuffer.remove(id);
        return list == null ? List.of() : list;
    }

    /** 历史传送：把观察者送到目标玩家在某时刻的位置（含跨维度）。 */
    public void teleportTo(ServerPlayer watcher, UUID target, long timeMillis) {
        List<TrackPoint> history = store.loadRange(target, TrackConfig.retentionDays());
        if (history.isEmpty()) {
            watcher.displayClientMessage(new net.minecraft.network.chat.TranslatableComponent("glowtrack.teleport.empty"), false);
            return;
        }
        TrackPoint nearest = com.glowtrack.track.TrackTimes.nearestByTime(history, timeMillis);
        if (nearest == null) {
            watcher.displayClientMessage(new net.minecraft.network.chat.TranslatableComponent("glowtrack.teleport.empty"), false);
            return;
        }
        ServerLevel level = watcher.getLevel().getServer().getLevel(
                ResourceKey.create(Registry.DIMENSION_REGISTRY, nearest.dimension()));
        if (level == null) level = watcher.getLevel();
        // 1.18.2 的 teleportTo 是 7 参（没有 1.20.5+ 的尾部 boolean）
        watcher.teleportTo(level, nearest.x(), nearest.y(), nearest.z(),
                nearest.yaw(), nearest.pitch());
        watcher.displayClientMessage(new net.minecraft.network.chat.TranslatableComponent("glowtrack.teleport.done",
                com.glowtrack.track.TrackTimes.hms(nearest.timeMillis()),
                String.format("%.1f, %.1f, %.1f", nearest.x(), nearest.y(), nearest.z())), false);
    }
}
