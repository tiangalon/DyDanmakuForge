package top.tiangalon.dydanmakuforge.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import top.tiangalon.dydanmakuforge.config.ConfigManager;
import top.tiangalon.dydanmakuforge.config.ConfigManager.MessageType;
import top.tiangalon.dydanmakuforge.net.WebSocketClientNetty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static top.tiangalon.dydanmakuforge.DyDanmakuForge.LOGGER;
import static top.tiangalon.dydanmakuforge.client.DyDanmakuForgeClient.OPEN_GUI;

public final class DyDanmakuScreen extends Screen {
    private static final String EXAMPLE_LIVE_ID = "594357732923";
    private static final ResourceLocation AVATAR_ID = ResourceLocation.parse("dydanmaku:avatar");
    private static final ResourceLocation LOADING_ID =
            ResourceLocation.fromNamespaceAndPath("dydanmaku", "textures/gui/sprite/loading.png");
    private static volatile Path avatarPath;

    private final DyDanmakuController controller;
    private EditBox liveIdInput;
    private Button connectButton;
    private DynamicTexture avatarTexture;
    private boolean avatarRegistered;
    private int scrollOffset;
    private int currentFrame;
    private long lastFrameTime;

    public DyDanmakuScreen(DyDanmakuController controller) {
        super(Component.literal("DyDanmaku"));
        this.controller = controller;
    }

