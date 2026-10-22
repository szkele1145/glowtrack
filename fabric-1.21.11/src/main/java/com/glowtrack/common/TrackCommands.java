package com.glowtrack.common;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import com.glowtrack.client.ClientTrackState;
import com.glowtrack.client.Text;
import com.glowtrack.client.gui.TrackScreen;
import com.glowtrack.track.TrackPoint;
import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 命令入口：{@code /gt} 或 {@code /glowtrack}（客户端命令）。
 *
 * <p>母版用 NeoForge 的 {@code RegisterClientCommandsEvent}；Fabric 侧改用
 * {@code ClientCommandRegistrationCallback}，在 {@code GlowTrackFabricClient} 里注册。</p>
 *
 * <p>客户端命令只在本地执行，因此可以直接 setScreen，无需服务端往返。
 * 仍做一次本地权限预判，真正的权限由服务端在数据请求时校验。</p>
 *
 * <p>{@code /gt import <文件名>}：从 glowtrack/export/ 下的导出 JSON
 * 导入轨迹到当前会话（只进客户端的 ClientTrackState，不落服务端存储）。</p>
 */
public final class TrackCommands {

    private TrackCommands() {}

    /** 注册客户端命令。 */
    public static void register(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher,
                                 net.minecraft.commands.CommandBuildContext context) {
        dispatcher.register(ClientCommandManager.literal("gt").executes(ctx -> open()));
        dispatcher.register(ClientCommandManager.literal("glowtrack").executes(ctx -> open()));

        dispatcher.register(ClientCommandManager.literal("gt")
                .then(ClientCommandManager.literal("import")
                        .then(ClientCommandManager.argument("file",
                                        com.mojang.brigadier.arguments.StringArgumentType.string())
                                .executes(ctx -> {
                                    String file = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "file");
                                    return importTrack(file);
                                }))));
    }

    private static int open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new TrackScreen());
        }
        return 1;
    }

    /**
     * 从 glowtrack/export/<文件名> 导入轨迹到当前会话。
     *
     * <p>文件名不允许路径分隔符（防目录穿越）；JSON 结构与
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
                Identifier dim = Identifier.tryParse(e.get("d").getAsString());
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
                fail(mc, "没有有效轨迹点");
                return 0;
            }
            pts.sort(java.util.Comparator.comparingLong(TrackPoint::timeMillis));

            ClientTrackState.get().importPoints(uuid, name, pts);
            mc.player.displayClientMessage(
                    Text.tr("glowtrack.import.done", pts.size()), false);
        } catch (Exception e) {
            fail(mc, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return 1;
    }

    private static void fail(Minecraft mc, String reason) {
        mc.player.displayClientMessage(Text.tr("glowtrack.import.fail", reason), false);
    }
}
