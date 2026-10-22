package com.glowtrack.track;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个玩家的一条轨迹：按时间升序的点序列 + 元信息。
 *
 * <p>轨迹按“自然日”切分存储，所以一条 PlayerTrack 代表某玩家某一天的轨迹。
 * 点列表 append 时保证时间单调递增。</p>
 */
public final class PlayerTrack {
    private final java.util.UUID player;
    private final String name;
    private final String date;          // yyyy-MM-dd
    private final List<TrackPoint> points = new ArrayList<>();

    public PlayerTrack(java.util.UUID player, String name, String date) {
        this.player = player;
        this.name = name;
        this.date = date;
    }

    public java.util.UUID player() { return player; }
    public String name() { return name; }
    public String date() { return date; }
    public int size() { return points.size(); }
    public boolean isEmpty() { return points.isEmpty(); }

    /** 不可变视图，避免外部直接改动内部列表。 */
    public List<TrackPoint> points() { return Collections.unmodifiableList(points); }

    public TrackPoint first() { return points.get(0); }
    public TrackPoint last() { return points.get(points.size() - 1); }

    public long firstTime() { return points.get(0).timeMillis(); }
    public long lastTime() { return points.get(points.size() - 1).timeMillis(); }

    /**
     * 追加一个点。仅当时间不早于最后一个点时才加入（保证升序）。
     *
     * <p>同一毫秒到达的点：<b>替换</b>最后一个点而不是丢弃——同一毫秒里的后一个点
     * 才是玩家当时真正的位置（采样时序上更靠后），丢弃它会让轨迹停在旧位置。
     * 时间早于最后一个点则拒绝（乱序包，直接丢弃）。</p>
     *
     * @return 是否成功加入（含"替换同毫秒旧点"的情况）
     */
    public boolean append(TrackPoint p) {
        if (points.isEmpty()) {
            points.add(p);
            return true;
        }
        long last = lastTime();
        if (p.timeMillis() < last) return false;          // 乱序：直接丢弃
        if (p.timeMillis() == last) {
            // 同毫秒：位置更新，替换而非追加（避免零距离重复点污染存储与网络流）
            points.set(points.size() - 1, p);
            return true;
        }
        points.add(p);
        return true;
    }

    /** 批量追加（假定已按时间升序）。返回实际加入数量。 */
    public int appendAll(List<TrackPoint> list) {
        int n = 0;
        for (TrackPoint p : list) if (append(p)) n++;
        return n;
    }

    /** 直接装载点（用于反序列化，跳过单调校验但保持顺序）。 */
    public void load(List<TrackPoint> list) {
        points.clear();
        points.addAll(list);
    }

    /** 裁剪掉早于 keepAfterMillis 的点（用于保留策略）。 */
    public void pruneBefore(long keepAfterMillis) {
        int i = 0;
        while (i < points.size() && points.get(i).timeMillis() < keepAfterMillis) i++;
        if (i > 0) points.subList(0, i).clear();
    }

    /** 内存点数上限裁剪（保留最新的）。 */
    public void trimToSize(int max) {
        if (points.size() > max) points.subList(0, points.size() - max).clear();
    }

    /** 二分查找：返回第一个 timeMillis >= t 的点下标，越界返回 size()。 */
    public int lowerBound(long t) {
        int lo = 0, hi = points.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (points.get(mid).timeMillis() < t) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** 找到时间最接近 t 的点（用于传送定位）。没有点时返回 null。 */
    public TrackPoint nearest(long t) {
        if (points.isEmpty()) return null;
        int i = lowerBound(t);
        if (i <= 0) return points.get(0);
        if (i >= points.size()) return points.get(points.size() - 1);
        TrackPoint a = points.get(i - 1), b = points.get(i);
        return (t - a.timeMillis() <= b.timeMillis() - t) ? a : b;
    }

    /** 该轨迹涉及的维度集合（去重）。null 维度不计入——它不是有效维度。 */
    public java.util.Set<Identifier> dimensions() {
        java.util.Set<Identifier> set = new java.util.LinkedHashSet<>();
        for (TrackPoint p : points) {
            if (p.dimension() != null) set.add(p.dimension());
        }
        return set;
    }

    /** 总路程（相邻点距离之和，跨维度段不计）。 */
    public double totalDistance() {
        double d = 0;
        for (int i = 1; i < points.size(); i++) {
            TrackPoint a = points.get(i - 1), b = points.get(i);
            if (a.inDimension(b.dimension())) d += a.distanceTo(b);
        }
        return d;
    }
}
