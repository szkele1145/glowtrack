package com.glowtrack.client;

import com.glowtrack.client.gui.TrackScreen;
import com.glowtrack.track.TrackPoint;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端事件：按键绑定、打开 GUI、降级模式本地采样。
 *
 * <p>母版用 NeoForge 的 {@code @EventBusSubscriber(value = true)} +
 * {@code RegisterKeyMappingsEvent} / {@code ClientTickEvent.Post} /
 * {@code ClientPlayerNetworkEvent.LoggingOut}；这里改成由
 * {@link com.glowtrack.fabric.GlowTrackFabricClient} 显式调用。</p>
 */
public final class ClientEvents {

    /** 打开追踪界面的按键。 */
    public static final KeyMapping OPEN_GUI = new KeyMapping(
            "key.glowtrack.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
            "key.categories.glowtrack");

    /** 本地降级采样：记录每个目标上一次采样的 tick。 */
    private static final Map<UUID, Integer> lastLocalTick = new HashMap<>();

    private ClientEvents() {}

    /**
     * 游戏主循环 tick：按键开关界面 + 本地降级采样 + 回放推进。
     *
     * <p>母版挂在 {@code ClientTickEvent.Post}（tick 末尾），这里挂在
     * {@code ClientTickEvents.END_CLIENT_TICK}，语义一致。</p>
     */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_GUI.consumeClick()) {
            if (mc.player == null) continue;
            // 已在追踪界面时再按 K = 关闭（常规 ESC 语义），避免重建屏幕丢失状态
            if (mc.screen instanceof TrackScreen) mc.setScreen(null);
            else mc.setScreen(new TrackScreen());
        }
        // 降级模式：本地采样已加载的追踪目标
        if (mc.player != null && !ClientTrackState.get().isServerMode()) {
            localSample(mc.player);
        }
        // 时间轴回放：目标自动匀速推进（回放动画）
        if (mc.player != null) {
            ClientTrackState.get().tickReplay();
        }
    }

    /** 退出游戏：清空本地状态。 */
    public static void onLoggingOut() {
        ClientTrackState.get().reset();
        lastLocalTick.clear();
    }

    /** 本地降级采样：仅对当前追踪目标采样。 */
    private static void localSample(LocalPlayer player) {
        ClientTrackState state = ClientTrackState.get();
        UUID tracked = state.tracked();
        if (tracked == null) return;
        // 只有追踪目标在附近（可能是实体追到了）时才记录
        var level = player.level();
        Player target = null;
        for (Player p : level.players()) {
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
