package com.glowtrack.server;

import com.glowtrack.GlowTrack;
import com.glowtrack.TrackConfig;
import com.glowtrack.network.Payloads;
import com.glowtrack.track.PlayerTrack;
import com.glowtrack.track.ActionEvent;
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
 * 閺堝秴濮熺粩顖濆缓鏉╁湱顓搁悶鍡楁珤閿涙艾宕熸笟瀣ㄢ偓?
 *
 * <p>閼卞矁鐭楅敍?/p>
 * <ul>
 *   <li>閺堝秴濮熺粩?tick 鐎佃澧嶉張澶屾閸氬秴宕熼崷銊у殠閻溾晛顔嶉柌鍥ㄧ壉</li>
 *   <li>缂佸瓨濮?鐠嬩礁婀潻鍊熼嚋鐠?閻ㄥ嫯顓归梼鍛彠缁紮绱濈悮顐ユ嫹闊亞甯虹€瑰墎些閸斻劍妞傛晶鐐哄櫤閹恒劑鈧?/li>
 *   <li>閸濆秴绨插鈧慨?閸嬫粍顒涙潻鍊熼嚋閵嗕椒绗呴崣鎴濇彥閻撗佲偓浣稿坊閸欒弓绱堕柅?/li>
 *   <li>鐎规碍婀￠拃鐣屾磸娑撳簼绻氶悾娆愮閻?/li>
 * </ul>
 */
@EventBusSubscriber(modid = GlowTrack.MOD_ID)
public final class ServerTrackManager {
    private static ServerTrackManager instance;

    private final TrackStore store;
    private final MinecraftServer minecraftServer;
    private final Map<UUID, Sampler> samplers = new ConcurrentHashMap<>();
    /** 鐟欏倸鐧傞懓?UUID -> 濮濓絽婀潻鍊熼嚋閻ㄥ嫮娲伴弽?UUID 闂嗗棗鎮?*/
    private final Map<UUID, Set<UUID>> watchers = new ConcurrentHashMap<>();
    private int saveCooldown = 0;
    /** 鐞涘奔璐熸禍瀣╂缂傛挸鍟块敍姝眑ayerId -> 娴滃娆㈤崚妤勩€冮敍鍫滃瘜缁捐法鈻兼禍瀣╂閸愭瑥鍙嗛敍灞炬晙閹佃甯归柅浣告倵濞撳懐鈹栭敍澶堚偓?*/
    private final java.util.Map<java.util.UUID, java.util.List<com.glowtrack.track.ActionEvent>> actionBuffer = new java.util.concurrent.ConcurrentHashMap<>();
    /** 閻ц棄鎮曢崡鏇犵处鐎涙﹫绱伴崢鐔哥槨 tick 闁插秴缂?Set + 閹风柉绀夐弫鎵矋閿涘瞼骞?20 缁夋帒鍩涢弬棰佺濞嗏槄绱欓惂钘夋倳閸楁洖鐔€閺堫兛绗夐崣姗堢礆閵?*/
    private Set<String> whitelistCache = null;
    private long whitelistCacheAt = 0;

