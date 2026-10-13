package com.glowtrack.track;

/**
 * 一个行为事件：白名单玩家在某一时刻的一次方块操作/交互/开容器。
 *
 * <p>与 {@link TrackPoint} 轨迹流完全独立，互不污染。</p>
 *
 * @param t        事件时刻（epoch 毫秒）
 * @param type     事件类型：place / break / interact / open
 * @param playerId 玩家 UUID 字符串（序列化用；网络层传 UUID 由包承载）
 * @param x        世界坐标 X
 * @param y        世界坐标 Y
 * @param z        世界坐标 Z
 * @param target   目标标识：方块 ID（如 minecraft:stone）、容器类型（如 minecraft:chest）
 *                 或交互物 ID
 */
public record ActionEvent(
        long t,
        String type,
        String playerId,
        double x,
        double y,
        double z,
        String target) {
}
