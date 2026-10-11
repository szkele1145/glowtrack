package com.glowtrack.common;

import com.glowtrack.TrackConfig;
import com.glowtrack.client.gui.TrackScreen;
import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * 命令入口：/gt 或 /glowtrack —— 客户端命令，直接打开追踪界面。
 *
 * <p>客户端命令（RegisterClientCommandsEvent）只在本机执行，
 * 因此可以直接 setScreen，无需服务端往返。仍做一次本地权限预判，
 * 但真正权限由服务端在数据请求时校验。</p>
 */
@EventBusSubscriber(modid = com.glowtrack.GlowTrack.MOD_ID, value = Dist.CLIENT)
public final class TrackCommands {
    private TrackCommands() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gt").executes(ctx -> open()));
        event.getDispatcher().register(Commands.literal("glowtrack").executes(ctx -> open()));
    }

    private static int open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new TrackScreen());
        }
        return Command.SINGLE_SUCCESS;
    }
}
