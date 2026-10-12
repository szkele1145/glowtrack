package com.glowtrack.client.xaero;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 配置插件：按目标类存在性门控 Xaero mixin 的应用。
 *
 * <p><b>兼容性关键约束</b>（v1.1.4 起）：</p>
 * <ul>
 *   <li><b>禁止在插件阶段用 {@code Class.forName} 探测 mod 类</b>。
 *   Mixin 插件在 prepare 阶段实例化，早于 mod 类加载器就绪；
 *   Sinytra Connector 等兼容层还会更早"点火"全部 mixin config——
 *   此时 Class.forName 可能触发目标类被非预期类加载器提前 define，
 *   连锁导致其他 mixin 到原版类的 mod 报 {@code loaded too early}。
 *   插件阶段改用 <b>资源探测</b>：在类路径上找 Xaero jar 的 marker 资源文件
 *   （{@code xaero.map.misc.Events} 的类文件存在性通过
 *   {@code getClass().getClassLoader().getResource()} 检查——只查资源不 define 类）。</li>
 *   <li><b>不做永久缓存</b>：{@code shouldApplyMixin} 每次调用现查资源（开销为一次
 *   classloader 资源查找，微秒级），避免 Connector 提前点火时把错误结果钉死。</li>
 *   <li>运行期（GUI / mixin 体内）的探测改走 {@link XaeroRuntime}，
 *   它在 mod 构造完成后才首次调用，此时可安全用 {@code Class.forName}。</li>
 * </ul>
 */
public class XaeroMixinPlugin implements IMixinConfigPlugin {

    /**
     * 资源探测：只查资源存在性，绝不触发类加载。
     * Xaero's World Map 的 marker：xaero/map/gui/GuiMap.class 资源。
     * Xaero's Minimap 的 marker：xaero/common/minimap/render/MinimapFBORenderer.class 资源。
     */
    private static boolean resourcePresent(String classResource) {
        try {
            return XaeroMixinPlugin.class.getClassLoader().getResource(classResource) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 按目标类资源分别门控；每次现查（无缓存），防兼容层提前点火钉死错误结果
        if (mixinClassName.endsWith("MixinMinimap")) {
            return resourcePresent("xaero/common/minimap/render/MinimapFBORenderer.class");
        }
        return resourcePresent("xaero/map/gui/GuiMap.class");
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