    public static void setAvatar(Path path) {
        avatarPath = path;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof DyDanmakuScreen screen) {
                screen.loadAvatar(path);
            }
        });
    }

    @Override
    protected void init() {
        String initialLiveId = liveIdInput == null ? EXAMPLE_LIVE_ID : liveIdInput.getValue();
        liveIdInput = new EditBox(font, 40, 70, 120, 20, Component.literal("输入直播间 URL 或房间号"));
        liveIdInput.setMaxLength(256);
        liveIdInput.setValue(initialLiveId);
        addRenderableWidget(liveIdInput);

        connectButton = Button.builder(Component.literal("连接"), button -> {
            if (controller.getWebsocket().isConnected()) {
                controller.disconnect();
            } else {
                controller.connect(liveIdInput.getValue());
            }
        }).bounds(170, 70, 70, 20).build();
        addRenderableWidget(connectButton);

        addRenderableWidget(Button.builder(Component.literal("更多设置"), button ->
                        minecraft.setScreen(new DyDanmakuSettingsScreen(this)))
                .bounds(width - 100, 25, 80, 20).build());

        ConfigManager.MethodVisibilityConfig visibility = ConfigManager.getMethodVisibilityConfig(
                ClientRuntime.getConfigDir().toString());
        MessageType[] messageTypes = MessageType.values();
        for (int index = 0; index < messageTypes.length; index++) {
            MessageType type = messageTypes[index];
            int x = 40 + (index % 3) * 75;
            int y = 112 + (index / 3) * 22;
            Checkbox checkbox = Checkbox.builder(Component.literal(type.getDisplayName()), font)
                    .pos(x, y)
                    .selected(visibility.isEnabled(type))
                    .maxWidth(70)
                    .onValueChange((changedCheckbox, selected) -> {
                        if (!ConfigManager.setMessageTypeEnabled(
                                ClientRuntime.getConfigDir().toString(), type, selected)) {
                            ClientRuntime.output("[DyDanmaku]消息类型过滤设置保存失败，请查看日志");
                        }
                    })
                    .build();
            addRenderableWidget(checkbox);
        }

        Path pendingAvatar = avatarPath;
        if (pendingAvatar != null && Files.isRegularFile(pendingAvatar)) {
            loadAvatar(pendingAvatar);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        WebSocketClientNetty websocket = controller.getWebsocket();
        boolean connected = websocket.isConnected();

        graphics.drawString(font, "DyDanmaku Forge", 40, 25, 0xFFFFFFFF, true);
        graphics.drawString(
                font,
                controller.isConnecting() ? "直播间状态：连接中" : connected ? "直播间状态：已连接" : "直播间状态：未连接",
                40,
                45,
                connected ? 0xFF55FF55 : 0xFFFFFFFF,
                true
        );

        liveIdInput.setEditable(!connected && !controller.isConnecting());
        connectButton.setMessage(Component.literal(
                controller.isConnecting() ? "连接中" : connected ? "断开" : "连接"));
        graphics.drawString(font, "显示类型（勾选=显示）：", 40, 100, 0xFFFFFFFF, false);

        if (!connected || websocket.params == null) {
            graphics.drawString(font, "输入可以是 URL 或者房间号", 40, 165, 0xFFAAAAAA, false);
            graphics.drawString(font, "示例 URL：https://live.douyin.com/594357732923", 40, 180, 0xFFAAAAAA, false);
            graphics.drawString(font, "示例房间号：594357732923", 40, 195, 0xFFAAAAAA, false);
            graphics.drawString(font, "按 F7 可关闭此界面", 40, 210, 0xFFAAAAAA, false);
            return;
        }

        liveIdInput.setValue(websocket.params.getOrDefault("live_id", ""));
        String sessionId = ConfigManager.getSessionId(ClientRuntime.getConfigDir().toString());
        if (sessionId == null || sessionId.isBlank()) {
            graphics.drawString(font, "未设置抖音 sessionid，可能无法收到礼物信息", 250, 75, 0xFFFF5555, true);
        }

        drawAvatar(graphics);
        graphics.drawString(font, "主播：" + websocket.params.getOrDefault("nickname", ""), 100, 165, 0xFFFFFFFF, true);
        graphics.drawString(font, "标题：" + websocket.params.getOrDefault("live_title", ""), 100, 180, 0xFFFFFFFF, true);
        drawDanmaku(graphics, websocket.getDanmakuText());
    }

    private void drawAvatar(GuiGraphics graphics) {
        if (avatarRegistered) {
            graphics.blit(AVATAR_ID, 40, 165, 0, 0, 50, 50, 50, 50);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastFrameTime > 33) {
            currentFrame = (currentFrame + 1) % 30;
            lastFrameTime = now;
        }
        graphics.blit(LOADING_ID, 40, 165, 0, currentFrame * 50, 50, 50, 50, 1500);
    }

    private void drawDanmaku(GuiGraphics graphics, String text) {
        int top = 225;
        int bottom = height - 15;
        int maxLines = Math.max(1, (bottom - top) / font.lineHeight);
        List<String> lines = text.isEmpty() ? List.of() : Arrays.asList(text.split("\\R"));
        int maxOffset = Math.max(0, lines.size() - maxLines);
        scrollOffset = Math.min(scrollOffset, maxOffset);
        int end = Math.max(0, lines.size() - scrollOffset);
        int start = Math.max(0, end - maxLines);

        graphics.fill(40, top - 5, width - 40, bottom + 5, 0x80000000);
        graphics.enableScissor(40, top - 5, width - 40, bottom + 5);
        for (int index = start; index < end; index++) {
            int y = top + (index - start) * font.lineHeight;
            graphics.drawString(font, lines.get(index), 45, y, 0xFFFFFFFF, false);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= 150) {
            scrollOffset = Math.max(0, scrollOffset + (verticalAmount > 0 ? 2 : -2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (OPEN_GUI.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER && liveIdInput.isFocused()) {
            controller.connect(liveIdInput.getValue());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        releaseAvatarTexture();
        super.removed();
    }

    private void loadAvatar(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(input);
            releaseAvatarTexture();
            avatarTexture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(AVATAR_ID, avatarTexture);
            avatarRegistered = true;
        } catch (IOException | RuntimeException exception) {
            releaseAvatarTexture();
            LOGGER.warn("[DyDanmaku]加载主播头像失败：{}", path, exception);
        }
    }

    private void releaseAvatarTexture() {
        if (avatarRegistered) {
            Minecraft.getInstance().getTextureManager().release(AVATAR_ID);
        } else if (avatarTexture != null) {
            avatarTexture.close();
        }
        avatarTexture = null;
        avatarRegistered = false;
    }
}
