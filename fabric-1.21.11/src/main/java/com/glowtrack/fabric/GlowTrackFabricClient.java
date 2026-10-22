package com.glowtrack.fabric;

import com.glowtrack.client.TrackRenderer;
import com.glowtrack.client.ActionRenderer;
import com.glowtrack.client.ClientEvents;
import com.glowtrack.network.ClientPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/**
 * GlowTrack 的 Fabric 客户端入口（1.21.11）。
 *
 * <p>把母版里三个 {@code @EventBusSubscriber(value = Dist.CLIENT)} 类的订阅
 * 换成 Fabric 的显式注册：</p>
 * <ul>
 *   <li>{@code ClientEvents} → 按键绑定 + tick 采样 + 退服清理</li>
 *   <li>{@code TrackRenderer} / {@code ActionRenderer} → 世界渲染事件</li>
 *   <li>{@code ClientPayloads} → S-&gt;C 包接收器</li>
 * </ul>
 */
public final class GlowTrackFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // S->C 包接收器
        ClientPayloads.register();

        // 按键绑定（打开追踪界面）
        KeyBindingHelper.registerKeyBinding(ClientEvents.OPEN_GUI);

        // 客户端 tick：开关界面 + 本地降级采样 + 回放推进
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ClientEvents.onClientTick());

        // 世界渲染：轨迹带 + 行为事件标记
        WorldRenderEvents.AFTER_ENTITIES.register(TrackRenderer::onRenderLevel);
        WorldRenderEvents.AFTER_ENTITIES.register(ActionRenderer::onRenderLevel);

        // 客户端命令 /gt、/glowtrack
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT
                .register((com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> dispatcher,
                           net.minecraft.commands.CommandBuildContext context) ->
                        com.glowtrack.common.TrackCommands.register(dispatcher, context));

        // 退出游戏时清空本地状态
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING
                .register(client -> ClientEvents.onLoggingOut());
    }

    private GlowTrackFabricClient() {}
}
