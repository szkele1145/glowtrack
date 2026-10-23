package com.glowtrack;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
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

    /**
     * Forge 1.20.1 的模组构造器是<b>无参</b>的（NeoForge 21.x 才注入
     * {@code IEventBus} / {@code ModContainer}）。配置注册走静态的
     * {@link ModLoadingContext}；各类的事件订阅由它们自己的
     * {@code @Mod.EventBusSubscriber} 声明，无需在这里手动挂总线。
     */
    public GlowTrack() {
        // COMMON：两端各读本地文件，不跨端同步
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TrackConfig.SPEC);
        LOGGER.info("[GlowTrack] 模组初始化完成");
    }
}
