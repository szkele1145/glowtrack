package com.glowtrack.track;

import com.glowtrack.TrackConfig;

/**
 * 采样策略判定：决定某个 tick 是否需要记录一个轨迹点。
 *
 * <p>三条准则任一命中即记录：</p>
 * <ol>
 *   <li>距离上次采样已间隔 ≥ 配置的采样间隔（保底，保证匀速时也有点）</li>
 *   <li>自上次采样以来位移 ≥ 配置的最小记录位移（跳跃/疾跑时更密）</li>
 *   <li>轨迹方向相对上一段发生明显偏转 ≥ 拐弯阈值（拐弯处补点，让轨迹贴合真实走位）</li>
 * </ol>
 *
 * <p>每个被追踪玩家持有一个独立实例。</p>
 *
 * <p><b>朝向基准</b>：转向判定比较的是「本次位移方向」与「上次采样时的位移方向」。
 * 两者都是"从上一个采样点指向当前点"的段方向——也就是轨迹的切线方向。
 * 这样平滑弯道（每 tick 转 5°、10 个 tick 累计转了 50°）在累计超过阈值时
 * 一定会触发补点，而不会像"tick↔tick 比较"那样永远测不出来。</p>
 */
public final class Sampler {
    private long lastTick = -1;
    private long lastTime = -1;
    /** 上次采样点的坐标（位移与段方向的基准）。 */
    private double lastX, lastY, lastZ;
    private String lastDim;
    /** 上次采样时的段方向（度）；NaN = 还没有可比基线。 */
    private double refHeading = Double.NaN;

    /** 重置状态（开始追踪时调用）。 */
    public void reset() {
        lastTick = -1;
        lastTime = -1;
        lastDim = null;
        refHeading = Double.NaN;
    }

    /**
     * 判定当前是否应采样。
     *
     * @param nowTick   当前世界 tick
     * @param timeMillis 当前真实时间（毫秒）
     * @param x,y,z     当前坐标
     * @param dimId     当前维度 ID 字符串
     * @return true 表示应记录一个点
     */
    public boolean shouldSample(long nowTick, long timeMillis,
                                double x, double y, double z, String dimId) {
        // 维度切换必定记录；新维度的朝向基线从头积累（不与旧维度比较）
        if (lastDim != null && !lastDim.equals(dimId)) {
            update(nowTick, timeMillis, x, y, z, dimId, Double.NaN);
            return true;
        }
        // 首个点
        if (lastTick < 0) {
            update(nowTick, timeMillis, x, y, z, dimId, Double.NaN);
            return true;
        }

        boolean byTime = (nowTick - lastTick) >= TrackConfig.sampleIntervalTicks();
        boolean byDist = false;
        boolean byTurn = false;

        // 本次的段方向：从上次采样点指向当前位置
        double segDx = x - lastX, segDy = y - lastY, segDz = z - lastZ;
        double segHeading = Double.NaN;
        if (segDx != 0 || segDz != 0) segHeading = Math.toDegrees(Math.atan2(segDz, segDx));

        if (lastDim != null && lastDim.equals(dimId)) {
            double distSq = segDx * segDx + segDy * segDy + segDz * segDz;
            double min = TrackConfig.minMoveDistance();
            if (min > 0 && distSq >= min * min) byDist = true;

            // 转向判定：本次段方向 vs 上次采样时的段方向
            if (!Double.isNaN(segHeading)) {
                if (Double.isNaN(refHeading)) {
                    // 还没有可比基线（刚采样完）：建立基线，不算转向
                    refHeading = segHeading;
                } else {
                    double diff = Math.abs(segHeading - refHeading);
                    if (diff > 180) diff = 360 - diff;
                    if (diff >= TrackConfig.turnAngleThreshold()) byTurn = true;
                }
            }
        }

        if (byTime || byDist || byTurn) {
            // 采样后：本次段方向成为新的比较基线
            update(nowTick, timeMillis, x, y, z, dimId, segHeading);
            return true;
        }
        return false;
    }

    private void update(long tick, long time, double x, double y, double z,
                        String dim, double segHeading) {
        lastTick = tick;
        lastTime = time;
        lastX = x; lastY = y; lastZ = z;
        lastDim = dim;
        // 段方向为 NaN（没动过）时保持原基线
        if (!Double.isNaN(segHeading)) refHeading = segHeading;
    }
}
