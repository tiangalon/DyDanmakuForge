package top.tiangalon.dydanmakuforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import top.tiangalon.dydanmakuforge.config.ConfigManager;
import top.tiangalon.dydanmakuforge.config.ConfigManager.MessageType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static top.tiangalon.dydanmakuforge.DyDanmakuForge.LOGGER;

public final class DyDanmakuForgeClient {
    public static final DyDanmakuController CONTROLLER = new DyDanmakuController();
    public static final KeyMapping OPEN_GUI = new KeyMapping(
            "key.dydanmaku.gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "key.category.dydanmaku.dydanmakukey"
    );

    private DyDanmakuForgeClient() {
    }

    public static void register(IEventBus modEventBus) {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("dydanmaku");
        try {
            Files.createDirectories(configDir);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建 DyDanmaku 配置目录", exception);
        }

        ClientRuntime.initialize(
                configDir,
                DyDanmakuForgeClient::sendChatMessage,
                DyDanmakuScreen::setAvatar
        );
        ConfigManager.createDefaultConfig(configDir.toString());

        modEventBus.addListener(DyDanmakuForgeClient::registerKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(DyDanmakuForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(DyDanmakuForgeClient::registerClientCommands);
        LOGGER.info("[DyDanmaku]配置目录：{}", configDir.toAbsolutePath());
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !OPEN_GUI.consumeClick()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DyDanmakuScreen) {
            minecraft.setScreen(null);
        } else {
            minecraft.setScreen(new DyDanmakuScreen(CONTROLLER));
        }
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("dydanmaku")
                        .executes(context -> {
                            ClientRuntime.output(
                                    "[DyDanmaku]用法：/dydanmaku <connect|disconnect|status|filter|unfilter>");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("connect")
                                .then(Commands.argument("live_id", StringArgumentType.string())
                                        .executes(context -> {
                                            CONTROLLER.connect(StringArgumentType.getString(context, "live_id"));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(Commands.literal("disconnect")
                                .executes(context -> {
                                    CONTROLLER.disconnect();
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    CONTROLLER.status();
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("filter")
                                .executes(context -> showAllowedMessageTypes())
                                .then(Commands.literal("status")
                                        .executes(context -> showAllowedMessageTypes()))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestMessageTypes(builder))
                                        .executes(context -> setMessageTypeEnabled(
                                                StringArgumentType.getString(context, "type"), false))))
                        .then(Commands.literal("unfilter")
                                .executes(context -> showAllowedMessageTypes())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestMessageTypes(builder))
                                        .executes(context -> setMessageTypeEnabled(
                                                StringArgumentType.getString(context, "type"), true))))
        );
    }

    private static CompletableFuture<Suggestions> suggestMessageTypes(SuggestionsBuilder builder) {
        for (MessageType type : MessageType.values()) {
            builder.suggest(type.getCommandName());
        }
        builder.suggest("all");
        return builder.buildFuture();
    }

    private static int setMessageTypeEnabled(String argument, boolean enabled) {
        String configDir = ClientRuntime.getConfigDir().toString();
        boolean saved;
        String targetName;
        if ("all".equalsIgnoreCase(argument) || "全部".equals(argument)) {
            saved = ConfigManager.setAllMessageTypesEnabled(configDir, enabled);
            targetName = "全部消息类型";
        } else {
            MessageType type = MessageType.fromArgument(argument);
            if (type == null) {
                ClientRuntime.output("[DyDanmaku]未知消息类型：" + argument
                        + "；可用类型：chat、member、stats、like、gift、fansclub、all");
                return 0;
            }
            saved = ConfigManager.setMessageTypeEnabled(configDir, type, enabled);
            targetName = type.getDisplayName();
        }

        if (!saved) {
            ClientRuntime.output("[DyDanmaku]消息类型过滤设置保存失败，请查看日志");
            return 0;
        }
        ClientRuntime.output("[DyDanmaku]已" + (enabled ? "允许" : "过滤") + "：" + targetName);
        return showAllowedMessageTypes();
    }

    private static int showAllowedMessageTypes() {
        ConfigManager.MethodVisibilityConfig visibility = ConfigManager.getMethodVisibilityConfig(
                ClientRuntime.getConfigDir().toString());
        List<String> allowed = new ArrayList<>();
        for (MessageType type : MessageType.values()) {
            if (visibility.isEnabled(type)) {
                allowed.add(type.getDisplayName() + "(" + type.getCommandName() + ")");
            }
        }
        ClientRuntime.output("[DyDanmaku]当前允许的消息类型："
                + (allowed.isEmpty() ? "无" : String.join("、", allowed)));
        return Command.SINGLE_SUCCESS;
    }

    private static void sendChatMessage(String message) {
        LOGGER.info("{}", message);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.gui.getChat().addMessage(Component.literal(message)));
    }
}
