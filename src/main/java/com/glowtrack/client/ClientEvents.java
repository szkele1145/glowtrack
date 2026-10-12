package com.glowtrack.client;

import com.glowtrack.GlowTrack;
import com.glowtrack.client.gui.TrackScreen;
import com.glowtrack.track.TrackPoint;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 客户端事件：按键绑定、打开 GUI、降级模式本地采样。 */
@EventBusSubscriber(modid = GlowTrack.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    public static final KeyMapping OPEN_GUI = new KeyMapping(
            "key.glowtrack.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.glowtrack");

    /** 本地降级采样：记录每个目标上次采样的 tick。 */
    private static final Map<UUID, Integer> lastLocalTick = new HashMap<>();

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI);
    }

    // 游戏总线：按键轮询 + 本地采样
    @EventBusSubscriber(modid = GlowTrack.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (OPEN_GUI.consumeClick()) {
                if (mc.player == null) continue;
                // 已在追踪界面时再按 K = 关闭（游戏 ESC 语义），避免重建屏幕丢搜索状态
                if (mc.screen instanceof TrackScreen) mc.setScreen(null);
                else mc.setScreen(new TrackScreen());
            }
            // 降级模式：本地采样已加载的追踪目标
            if (mc.player != null && !ClientTrackState.get().isServerMode()) {
                localSample(mc.player);
            }
            // 时间轴回放：游标自动匀速推进（回放动画）
            if (mc.player != null) {
                ClientTrackState.get().tickReplay();
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientTrackState.get().reset();
            lastLocalTick.clear();
        }
    }

    /** 本地降级采样：仅对当前追踪目标采样。 */
    private static void localSample(LocalPlayer player) {
        ClientTrackState state = ClientTrackState.get();
        UUID tracked = state.tracked();
        if (tracked == null) return;
        // 只有当追踪目标在附近（可作为实体拿到）时才记录
        var level = player.level();
        net.minecraft.world.entity.player.Player target = null;
        for (net.minecraft.world.entity.player.Player p : level.players()) {
            if (p.getUUID().equals(tracked)) { target = p; break; }
        }
        if (target == null) return;

        long now = System.currentTimeMillis();
        String dim = player.level().dimension().location().toString();
        // 本地降级模式：用客户端设置的采样间隔判定
        Integer last = lastLocalTick.get(tracked);
        if (last != null && player.tickCount - last < ClientSettings.get().sampleIntervalTicks) return;
        lastLocalTick.put(tracked, player.tickCount);

        float speed = (float) target.getDeltaMovement().horizontalDistance();
        state.localAppend(new TrackPoint(now, target.getX(), target.getY(), target.getZ(),
                player.level().dimension().location(), speed, target.getYRot(), target.getXRot()));
    }
}
