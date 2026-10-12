package com.glowtrack.track;

import com.glowtrack.TrackConfig;

/**
 * 采样策略判定：决定某个 tick 是否需要记录一个轨迹点。
 *
 * <p>三条准则任一命中即记录：</p>
 * <ol>
 *   <li>距离上次采样已间隔 ≥ 配置的采样间隔（保底，保证匀速时也有点）</li>
 *   <li>自上次采样以来位移 ≥ 配置的最小记录位移（跳跃/疾跑时更密）</li>
 *   <li>移动方向相对上次发生明显偏转 ≥ 拐弯阈值（拐弯处补点，让轨迹贴合真实走位）</li>
 * </ol>
 *
 * <p>每个被追踪玩家持有一个独立实例。</p>
 */
public final class Sampler {
    private long lastTick = -1;
    private long lastTime = -1;
    private double lastX, lastY, lastZ;
    private String lastDim;
    private double lastHeading = Double.NaN;

    /** 重置状态（开始追踪时调用）。 */
    public void reset() {
        lastTick = -1;
        lastTime = -1;
        lastDim = null;
        lastHeading = Double.NaN;
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
        // 维度切换必定记录
        if (lastDim != null && !lastDim.equals(dimId)) {
            update(nowTick, timeMillis, x, y, z, dimId, Double.NaN);
            return true;
        }
        if (lastTick < 0) { // 首个点
            update(nowTick, timeMillis, x, y, z, dimId, Double.NaN);
            return true;
        }

        boolean byTime = (nowTick - lastTick) >= TrackConfig.SAMPLE_INTERVAL_TICKS.get();
        boolean byDist = false;
        boolean byTurn = false;

        if (lastDim != null && lastDim.equals(dimId)) {
            double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            double min = TrackConfig.MIN_MOVE_DISTANCE.get();
            if (min > 0 && distSq >= min * min) byDist = true;

            if (dx != 0 || dz != 0) {
                double heading = Math.toDegrees(Math.atan2(dz, dx));
                if (!Double.isNaN(lastHeading)) {
                    double diff = Math.abs(heading - lastHeading);
                    if (diff > 180) diff = 360 - diff;
                    if (diff >= TrackConfig.TURN_ANGLE_THRESHOLD.get()) byTurn = true;
                }
                lastHeading = heading;
            }
        }

        if (byTime || byDist || byTurn) {
            update(nowTick, timeMillis, x, y, z, dimId, lastHeading);
            return true;
        }
        return false;
    }

    private void update(long tick, long time, double x, double y, double z,
                        String dim, double heading) {
        lastTick = tick;
        lastTime = time;
        lastX = x; lastY = y; lastZ = z;
        lastDim = dim;
        lastHeading = heading;   // NaN = 重置（维度切换/首点），新维度方向从零积累，避免与旧维度 heading 误比
    }
}
