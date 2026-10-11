package com.glowtrack;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GlowTrack —— 玩家移动轨迹追踪模组。
 *
 * <p>核心能力：服务端记录白名单玩家轨迹、OP 通过 GUI 按需追踪并回看、
 * 世界内实时渲染发光立体轨迹线、时间轴回看 + 一键传送、Xaero 地图联动。</p>
 */
@Mod(GlowTrack.MOD_ID)
public final class GlowTrack {
    public static final String MOD_ID = "glowtrack";
    public static final Logger LOGGER = LoggerFactory.getLogger("GlowTrack");

    public GlowTrack(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, TrackConfig.SPEC);
        LOGGER.info("[GlowTrack] 模组初始化完成");
    }
}