    private ServerTrackManager(MinecraftServer server) {
        Path root = server.getServerDirectory().resolve("glowtrack");
        this.store = new TrackStore(root);
        this.minecraftServer = server;
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
        GlowTrack.LOGGER.info("[GlowTrack] track storage ready, cleaned {} expired files", removed);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (instance != null) {
            instance.store.saveAll();
            GlowTrack.LOGGER.info("[GlowTrack] 閺堝秴濮熺粩顖濆缓鏉╃懓鍑￠崗銊╁劥閽€鐣屾磸");
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

        // 濮ｅ繋閲滅憴鍌氱檪閼板懍绔存稉顏呮晙閹电懓鍨悰顭掔窗閺?tick 閸愬懎顦挎稉顏囶潶鏉╁€熼嚋閻溾晛顔嶉惃鍕仯閸氬牆鑻熸潻娑楃娑擃亜瀵?
        // key = watcher UUID閿涘瘉alue = (target UUID -> 閻愮懓鍨悰?閵嗗倹瀵滈惄顔界垼閸掑棛绮嶉敍?
        // 閸ョ姳璐?TrackData.player 閺勵垰宕熼崐灏栤偓鏂衡偓鏂款樋閻╊喗鐖ｅǎ宄版躬娑撯偓娑擃亜瀵橀柌灞肩窗鐞氼偄顓归幋椋庮伂閹?id 鏉╁洦鎶ゆ稉銏犵磾閵?
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

            // 閼汇儲顒滅悮顐＄铂娴滈缚鎷烽煪顏庣礉閹?(鐟欏倸鐧傞懓? 閻╊喗鐖? 閺€鎺撳
            for (Map.Entry<UUID, Set<UUID>> e : watchers.entrySet()) {
                if (e.getValue().contains(id)) {
                    batch.computeIfAbsent(e.getKey(), k -> new java.util.HashMap<>())
                         .computeIfAbsent(id, k -> new ArrayList<>()).add(net(id, p));
                }
            }
        }

        // 濮ｅ繗顫囩€电喕鈧懏鐦￠惄顔界垼濮?tick 閼峰啿顦挎稉鈧稉顏勫瘶閿涘牐顫囩€电喕鈧懎鎮撻弮鎯版嫹婢舵矮閲滈悳鈺侇啀閺冭泛鎮囬弨璺烘倗閻ㄥ嫬瀵橀敍?
        for (Map.Entry<UUID, Map<UUID, List<Payloads.NetPoint>>> e : batch.entrySet()) {
            ServerPlayer watcher = server.getPlayerList().getPlayer(e.getKey());
            if (watcher == null) continue;
            for (Map.Entry<UUID, List<Payloads.NetPoint>> t : e.getValue().entrySet()) {
                com.glowtrack.network.ServerNet.send(watcher, new Payloads.TrackData(
                        t.getKey(), false, t.getValue()));
                // 鐞涘奔璐熸禍瀣╂闂呭繗寤烘潻鐟邦杻闁插繋绔寸挧閿嬪腹闁緤绱欓崣顖炩偓澶愨偓姘朵壕閿涙艾顕粩顖涚梾鐟佸懏妞傞棃娆撶帛鐠哄疇绻冮敍?
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

        // 閸涖劍婀￠拃鐣屾磸閿涘牆绱撳銉窗绾句胶娲?IO 娑撳秹妯嗘繅鐐板瘜缁捐法鈻?tick閿涙笨rackStore 閸愬懘鍎村鏌ユ敚閿涘苯鎻╅悡褍鎮楅崘娆戞磸鐎瑰鍙忛敍?
        if (++saveCooldown >= 6000) { // 缁?5 閸掑棝鎸?
            saveCooldown = 0;
            java.util.List<PlayerTrack> snapshot = store.allToday();
            Thread worker = new Thread(() -> {
                for (PlayerTrack t : snapshot) store.save(t.player());
                GlowTrack.LOGGER.debug("[GlowTrack] async save {} player tracks", snapshot.size());
            }, "GlowTrack-Save");
            worker.setDaemon(true);
            worker.start();
        }
    }

    /** 鐠囪褰囬惂钘夋倳閸楁洜甯虹€硅泛鎮曢梿鍡楁値閿涘牆鐢?20s 缂傛挸鐡ㄩ敍娉僫ck 閻戭叀鐭惧鍕槨缁夋帟鐨熼悽?20 濞嗏槄绱濋柌宥呯紦閹存劖婀?O(n) 閺佹壆绮嶉幏鐤閿涘鈧?*/
    private Set<String> whitelistNames(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (whitelistCache != null && now - whitelistCacheAt < 20_000L) return whitelistCache;
        Set<String> set = new HashSet<>();
        try {
            String[] names = server.getPlayerList().getWhiteListNames();
            for (String n : names) set.add(n);
        } catch (Exception e) {
            GlowTrack.LOGGER.warn("[GlowTrack] 鐠囪褰囬惂钘夋倳閸楁洖銇戠拹? {}", e.getMessage());
        }
        whitelistCache = set;
        whitelistCacheAt = now;
        return set;
    }

    private Payloads.NetPoint net(UUID id, TrackPoint p) {
        return new Payloads.NetPoint(p.timeMillis(), p.x(), p.y(), p.z(),
                p.dimension().toString(), p.speed(), p.yaw(), p.pitch());
    }

    // ==================== 鐎电懓顦婚幒銉ュ經 ====================

    /** 娑撳褰傞惂钘夋倳閸楁洜甯虹€硅泛鍨悰顭掔礄閸氼偄婀痪璺ㄥЦ閹椒绗屾禒濠冩）鏉炪劏鎶楅悙瑙勬殶閿涘鈧?*/
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
     * 鐟欙絾鐎界粋鑽ゅ殠閻ц棄鎮曢崡鏇犲负鐎瑰墎娈戦惇鐔风杽 UUID閵?
     *
     * <p>鏉炪劏鎶楅弫鐗堝祦閺勵垱瀵滈悳鈺侇啀閻喎鐤?UUID 鐠佹澘缍嶉惃鍕┾偓鍌欑喘閸忓牅绮犻張宥呭閸?usercache
     * 閿涘澖@link GameProfileCache}閿涘袙閺嬫劕婀痪鎸幠佸蹇庣瑓閻?Mojang UUID閿?
     * 缂傛挸鐡ㄩ張顏勬嚒娑擃厽妞傞崶鐐衡偓鈧崚?{@code OfflinePlayer:name} 閹恒劌顕遍敍鍫㈩瀲缁炬寧膩瀵繑婀囬崝鈥虫珤閿涘鈧?/p>
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

    /** 缁傝崵鍤庨惂钘夋倳閸楁洜甯虹€瑰墎鏁?OfflinePlayer:name 閹恒劌顕?UUID閵?*/
    private UUID legacyId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 瀵偓婵鎷烽煪顏庣窗閸旂姾娴囬崢鍡楀蕉韫囶偆鍙?+ 閸旂姴鍙嗙拋銏ゆ閵?*/
    public void startTracking(ServerPlayer watcher, UUID target, int days) {
        int d = Math.max(1, Math.min(days, TrackConfig.RETENTION_DAYS.get()));
        List<TrackPoint> history = store.loadRange(target, d);
        List<Payloads.NetPoint> net = new java.util.ArrayList<>();
        for (TrackPoint p : history) net.add(net(target, p));
        com.glowtrack.network.ServerNet.send(watcher, new Payloads.TrackData(target, true, net));
        // 快照附带当日历史行为事件（回放可见他今天干过什么）
        var hist = store.loadActions(target, com.glowtrack.track.TrackStore.todayKey());
        if (!hist.isEmpty()) {
            var netActs = new ArrayList<com.glowtrack.network.ActionDataPackets.NetAction>();
            for (var ae : hist) netActs.add(new com.glowtrack.network.ActionDataPackets.NetAction(
                    ae.t(), ae.type(), ae.target(), ae.x(), ae.y(), ae.z()));
            com.glowtrack.network.ServerNet.send(watcher, new com.glowtrack.network.ActionDataPackets.ActionData(target, netActs));
        }
        watchers.computeIfAbsent(watcher.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(target);
        GlowTrack.LOGGER.info("[GlowTrack] {} started tracking {} ({} points)", watcher.getName().getString(), target, net.size());
    }

    public void stopTracking(ServerPlayer watcher, UUID target) {
        Set<UUID> set = watchers.get(watcher.getUUID());
        if (set != null) set.remove(target);
    }

    /** 閻溾晛顔嶉柅鈧崙鐑樻濞撳懐鎮婇崗鎯邦吂闂冨懎鍙х化璁崇瑢闁插洦鐗遍崳顭掔礄鐠囥儳甯虹€硅泛褰查懗钘夋倱閺冭埖妲哥憴鍌氱檪閼板懎鎷扮悮顐ユ嫹闊亣鈧拑绱氶妴?*/
    public void removeWatcher(UUID watcher) {
        watchers.remove(watcher);
        samplers.remove(watcher);
    }

    /** 閻ц棄鎮曢崡鏇炲敶閻溾晛顔嶉崚銈呯暰閿涘牐顢戞稉杞扮皑娴犲墎娲冮崥顒傛暏閿涘鈧?*/
    public boolean isTracked(String name) {
        return whitelistNames(minecraftServer).contains(name);
    }

    /** 鐠佹澘缍嶆稉鈧弶陇顢戞稉杞扮皑娴犺绱欐稉鑽ゅ殠缁嬪绨ㄦ禒鍓佹磧閸氼剝鐨熼悽顭掔礆閵?*/
    public void recordAction(java.util.UUID id, String name, com.glowtrack.track.ActionEvent e) {
        var list = actionBuffer.computeIfAbsent(id, k -> new ArrayList<>());
        if (list.size() < 10000) list.add(e);
        store.appendAction(id, e);   // 鍙屽啓锛氬唴瀛樻帹閫?+ 钀界洏缂撳啿锛堥噸鍚笉涓級
    }

    /** 閸欐牕鍤獮鑸电缁岀儤鐓囬悳鈺侇啀閻ㄥ嫯顢戞稉杞扮皑娴犺绱欓弨鎺撳閹恒劑鈧胶鏁ら敍澶堚偓?*/
    public java.util.List<com.glowtrack.track.ActionEvent> drainActions(java.util.UUID id) {
        var list = actionBuffer.remove(id);
        return list == null ? java.util.List.of() : list;
    }

    /** 閸樺棗褰舵导鐘烩偓渚婄窗閹跺﹨顫囩€电喕鈧懘鈧礁鍩岄惄顔界垼閻溾晛顔嶉崷銊︾厙閺冭泛鍩㈤惃鍕秴缂冾噯绱欓崥顐ユ硶缂佹潙瀹抽敍澶堚偓?*/
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
