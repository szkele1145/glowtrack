package com.glowtrack.server;

import com.glowtrack.GlowTrack;
import com.glowtrack.track.ActionEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 服务端行为事件监听：方块放置/破坏/右键交互/容器打开，写入行为事件流。
 *
 * <p>只记白名单玩家（复用 ServerTrackManager 的白名单缓存）。
 * 事件在主线程触发，记录无需加锁。</p>
 */
@EventBusSubscriber(modid = GlowTrack.MOD_ID)
public final class ActionListener {
    private ActionListener() {}

    /** 方块破坏 → "break"，target 为方块 ID。 */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        if (!tracked(sp)) return;
        record(sp, "break",
                event.getState().getBlock().builtInRegistryHolder().getRegisteredName());
    }

    /** 方块放置 → "place"，target 为方块 ID。 */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!tracked(sp)) return;
        record(sp, "place",
                event.getPlacedBlock().getBlock().builtInRegistryHolder().getRegisteredName());
    }

    /** 右键方块 → "interact"，target 为方块 ID（仅服务端记录，客户端事件跳过）。 */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!tracked(sp)) return;
        record(sp, "interact",
                event.getLevel().getBlockState(event.getPos()).getBlock().builtInRegistryHolder().getRegisteredName());
    }

    /** 容器打开 → "open"，target 为容器方块 ID（打开菜单的方块）。 */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!tracked(sp)) return;
        // target：玩家打开的菜单对应容器的方块/实体类型名，取不到则用菜单标题
        String target;
        try {
            var container = event.getContainer();
            target = container.getClass().getSimpleName();
        } catch (Throwable t) {
            target = "unknown";
        }
        record(sp, "open", target);
    }

    // ==================== 辅助 ====================

    private static boolean tracked(ServerPlayer sp) {
        ServerTrackManager m = ServerTrackManager.get();
        return m != null && m.isTracked(sp.getName().getString());
    }

    private static void record(ServerPlayer sp, String type, String target) {
        ServerTrackManager m = ServerTrackManager.get();
        if (m == null) return;
        m.recordAction(sp.getUUID(), sp.getName().getString(), new ActionEvent(
                System.currentTimeMillis(), type, sp.getUUID().toString(),
                sp.getX(), sp.getY(), sp.getZ(), target));
    }
}
