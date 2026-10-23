package com.glowtrack.fabric;

import com.glowtrack.TrackConfig;
import com.glowtrack.fabric.net.FabricNetwork;
import com.glowtrack.network.ServerPayloads;
import com.glowtrack.server.FabricServerEvents;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GlowTrack 的 Fabric 入口（1.21.11）。
 *
 * <p>与 NeoForge 母版的差异都在这一层：模组初始化、配置加载、网络注册、
 * 服务器事件挂载。业务逻辑（采样、存储、渲染、GUI）仍复用母版代码。</p>
 */
public final class GlowTrackFabric implements ModInitializer {

    public static final String MOD_ID = "glowtrack";
    public static final Logger LOGGER = LoggerFactory.getLogger("GlowTrack");

    @Override
    public void onInitialize() {
        // 配置：NeoForge 用 ModConfigSpec，这里换成自己读 TOML。
        TrackConfig.load();
        // 网络包注册（play 阶段 C2S / S2C）+ 服务端接收器。
        FabricNetwork.register();
        ServerPayloads.register();
        // 服务器事件：玩家进服/退服、方块交互、tick 采样。
        FabricServerEvents.register();
        LOGGER.info("[GlowTrack] 模组初始化完成（Fabric / 1.21.8）");
    }

    // Fabric 用反射实例化 entrypoint：构造器必须可访问，private 会让 mod 加载直接失败
    public GlowTrackFabric() {}
}
