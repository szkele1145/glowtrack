package com.glowtrack;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 平台路径工具（Fabric 版）。
 *
 * <p>对应 NeoForge 母版里的 {@code FMLPaths.GAMEDIR} / {@code FMLPaths.CONFIGDIR}。
 * 用 {@code FabricLoader.getGameDir()} / {@code getConfigDir()} 拿，
 * 这样配置文件和导出文件的落盘位置与 NeoForge 版一致。</p>
 *
 * <p>容错：脱离游戏环境运行（例如零依赖自检）时，FabricLoader 可能还没初始化，
 * 路径会是 null 或抛异常。此时兜底到临时目录，保证静态初始化不炸。</p>
 */
public final class FabricPaths {

    private FabricPaths() {}

    /** 游戏目录（对应 FMLPaths.GAMEDIR）。 */
    public static Path gameDir() {
        try {
            Path p = FabricLoader.getInstance().getGameDir();
            if (p != null) return p;
        } catch (Throwable ignored) {}
        return fallback("game");
    }

    /** 配置目录（对应 FMLPaths.CONFIGDIR）。 */
    public static Path configDir() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir();
            if (p != null) return p;
        } catch (Throwable ignored) {}
        return fallback("config");
    }

    /** 脱离游戏环境时的兜底目录。 */
    private static Path fallback(String kind) {
        return Paths.get(System.getProperty("java.io.tmpdir", "."), "glowtrack", kind);
    }
}
