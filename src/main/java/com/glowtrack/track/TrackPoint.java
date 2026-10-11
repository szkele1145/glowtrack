package com.glowtrack.track;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 一个轨迹点：玩家在某一时刻的位置快照。
 *
 * <p>不可变对象；维度用 ResourceLocation 存储（而非 ResourceKey），
 * 以便脱离注册表进行序列化/反序列化。</p>
 *
 * @param timeMillis 采样时刻（epoch 毫秒）
 * @param x          世界坐标 X
 * @param y          世界坐标 Y
 * @param z          世界坐标 Z
 * @param dimension  维度 ID，例如 minecraft:overworld
 * @param speed      采样瞬间的移动速度（格/tick）
 * @param yaw        朝向偏航角（度）
 * @param pitch      朝向俯仰角（度）
 */
public record TrackPoint(
        long timeMillis,
        double x,
        double y,
        double z,
        ResourceLocation dimension,
        float speed,
        float yaw,
        float pitch) {

    /** 由维度 ResourceKey 推导出 ResourceLocation。 */
    public static ResourceLocation dimId(ResourceKey<Level> key) {
        return key.location();
    }

    /** 判断本点是否属于给定维度。 */
    public boolean inDimension(ResourceLocation dim) {
        return dimension.equals(dim);
    }

    /** 与另一点的欧氏距离（忽略维度差异）。 */
    public double distanceTo(TrackPoint o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
