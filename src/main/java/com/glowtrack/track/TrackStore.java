package com.glowtrack.track;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.glowtrack.GlowTrack;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轨迹存储：内存索引 + 文件持久化 + 导入导出。
 *
 * <p>文件布局：{@code <root>/<uuid>/<yyyy-MM-dd>.json}。
 * 内存中以 {@code UUID -> PlayerTrack} 索引当前日期的轨迹；
 * 历史日期的轨迹按需从磁盘读取。</p>
 *
 * <p>线程安全：所有公开方法都在锁内操作，服务端 tick 与网络线程均可调用。</p>
 */
public final class TrackStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final Path root;
    /** uuid -> 今日轨迹 */
    private final Map<UUID, PlayerTrack> today = new ConcurrentHashMap<>();
    /** uuid -> 待落盘的行为事件（与轨迹点流独立，save 时取出合并写入 .actions.json） */
    private final Map<UUID, List<ActionEvent>> storedActions = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    public TrackStore(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            GlowTrack.LOGGER.error("[GlowTrack] 无法创建轨迹目录 {}", root, e);
        }
    }

    public Path root() { return root; }

    public static String todayKey() {
        return LocalDate.now(ZONE).toString();
    }

    /** 取得（必要时创建）某玩家今日的轨迹容器。 */
    public PlayerTrack trackFor(UUID id, String name) {
        synchronized (lock) {
            return today.computeIfAbsent(id, k -> new PlayerTrack(k, name, todayKey()));
        }
    }

    /** 只读地取某玩家今日轨迹，不存在返回 null。 */
    public PlayerTrack peek(UUID id) {
        synchronized (lock) {
            return today.get(id);
        }
    }

    /** 所有今日轨迹的快照列表。 */
    public List<PlayerTrack> allToday() {
        synchronized (lock) {
            return new ArrayList<>(today.values());
        }
    }

    /** 追加一个点并返回是否成功。 */
    public boolean append(UUID id, String name, TrackPoint p) {
        PlayerTrack t = trackFor(id, name);
        boolean ok = t.append(p);
        // 只在超限时裁剪（避免每 append 一次 O(n) subList 检查；50k 点时每秒 20 次）
        if (ok && t.size() > com.glowtrack.TrackConfig.maxPointsPerPlayer())
            t.trimToSize(com.glowtrack.TrackConfig.maxPointsPerPlayer());
        return ok;
    }

    /** 追加一个行为事件进待落盘缓冲（内存上限裁剪，保留最新）。 */
    public void appendAction(UUID id, ActionEvent e) {
        List<ActionEvent> list = storedActions.computeIfAbsent(id, k -> new ArrayList<>());
        synchronized (list) {
            list.add(e);
            int max = 10000;
            if (list.size() > max) list.subList(0, list.size() - max).clear();
        }
    }

    // ==================== 持久化 ====================

    public Path fileFor(UUID id, String date) {
        return root.resolve(id.toString()).resolve(date + ".json");
    }

    /** 行为事件的独立文件（不与轨迹文件格式耦合）：{@code <uuid>/<date>.actions.json} */
    public Path actionsFileFor(UUID id, String date) {
        return root.resolve(id.toString()).resolve(date + ".actions.json");
    }

    /** 将某玩家今日轨迹写盘（轨迹与行为事件分别写各自的文件）。 */
    public void save(UUID id) {
        PlayerTrack t;
        List<ActionEvent> actions;
        synchronized (lock) {
            t = today.get(id);
            actions = storedActions.remove(id);   // 取出即清空：下次落盘只写新增部分
        }
        if (t != null && !t.isEmpty()) {
            Path f = fileFor(id, t.date());
            try {
                Files.createDirectories(f.getParent());
                try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                    GSON.toJson(toJson(t), w);
                }
            } catch (IOException e) {
                GlowTrack.LOGGER.error("[GlowTrack] 保存轨迹失败 {}", f, e);
            }
        }
        // 行为事件：与已有文件合并后整体重写（落盘期间新事件仍可继续 append）
        if (actions != null && !actions.isEmpty()) {
            String date = (t != null && !t.isEmpty()) ? t.date() : todayKey();
            saveActions(id, date, actions);
        }
    }

    /** 行为事件合并写盘：读旧文件（容错）+ 内存新增，按时间排序去重后整体重写。 */
    private void saveActions(UUID id, String date, List<ActionEvent> fresh) {
        Path f = actionsFileFor(id, date);
        try {
            Files.createDirectories(f.getParent());
            Map<Long, ActionEvent> merged = new HashMap<>();
            for (ActionEvent e : loadActionsFromDisk(id, date))
                merged.put(e.t(), e);
            for (ActionEvent e : fresh)
                merged.put(e.t(), e);
            List<ActionEvent> all = new ArrayList<>(merged.values());
            all.sort(java.util.Comparator.comparingLong(ActionEvent::t));
            JsonObject o = new JsonObject();
            o.addProperty("version", 1);
            o.addProperty("player", id.toString());
            o.addProperty("date", date);
            JsonArray arr = new JsonArray();
            for (ActionEvent e : all) {
                JsonObject j = new JsonObject();
                j.addProperty("t", e.t());
                j.addProperty("type", e.type());
                j.addProperty("x", e.x());
                j.addProperty("y", e.y());
                j.addProperty("z", e.z());
                j.addProperty("target", e.target());
                arr.add(j);
            }
            o.add("actions", arr);
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(o, w);
            }
        } catch (IOException e) {
            GlowTrack.LOGGER.error("[GlowTrack] 保存行为事件失败 {}", f, e);
        }
    }

    /** 从磁盘读取某玩家指定日期的行为事件；文件不存在/损坏返回空列表（容错）。 */
    private List<ActionEvent> loadActionsFromDisk(UUID id, String date) {
        Path f = actionsFileFor(id, date);
        if (!Files.exists(f)) return new ArrayList<>();
        List<ActionEvent> out = new ArrayList<>();
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            if (!obj.has("actions")) return out;   // 旧格式/空文件：无 actions 键
            for (JsonElement el : obj.getAsJsonArray("actions")) {
                try {
                    JsonObject e = el.getAsJsonObject();
                    String type = e.get("type").getAsString();
                    long ts = e.get("t").getAsLong();
                    if (ts < 0) continue;
                    out.add(new ActionEvent(
                            ts, type,
                            e.has("playerId") ? e.get("playerId").getAsString() : id.toString(),
                            e.get("x").getAsDouble(),
                            e.get("y").getAsDouble(),
                            e.get("z").getAsDouble(),
                            e.has("target") ? e.get("target").getAsString() : ""));
                } catch (Exception ex) {
                    GlowTrack.LOGGER.warn("[GlowTrack] 跳过损坏的行为事件: {}", ex.getMessage());
                }
            }
        } catch (Exception e) {
            GlowTrack.LOGGER.warn("[GlowTrack] 读取行为事件失败 {}（按空处理）: {}", f, e.getMessage());
        }
        return out;
    }

    /** 供快照加载用：读某玩家指定日期的行为事件（公开只读）。 */
    public List<ActionEvent> loadActions(UUID id, String date) {
        return loadActionsFromDisk(id, date);
    }

    /** 保存全部。 */
    public void saveAll() {
        for (UUID id : new ArrayList<>(today.keySet())) save(id);
    }

    /** 从磁盘读取某玩家指定日期的轨迹；不存在返回 null。 */
    public PlayerTrack load(UUID id, String date) {
        Path f = fileFor(id, date);
        if (!Files.exists(f)) return null;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            return fromJson(obj);
        } catch (Exception e) {
            GlowTrack.LOGGER.error("[GlowTrack] 读取轨迹失败 {}", f, e);
            return null;
        }
    }

    /**
     * 合并读取某玩家最近若干天的历史轨迹（含今日），返回按时间升序合并后的完整列表。
     * 用于时间轴回看时加载较长跨度的数据。
     */
    public List<TrackPoint> loadRange(UUID id, int days) {
        List<TrackPoint> out = new ArrayList<>();
        LocalDate todayLd = LocalDate.now(ZONE);
        for (int i = 0; i < days; i++) {
            String date = todayLd.minusDays(i).toString();
            PlayerTrack t = (i == 0) ? peek(id) : null;
            if (t == null) t = load(id, date);
            if (t != null) out.addAll(t.points());
        }
        out.sort(java.util.Comparator.comparingLong(TrackPoint::timeMillis));
        return out;
    }

    // ==================== JSON 转换 ====================

    public JsonObject toJson(PlayerTrack t) {
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);
        o.addProperty("player", t.player().toString());
        o.addProperty("name", t.name());
        o.addProperty("date", t.date());
        JsonArray arr = new JsonArray();
        for (TrackPoint p : t.points()) {
            JsonObject e = new JsonObject();
            e.addProperty("t", p.timeMillis());
            e.addProperty("x", p.x());
            e.addProperty("y", p.y());
            e.addProperty("z", p.z());
            e.addProperty("d", p.dimension() == null ? "" : p.dimension().toString());
            e.addProperty("v", p.speed());
            e.addProperty("yaw", p.yaw());
            e.addProperty("pitch", p.pitch());
            arr.add(e);
        }
        o.add("points", arr);
        return o;
    }

    public PlayerTrack fromJson(JsonObject o) {
        UUID id = UUID.fromString(o.get("player").getAsString());
        String name = o.has("name") ? o.get("name").getAsString() : "";
        String date = o.has("date") ? o.get("date").getAsString() : todayKey();
        PlayerTrack t = new PlayerTrack(id, name, date);
        List<TrackPoint> pts = new ArrayList<>();
        for (JsonElement el : o.getAsJsonArray("points")) {
            // 逐点容错：手改文件/导入损坏数据时跳过坏点而不是整份轨迹加载失败
            try {
                JsonObject e = el.getAsJsonObject();
                long ts = e.get("t").getAsLong();
                ResourceLocation dim = ResourceLocation.tryParse(e.get("d").getAsString());
                if (dim == null || ts < 0) continue;   // 缺关键字段（时间/维度）的点无意义
                pts.add(new TrackPoint(
                        ts,
                        e.get("x").getAsDouble(),
                        e.get("y").getAsDouble(),
                        e.get("z").getAsDouble(),
                        dim,
                        e.has("v") ? e.get("v").getAsFloat() : 0f,
                        e.has("yaw") ? e.get("yaw").getAsFloat() : 0f,
                        e.has("pitch") ? e.get("pitch").getAsFloat() : 0f));
            } catch (Exception ex) {
                GlowTrack.LOGGER.warn("[GlowTrack] 跳过损坏的轨迹点: {}", ex.getMessage());
            }
        }
        t.load(pts);
        return t;
    }

    // ==================== 导入 / 导出 ====================

    /** 导出为 JSON 字符串。 */
    public String exportJson(PlayerTrack t) {
        return GSON.toJson(toJson(t));
    }

    /** 导出为 CSV（表头 + 每点一行）。 */
    public String exportCsv(PlayerTrack t) {
        StringBuilder sb = new StringBuilder("time,x,y,z,dim,speed,yaw,pitch\n");
        for (TrackPoint p : t.points()) {
            sb.append(p.timeMillis()).append(',')
              .append(p.x()).append(',')
              .append(p.y()).append(',')
              .append(p.z()).append(',')
              .append(p.dimension()).append(',')
              .append(p.speed()).append(',')
              .append(p.yaw()).append(',')
              .append(p.pitch()).append('\n');
        }
        return sb.toString();
    }

    /**
     * 从 JSON 字符串导入并合并进存储（按 timeMillis 去重）。
     * @return 合并进的点数
     */
    public int importJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        PlayerTrack t = fromJson(o);
        return mergeInto(t);
    }

    /**
     * 从 CSV 字符串导入并合并。列：time,x,y,z,dim[,speed,yaw,pitch]
     * @return 合并进的点数
     */
    public int importCsv(String csv, UUID id, String name) {
        String[] lines = csv.split("\n");
        PlayerTrack t = trackFor(id, name);
        int n = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || (i == 0 && line.toLowerCase().startsWith("time"))) continue;
            String[] c = line.split(",");
            if (c.length < 5) continue;
            try {
                ResourceLocation dim = ResourceLocation.tryParse(c[4].trim());
                if (dim == null) continue;   // 与 fromJson 对齐：坏维度行直接跳过，防止 null 维度点混入
                TrackPoint p = new TrackPoint(
                        Long.parseLong(c[0].trim()),
                        Double.parseDouble(c[1].trim()),
                        Double.parseDouble(c[2].trim()),
                        Double.parseDouble(c[3].trim()),
                        dim,
                        c.length > 5 ? Float.parseFloat(c[5].trim()) : 0f,
                        c.length > 6 ? Float.parseFloat(c[6].trim()) : 0f,
                        c.length > 7 ? Float.parseFloat(c[7].trim()) : 0f);
                if (t.append(p)) n++;
            } catch (NumberFormatException ignored) {}
        }
        return n;
    }

    /** 把一条轨迹的点合并进对应玩家的今日轨迹（去重）。 */
    private int mergeInto(PlayerTrack src) {
        PlayerTrack dst = trackFor(src.player(), src.name());
        int n = 0;
        for (TrackPoint p : src.points()) {
            if (dst.append(p)) n++;
        }
        return n;
    }

    // ==================== 保留策略 ====================

    /** 删除早于 retentionDays 的磁盘文件，并裁剪内存。返回删除的文件数。 */
    public int enforceRetention(int retentionDays) {
        long cutoff = System.currentTimeMillis() - retentionDays * 24L * 3600L * 1000L;
        LocalDate limitDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(cutoff), ZONE);
        int removed = 0;
        // 先把要处理的目录收集出来并关掉 walk 流——Windows 上开着句柄删不掉目录
        List<Path> dirs = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isDirectory).forEach(dirs::add);
        } catch (IOException e) {
            GlowTrack.LOGGER.error("[GlowTrack] 清理历史轨迹失败", e);
            return 0;
        }
        for (Path p : dirs) {
            if (p.equals(root)) continue;
            try (var files = Files.list(p)) {
                for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".json"))::iterator) {
                    String base = f.getFileName().toString().replace(".json", "");
                    if (base.endsWith(".actions")) base = base.substring(0, base.length() - ".actions".length());
                    try {
                        if (LocalDate.parse(base).isBefore(limitDate)) {
                            Files.deleteIfExists(f);
                            removed++;
                        }
                    } catch (Exception ignored) {}
                }
            } catch (IOException ignored) {}
            // 空目录一并删掉，否则 UUID 目录会随玩家数量无限累积
            try (var any = Files.list(p)) {
                if (!any.findAny().isPresent()) Files.deleteIfExists(p);
            } catch (IOException ignored) {}
        }
        return removed;
    }
}
