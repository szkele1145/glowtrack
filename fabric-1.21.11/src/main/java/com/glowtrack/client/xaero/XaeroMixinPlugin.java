package com.glowtrack.client.xaero;

import com.glowtrack.fabric.GlowTrackFabric;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 配置插件：按目标类存在性门控 Xaero mixin 的应用。
 *
 * <p><b>v1.1.9：MixinMinimap 的注入目标从 MinimapFBORenderer 移到 MinimapProcessor。</b>
 * 原目标与 XaeroPlus 同类竞争，实测引发其接口注入失效（ClassCastException 崩渲染）。
 * MinimapProcessor 是 Xaero 渲染链最外层、XaeroPlus 未注入——小地图功能保留，冲突消除。</p>
 *
 * <p><b>兼容性关键约束</b>（v1.1.4 起）：</p>
 * <ul>
 *   <li><b>插件阶段禁止 Class.forName 探测 mod 类</b>（Sinytra Connector 提前点火时会
 *   引发 loaded too early 连锁）。资源探测只查 .class 资源存在性，不 define 类。</li>
 *   <li><b>不做永久缓存</b>：shouldApplyMixin 每次现查，防提前点火钉死错误结果。</li>
 *   <li>运行期探测走 {@link com.glowtrack.client.XaeroRuntime}（mod 构造后安全）。</li>
 * </ul>
 */
public class XaeroMixinPlugin implements IMixinConfigPlugin {

    private static boolean resourcePresent(String classResource) {
        try {
            return XaeroMixinPlugin.class.getClassLoader().getResource(classResource) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- 可观测性（防重日志 + mixin 激活标志）----
    private static boolean loggedDetect = false;
    private static final java.util.concurrent.atomic.AtomicBoolean MM_ACTIVATED =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean WM_ACTIVATED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** mixin 注入体首行调用：第一次真正执行时打一条激活日志（证明注入+运行都成功）。 */
    public static void markActivated(String className) {
        java.util.concurrent.atomic.AtomicBoolean flag =
                className.endsWith("MixinMinimap") ? MM_ACTIVATED : WM_ACTIVATED;
        if (flag != null && flag.compareAndSet(false, true)) {
            com.glowtrack.fabric.GlowTrackFabric.LOGGER.info("[GlowTrack] Xaero 轨迹叠加已激活（{}）", className);
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 每次现查（无缓存），防兼容层提前点火钉死错误结果
        boolean mm = mixinClassName.endsWith("MixinMinimap");
        boolean present = resourcePresent(mm
                ? "xaero/common/minimap/MinimapProcessor.class"
                : "xaero/map/gui/GuiMap.class");
        if (!loggedDetect) {
            loggedDetect = true;
            com.glowtrack.fabric.GlowTrackFabric.LOGGER.info("[GlowTrack] Xaero 检测{}，将{} {}",
                    present ? "成功" : "失败（功能置灰）", present ? "应用" : "跳过", mixinClassName);
        }
        return present;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass,
                                   String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass,
                                    String mixinClassName, IMixinInfo mixinInfo) {}
}
