package com.glowtrack.common;

import com.glowtrack.GlowTrack;
import com.glowtrack.TrackConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

/**
 * 权限判定：OP 等级优先，其次权限节点（LuckPerms 兼容）。
 *
 * <p>注册一个 Boolean 权限节点 {@code glowtrack.gui}。默认解析器在没有
 * LuckPerms 等外部权限插件接管时，回退到"OP 等级达标即放行"，
 * 保证裸服务端也能用；装了 LuckPerms 后由其覆盖该节点实现细粒度授权。</p>
 */
@EventBusSubscriber(modid = GlowTrack.MOD_ID)   // PermissionGatherEvent 是 FORGE 总线事件，不能挂 MOD bus
public final class Permissions {
    private Permissions() {}

    /** 权限节点：默认解析器回退到 OP 等级判定。 */
    public static final PermissionNode<Boolean> GUI_NODE = new PermissionNode<>(
            new ResourceLocation(GlowTrack.MOD_ID, "gui"),
            PermissionTypes.BOOLEAN,
            (player, node, ctx) -> {
                if (player == null) return false;
                CommandSourceStack src = player.createCommandSourceStack();
                return src.hasPermission(TrackConfig.requiredOpLevel());
            });

    @SubscribeEvent
    public static void gatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(GUI_NODE);
    }

    /** 综合权限判定：OP 等级达标，或（配置启用节点检查时）权限节点为真。 */
    public static boolean canUse(ServerPlayer player) {
        if (player == null) return false;
        CommandSourceStack src = player.createCommandSourceStack();
        if (src.hasPermission(TrackConfig.requiredOpLevel())) return true;
        String node = TrackConfig.permissionNode();
        if (node == null || node.isBlank()) return false;   // 留空 = 仅按 OP 等级
        try {
            return Boolean.TRUE.equals(PermissionAPI.getPermission(player, GUI_NODE));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 修改全局配置的权限判定（改采样间隔 / 保留天数）。
     *
     * <p><b>刻意比 {@link #canUse} 严</b>：{@code canUse} 的语义是"能打开追踪
     * 界面看别人的轨迹"，设计上是允许通过 LuckPerms 授权给非 OP 玩家的；
     * 而采样间隔与保留天数是<b>影响全服所有玩家</b>的设置——被授权的普通
     * 玩家若能改，把 retentionDays 改成 1 就等于清空所有人的历史轨迹。</p>
     *
     * <p>因此这里只认 OP 等级（默认要求 2 级），不查 LuckPerms 节点。</p>
     */
    public static boolean canModifyGlobalSettings(ServerPlayer player) {
        if (player == null) return false;
        CommandSourceStack src = player.createCommandSourceStack();
        return src.hasPermission(TrackConfig.settingsOpLevel());
    }
}
