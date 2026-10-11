package com.glowtrack.client.xaero;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 配置插件：只有当 Xaero 世界地图存在时才应用其 mixin。
 *
 * <p>用 {@code Class.forName} 探测 Xaero 类是否存在（比 ModList 更早可用、更可靠），
 * 避免未装 Xaero 时 mixin 因目标类缺失而报错。</p>
 */
public class XaeroMixinPlugin implements IMixinConfigPlugin {

    private static boolean xaeroPresent() {
        try {
            Class.forName("xaero.map.gui.GuiMap");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 按 mixin 名分别门控：MixinGuiMap 需要世界地图，MixinMinimap 需要小地图
        if (mixinClassName.endsWith("MixinMinimap")) {
            return com.glowtrack.client.XaeroDetect.hasMinimap();
        }
        return com.glowtrack.client.XaeroDetect.hasWorldMap();
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                                   String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                                    String mixinClassName, IMixinInfo mixinInfo) {}
}
