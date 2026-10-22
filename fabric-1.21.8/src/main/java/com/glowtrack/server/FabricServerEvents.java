package com.glowtrack.server;

import com.glowtrack.fabric.GlowTrackFabric;
import com.glowtrack.network.Payloads;
import com.glowtrack.network.ServerNet;
import com.glowtrack.track.ActionEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端事件挂载（Fabric 版）。
 *
 * <p>对应母版的三个 {@code @EventBusSubscriber} 类：
 * {@code ServerConnectionEvents} / {@code ActionListener} / {@code ServerTrackManager} 的事件方法。
 * 这里把订阅集中在一个地方。</p>
 */
public final class FabricServerEvents {

    private FabricServerEvents() {}

    /** 注册全部服务端事件。 */
    public static void register() {
        // ---- 生命周期 ----
        ServerLifecycleEvents.SERVER_STARTED.register(ServerTrackManager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerTrackManager.onServerStopping());
        ServerTickEvents.END_SERVER_TICK.register(ServerTrackManager::onServerTick);

        // ---- 玩家进服 / 退服 ----
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer sp = handler.getPlayer();
            ServerTrackManager.initIfNeeded(server);
            // 对端没装 GlowTrack 时安全跳过（optional 通道），不阻塞进服。
            ServerNet.send(sp, new Payloads.Hello(1));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (ServerTrackManager.get() != null) {
                ServerTrackManager.get().removeWatcher(handler.getPlayer().getUUID());
            }
        });

        // ---- 行为事件：方块破坏 ----
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            if (!tracked(sp)) return;
            net.minecraft.world.level.block.state.BlockState bs = world.getBlockState(pos);
            record(sp, "break", bs.getBlock().builtInRegistryHolder().getRegisteredName());
        });

        // ---- 行为事件：方块放置 ----
        // Fabric API 没有直接的"放置"事件，用 ServerPlayerEntityPlaceEvents
        // 不存在于通用 Fabric API；这里改用 USE_BLOCK 回调区分：
        // 放置方块时右键方块会触发 USE_BLOCK，且随后方块状态改变。
        // 为保证不误判，仅在"用方块右键空气之外的位置"时记 interact，
        // 放置由 break/place 的替代路径覆盖。
        // 1.21.9+ 的 UseBlockCallback 签名是 (Player, World, Hand, BlockHitResult)，
        // 不再传 state / pos；方块信息从 hit.getBlockPos() 拿。
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide()) return net.minecraft.world.InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return net.minecraft.world.InteractionResult.PASS;
            if (!tracked(sp)) return net.minecraft.world.InteractionResult.PASS;
            net.minecraft.world.level.block.state.BlockState state = world.getBlockState(hit.getBlockPos());
            record(sp, "interact", state.getBlock().builtInRegistryHolder().getRegisteredName());
            return net.minecraft.world.InteractionResult.PASS;
        });
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
