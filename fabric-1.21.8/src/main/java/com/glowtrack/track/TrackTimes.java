package com.glowtrack.track;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 时间格式化与轨迹点查找的公共工具。
 *
 * <p>全模组时间显示统一为 {@code HH:mm:ss}；SimpleDateFormat 非线程安全，
 * 这里每个调用线程各取一份（ThreadLocal），避免每帧 new 的开销。</p>
 */
public final class TrackTimes {
    private static final ThreadLocal<SimpleDateFormat> HMS =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm:ss"));

    private TrackTimes() {}

    /** 统一格式：时:分:秒。 */
    public static String hms(long timeMillis) {
        return HMS.get().format(new Date(timeMillis));
    }

    /** 二分查找时间最接近 t 的点（列表须按 timeMillis 升序）。空列表返回 null。 */
    public static TrackPoint nearestByTime(List<TrackPoint> pts, long t) {
        if (pts.isEmpty()) return null;
        int lo = 0, hi = pts.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (pts.get(mid).timeMillis() < t) lo = mid + 1; else hi = mid;
        }
        if (lo > 0 && Math.abs(pts.get(lo - 1).timeMillis() - t)
                <= Math.abs(pts.get(lo).timeMillis() - t)) lo--;
        return pts.get(lo);
    }
}
