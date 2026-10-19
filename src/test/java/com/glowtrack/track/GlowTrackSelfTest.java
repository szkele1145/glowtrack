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

        System.out.println(failed == 0
                ? "OK: all glowtrack self-tests passed (" + passed + ")"
                : "FAILED: " + failed + " check(s) failed");
        if (failed > 0) {
            System.exit(1);
        }
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

    /** append 必须保持时间严格升序（同毫秒也拒绝）。 */
    private static void trackPointAppendKeepsTimeOrder() {
        PlayerTrack t = new PlayerTrack(UUID.randomUUID(), "n", "2026-10-20");
        check("首个点可加入", t.append(pt(1000L)));
        check("同毫秒重复点被拒", !t.append(pt(1000L)));
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
