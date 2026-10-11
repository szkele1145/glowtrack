package com.glowtrack.common;

import com.glowtrack.GlowTrack;
import com.glowtrack.TrackConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * 权限判定：OP 等级优先，其次权限节点（LuckPerms 兼容）。
 *
 * <p>注册一个 Boolean 权限节点 {@code glowtrack.gui}。默认解析器在没有
 * LuckPerms 等外部权限插件接管时，回退到"OP 等级达标即放行"，
 * 保证裸服务端也能用；装了 LuckPerms 后由其覆盖该节点实现细粒度授权。</p>
 */
@EventBusSubscriber(modid = GlowTrack.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class Permissions {
    private Permissions() {}

    /** 权限节点：默认解析器回退到 OP 等级判定。 */
    public static final PermissionNode<Boolean> GUI_NODE = new PermissionNode<>(
            ResourceLocation.fromNamespaceAndPath(GlowTrack.MOD_ID, "gui"),
            PermissionTypes.BOOLEAN,
            (player, node, ctx) -> {
                if (player == null) return false;
                CommandSourceStack src = player.createCommandSourceStack();
                return src.hasPermission(TrackConfig.REQUIRED_OP_LEVEL.get());
            });

    @SubscribeEvent
    public static void gatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(GUI_NODE);
    }

    /** 综合权限判定：OP 等级达标，或权限节点为真。 */
    public static boolean canUse(ServerPlayer player) {
        if (player == null) return false;
        CommandSourceStack src = player.createCommandSourceStack();
        if (src.hasPermission(TrackConfig.REQUIRED_OP_LEVEL.get())) return true;
        try {
            return Boolean.TRUE.equals(PermissionAPI.getPermission(player, GUI_NODE));
        } catch (Exception e) {
            return false;
        }
    }
}
