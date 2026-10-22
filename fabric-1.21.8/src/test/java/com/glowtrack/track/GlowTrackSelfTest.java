package com.glowtrack.track;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 零依赖回归自检（不引入 JUnit——本机 mavenCentral 不通）。
 *
 * <p>跑法：{@code gradle test}（core/worldreplay 同款做法）。</p>
 */
public final class GlowTrackSelfTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        nullDimensionCsvIsSkipped();
        nullDimensionDoesNotBreakExport();
        trackPointAppendKeepsTimeOrder();
        samplerRecordsOnDimensionChange();
        ribbonWidthIsRealGeometry();
        ribbonJointsAreContinuous();
        nullDimensionDoesNotCrashGeometry();
        lineWidthSettingIsClamped();
        sameMillisecondPointReplacesPrevious();
        dimensionsSkipNullDimension();
        retentionRemovesExpiredFilesAndEmptyDirs();
        packetCountClampBlocksOom();
        samplerDetectsSmoothTurn();
        actionEventsAreClearedOnTargetSwitch();

        System.out.println(failed == 0
                ? "OK: all glowtrack self-tests passed (" + passed + ")"
                : "FAILED: " + failed + " check(s) failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    /** 线宽设置必须夹在 1..12（设置文件被手改过也不能越界渲染）。 */
    private static void lineWidthSettingIsClamped() {
        var s = new com.glowtrack.client.ClientSettings();
        check("线宽默认 5 格", s.lineWidth == 5, "w=" + s.lineWidth);
        s.lineWidth = 999;
        s.clampLineWidth();
        check("线宽上限夹取到 12", s.lineWidth == 12, "w=" + s.lineWidth);
        s.lineWidth = -50;
        s.clampLineWidth();
        check("线宽下限夹取到 1", s.lineWidth == 1, "w=" + s.lineWidth);
    }

    /** 同毫秒的点应替换最后一个而不是被丢弃（同毫秒的后点是更真实的位置）。 */
    private static void sameMillisecondPointReplacesPrevious() {
        var dim = net.minecraft.resources.ResourceLocation.parse("minecraft:overworld");
        var t = new PlayerTrack(UUID.randomUUID(), "t", "2026-10-21");
        boolean ok1 = t.append(new TrackPoint(1000, 0, 64, 0, dim, 0, 0, 0));
        boolean ok2 = t.append(new TrackPoint(2000, 5, 64, 0, dim, 0, 0, 0));
        boolean ok3 = t.append(new TrackPoint(2000, 9, 64, 0, dim, 0, 0, 0));   // 同毫秒
        check("首点加入", ok1, "");
        check("正常升序点加入", ok2, "");
        check("同毫秒点返回 true（替换语义）", ok3, "ok3=" + ok3);
        check("同毫秒点替换后仍是 2 个点", t.size() == 2, "size=" + t.size());
        check("同毫秒点是新位置", t.last().x() == 9.0, "x=" + t.last().x());
        boolean ok4 = t.append(new TrackPoint(1500, 0, 64, 0, dim, 0, 0, 0));   // 乱序
        check("乱序点被拒绝", !ok4 && t.size() == 2, "ok4=" + ok4);
    }

    /** dimensions() 集合不得包含 null（坏点不应污染维度集合）。 */
    private static void dimensionsSkipNullDimension() {
        var dim = net.minecraft.resources.ResourceLocation.parse("minecraft:overworld");
        var t = new PlayerTrack(UUID.randomUUID(), "t", "2026-10-21");
        t.load(java.util.List.of(
                new TrackPoint(1000, 0, 64, 0, dim, 0, 0, 0),
                new TrackPoint(2000, 0, 64, 0, null, 0, 0, 0)));   // 坏点
        var dims = t.dimensions();
        check("null 维度不计入维度集合", dims.size() == 1 && dims.contains(dim), "dims=" + dims);
    }

    /** 保留清理：过期文件被删、目录清空后一并删除、未过期文件保留。 */
    private static void retentionRemovesExpiredFilesAndEmptyDirs() throws Exception {        var root = java.nio.file.Files.createTempDirectory("gtstore");
        try {
            var id = UUID.randomUUID();
            var dir = root.resolve(id.toString());
            java.nio.file.Files.createDirectories(dir);
            // 过期文件（2020-01-01）与未过期文件（2999-12-31）
            java.nio.file.Files.writeString(dir.resolve("2020-01-01.json"), "{}");
            java.nio.file.Files.writeString(dir.resolve("2020-01-01.actions.json"), "{}");
            java.nio.file.Files.writeString(dir.resolve("2999-12-31.json"), "{}");
            // 只含过期文件的目录（清空后应被删除）
            var doomed = root.resolve(UUID.randomUUID().toString());
            java.nio.file.Files.createDirectories(doomed);
            java.nio.file.Files.writeString(doomed.resolve("2020-06-01.json"), "{}");
            // 另一个空目录（模拟以前清理剩下的）
            var emptyDir = root.resolve(UUID.randomUUID().toString());
            java.nio.file.Files.createDirectories(emptyDir);

            var store = new com.glowtrack.track.TrackStore(root);
            int removed = store.enforceRetention(30);

            check("过期文件被删除（3 个）", removed >= 3, "removed=" + removed);
            check("未过期文件保留", java.nio.file.Files.exists(dir.resolve("2999-12-31.json")), "");
            check("未过期文件所在的目录保留", java.nio.file.Files.exists(dir), "");
            check("清空后的目录被删除", !java.nio.file.Files.exists(doomed), "");
            check("预先存在的空目录被删除", !java.nio.file.Files.exists(emptyDir), "");
        } finally {
            // 递归清理临时目录
            try (var w = java.nio.file.Files.walk(root)) {
                w.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    /**
     * 网络包列表长度必须钳制：恶意客户端填一个巨大的 varint 长度，
     * 若直接 new ArrayList<>(n) 会分配巨量内存（OOM）。
     */
    private static void packetCountClampBlocksOom() {
        int max = 4096;
        check("正常长度原样返回", com.glowtrack.network.Payloads.clampCount(10, max) == 10, "");
        check("超大长度被钳到上限", com.glowtrack.network.Payloads.clampCount(Integer.MAX_VALUE, max) == max, "");
        check("负长度归零", com.glowtrack.network.Payloads.clampCount(-1, max) == 0, "");
        check("上限为 0 时恒为 0", com.glowtrack.network.Payloads.clampCount(100, 0) == 0, "");
    }

    /**
     * 换目标/停止追踪后，行为事件不能残留。
     *
     * <p>ActionRenderer 直接用 actionsView() 渲染，不看 tracked——
     * 事件不清理就会在世界里留下上一个目标的"幽灵标记"。
     * 而且事件流只进不出（不像轨迹点有服务端上限兜底），
     * 长时间追踪会无上限增长吃满内存。</p>
     */
    private static void actionEventsAreClearedOnTargetSwitch() {
        var state = com.glowtrack.client.ClientTrackState.get();
        var id1 = UUID.randomUUID();
        var id2 = UUID.randomUUID();
        var net = new java.util.ArrayList<com.glowtrack.network.ActionDataPackets.NetAction>();
        net.add(new com.glowtrack.network.ActionDataPackets.NetAction(
                1000L, "break", "minecraft:stone", 1, 2, 3));
        state.onActionData(id1, net);
        check("事件已接收", state.actionsView().size() == 1, "size=" + state.actionsView().size());

        // 换目标（快照）→ 旧目标的事件必须清掉
        state.onTrackData(new com.glowtrack.network.Payloads.TrackData(
                id2, true, java.util.List.of()));
        check("换目标后旧事件被清空", state.actionsView().isEmpty(),
                "size=" + state.actionsView().size());

        // 再来一次，验证停止追踪也会清
        state.onActionData(id2, net);
        check("事件已重新接收", state.actionsView().size() == 1, "size=" + state.actionsView().size());
        state.clearTracking();
        check("停止追踪后事件被清空", state.actionsView().isEmpty(),
                "size=" + state.actionsView().size());
    }

    /**
     * null 维度的点不能让几何/时间轴崩掉。
     * inDimension 曾直接 dimension.equals(dim)——dimension 为 null 时 NPE，
     * 而时间轴的维度切换标记会对相邻两点反复调用它。
     */
    private static void nullDimensionDoesNotCrashGeometry() {
        var dim = net.minecraft.resources.ResourceLocation.parse("minecraft:overworld");
        java.util.List<com.glowtrack.track.TrackPoint> pts = java.util.List.of(
                new TrackPoint(1000, 0, 64, 0, dim, 0, 0, 0),
                new TrackPoint(2000, 0, 64, 0, null, 0, 0, 0),      // 坏点：维度为 null
                new TrackPoint(3000, 0, 64, 0, dim, 0, 0, 0));
        // inDimension 不得抛异常
        boolean ok = true;
        try {
            pts.get(1).inDimension(dim);
            pts.get(0).inDimension(null);
        } catch (NullPointerException e) {
            ok = false;
        }
        check("null 维度的 inDimension 不抛 NPE", ok, "");
        // contiguousRuns 不得因坏点崩掉
        java.util.List<int[]> runs = com.glowtrack.client.render.TrackGeometry.contiguousRuns(pts, dim);
        check("null 维度点被跳过、其余仍成段", runs.size() == 2, "runs=" + runs.size());
    }

    /** 带子宽度验证：直线段的 L/R 顶点对距离 = 线宽（几何宽度真实生效，不再依赖 glLineWidth）。 */    private static void ribbonWidthIsRealGeometry() {
        var dim = net.minecraft.resources.ResourceLocation.parse("minecraft:overworld");
        java.util.List<com.glowtrack.track.TrackPoint> pts = java.util.List.of(
                new TrackPoint(1000, 0, 64, 0, dim, 0, 0, 0),
                new TrackPoint(2000, 10, 64, 0, dim, 0, 0, 0));   // 沿 +X 的直线
        float width = 5.0f;
        float[] colors = new float[pts.size() * 4];
        float[] out = new float[pts.size() * 2 * com.glowtrack.client.render.TrackRibbon.STRIDE];
        int verts = com.glowtrack.client.render.TrackRibbon.build(pts, true, 0, width, colors, out);
        check("带子顶点数 = 4（2 点 × L/R）", verts == 4, "verts=" + verts);
        // L0=(x-2.5, z), R0=(x+2.5, z)：宽度沿 Z 展开（直线沿 X，法线是 Z 向）
        float dzL = out[0 * 7 + 2], dzR = out[1 * 7 + 2];
        float w = Math.abs(dzL - dzR);
        check("带子宽度 = 5.0（几何展开）", Math.abs(w - width) < 0.01f, "width=" + w);
        // Y 一致
        check("带子 Y 传递正确", out[1] == 64.0f && out[1 + 7] == 64.0f, "y=" + out[1]);
    }

    /** 拐角连续性：L 形折线的角点两侧偏移应该衔接（法线用前后段平均）。 */
    private static void ribbonJointsAreContinuous() {
        var dim = net.minecraft.resources.ResourceLocation.parse("minecraft:overworld");
        java.util.List<com.glowtrack.track.TrackPoint> pts = java.util.List.of(
                new TrackPoint(1000, 0, 64, 0, dim, 0, 0, 0),
                new TrackPoint(2000, 10, 64, 0, dim, 0, 0, 0),    // → 沿 +X
                new TrackPoint(3000, 10, 64, 10, dim, 0, 0, 0)); // ↓ 拐角后沿 +Z
        float[] colors = new float[pts.size() * 4];
        float[] out = new float[pts.size() * 2 * com.glowtrack.client.render.TrackRibbon.STRIDE];
        int verts = com.glowtrack.client.render.TrackRibbon.build(pts, true, 0, 5.0f, colors, out);
        check("L 形折线顶点数 = 6", verts == 6, "verts=" + verts);
        // 角点（i=1，坐标 10,64,0）：法线 = 两段方向平均 (1,0)+(0,1) → (0.707,0.707)，法线=(-0.707,0.707)
        // 顶点存的是绝对坐标，需减去角点原点再验证偏移量与方向
        int corner = 2 * 7;   // 第 2 个点（角点）的 L 顶点
        float ox = out[corner] - 10.0f, oz = out[corner + 2] - 0.0f;
        float len = (float) Math.sqrt(ox * ox + oz * oz);
        check("角点法线保持归一", Math.abs(len - 2.5f) < 0.02f, "offset=" + len);
        // 方向：法线应指向 (-x, +z) 象限（两段方向 (1,0)、(0,1) 平均后取垂线）
        check("角点法线方向正确（-x,+z）", ox < -1.0f && oz > 1.0f, "offset=(" + ox + "," + oz + ")");
    }

    /** 导入含非法维度行的 CSV：坏行必须被跳过，不能造出 dimension=null 的点。 */
    private static void nullDimensionCsvIsSkipped() throws Exception {
        Path dir = Files.createTempDirectory("gt-selftest");
        TrackStore store = new TrackStore(dir);
        String csv = String.join("\n",
                "time,x,y,z,dim,speed,yaw,pitch",
                "1000,1,2,3,minecraft:overworld,0.5,0,0",
                "2000,4,5,6,a b,0.5,0,0",            // 非法维度：tryParse 返回 null
                "3000,7,8,9,minecraft:the_nether,0.5,0,0");
        int n = store.importCsv(csv, UUID.randomUUID(), "Tester");
        check("CSV 导入跳过非法维度行", n == 2, "imported=" + n + "（期望 2）");
    }

    /** dimension=null 的点不能进存储：toJson 时 p.dimension().toString() 会 NPE。 */
    private static void nullDimensionDoesNotBreakExport() throws Exception {
        Path dir = Files.createTempDirectory("gt-selftest");
        TrackStore store = new TrackStore(dir);
        UUID id = UUID.randomUUID();
        // 直接构造一个 null 维度的点，模拟历史脏数据
        PlayerTrack t = store.trackFor(id, "x");
        t.load(java.util.List.of(new TrackPoint(1000L, 1, 2, 3, null, 0f, 0f, 0f)));
        String json = store.exportJson(t);   // 修复前：这里 NPE
        check("导出不因 null 维度崩溃", json.contains("\"d\""), "json 长度=" + json.length());
    }

    /** append 必须保持时间升序（同毫秒替换旧点，更早的点拒绝）。 */
    private static void trackPointAppendKeepsTimeOrder() {
        PlayerTrack t = new PlayerTrack(UUID.randomUUID(), "n", "2026-10-20");
        check("首个点可加入", t.append(pt(1000L)));
        check("同毫秒重复点替换旧点（不算新点）", t.append(pt(1000L)) && t.size() == 1, "size=" + t.size());
        check("更早的点被拒", !t.append(pt(500L)));
        check("更晚的点可加入", t.append(pt(2000L)));
        check("最终点数正确", t.size() == 2, "size=" + t.size());
    }

    /** 维度切换必定触发采样（轨迹跨维度时不能断线）。 */
    private static void samplerRecordsOnDimensionChange() {
        Sampler s = new Sampler();
        s.shouldSample(0L, 0L, 0, 0, 0, "minecraft:overworld");
        boolean sampled = s.shouldSample(1L, 50L, 0.1, 0, 0.1, "minecraft:the_nether");
        check("维度切换必定采样", sampled, "sampled=" + sampled);
    }

    /**
     * 平滑弯道也必须触发补点。
     *
     * <p>修复前 lastHeading 被"上次采样点朝向"和"上一 tick 朝向"两个用途共用，
     * 每 tick 覆盖后比较的其实是 tick↔tick 的朝向差——每 tick 只转 5° 的
     * 平滑弯道（累计转了 30°，超过默认 25° 阈值）永远触发不了补点，
     * 轨迹会在弯道处被拉成一条直线。</p>
     */
    private static void samplerDetectsSmoothTurn() {
        Sampler s = new Sampler();
        String dim = "minecraft:overworld";
        // 起点：沿 +X
        s.shouldSample(0L, 0L, 0, 64, 0, dim);
        // 每 tick 转 10°，走 1 格（累计转向 60°，超过默认 25° 阈值）
        // 采样间隔 20 tick，这几 tick 内不会触发时间条件；
        // 位移 1 格 < 默认最小位移 0；只有"转向补点"能触发。
        int samples = 0;
        double x = 0, z = 0;
        for (int i = 1; i <= 8; i++) {
            double ang = Math.toRadians(i * 10.0);
            x += Math.cos(ang);
            z += Math.sin(ang);
            if (s.shouldSample(i, i * 50L, x, 64, z, dim)) samples++;
        }
        check("平滑弯道触发补点（累计转向超阈值）", samples >= 1, "samples=" + samples);
    }

    private static TrackPoint pt(long t) {
        return new TrackPoint(t, 0, 0, 0,
                net.minecraft.resources.ResourceLocation.parse("minecraft:overworld"), 0f, 0f, 0f);
    }

    private static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("  PASS " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name + "  " + detail);
        }
    }

    private GlowTrackSelfTest() {}
}
