package com.glowtrack.common;

import com.glowtrack.TrackConfig;
import com.glowtrack.client.ClientTrackState;
import com.glowtrack.client.Text;
import com.glowtrack.client.gui.TrackScreen;
import com.glowtrack.track.TrackPoint;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 命令入口：/gt 或 /glowtrack —— 客户端命令。
 *
 * <p>客户端命令（RegisterClientCommandsEvent）只在本机执行，
 * 因此可以直接 setScreen，无需服务端往返。仍做一次本地权限预判，
 * 但真正权限由服务端在数据请求时校验。</p>
 *
 * <p>/gt import <文件名>：从 glowtrack/export/ 下的导出 JSON
 * 本地打开一份轨迹（只进本会话的 ClientTrackState，不进服务端存储）。</p>
 */
@EventBusSubscriber(modid = com.glowtrack.GlowTrack.MOD_ID, value = Dist.CLIENT)
public final class TrackCommands {
    private TrackCommands() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gt").executes(ctx -> open()));
        event.getDispatcher().register(Commands.literal("glowtrack").executes(ctx -> open()));

        event.getDispatcher().register(Commands.literal("gt")
                .then(Commands.literal("import")
                        .then(Commands.argument("file", StringArgumentType.string())
                                .executes(ctx -> {
                                    String file = StringArgumentType.getString(ctx, "file");
                                    return importTrack(file);
                                }))));
    }

    private static int open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new TrackScreen());
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 从 glowtrack/export/<文件名> 导入轨迹到当前会话。
     *
     * <p>文件名不允许含路径分隔符（防目录穿越）；JSON 结构与
     * exportSelected 一致：{"player":"uuid","name":"...","points":[{t,x,y,z,d,v}]}。</p>
     */
    private static int importTrack(String file) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        try {
            if (file.contains("/") || file.contains("\\") || file.contains("..")) {
                fail(mc, "非法文件名");
                return 0;
            }
            Path f = mc.gameDirectory.toPath().resolve("glowtrack").resolve("export").resolve(file);
            if (!Files.isRegularFile(f)) {
                fail(mc, "文件不存在: " + file);
                return 0;
            }

            String json = Files.readString(f, StandardCharsets.UTF_8);
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

            UUID uuid;
            try {
                uuid = UUID.fromString(root.get("player").getAsString());
            } catch (Exception e) {
                fail(mc, "缺少或非法 player 字段");
                return 0;
            }
            String name = root.has("name") ? root.get("name").getAsString() : "track";

            List<TrackPoint> pts = new ArrayList<>();
            com.google.gson.JsonArray arr = root.getAsJsonArray("points");
            if (arr == null) {
                fail(mc, "缺少 points 数组");
                return 0;
            }
            for (var el : arr) {
                com.google.gson.JsonObject e = el.getAsJsonObject();
                ResourceLocation dim = ResourceLocation.tryParse(e.get("d").getAsString());
                if (dim == null) continue;   // 无法解析的维度：跳过该点
                pts.add(new TrackPoint(
                        e.get("t").getAsLong(),
                        e.get("x").getAsDouble(),
                        e.get("y").getAsDouble(),
                        e.get("z").getAsDouble(),
                        dim,
                        e.get("v").getAsFloat(),
                        e.has("yaw") ? e.get("yaw").getAsFloat() : 0f,
                        e.has("pitch") ? e.get("pitch").getAsFloat() : 0f));
            }
            if (pts.isEmpty()) {
                fail(mc, "无有效轨迹点");
                return 0;
            }
            pts.sort(java.util.Comparator.comparingLong(TrackPoint::timeMillis));

            ClientTrackState.get().importPoints(uuid, name, pts);
            mc.player.displayClientMessage(
                    Text.tr("glowtrack.import.done", pts.size()), false);
        } catch (Exception e) {
            fail(mc, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void fail(Minecraft mc, String reason) {
        mc.player.displayClientMessage(Text.tr("glowtrack.import.fail", reason), false);
    }
}
