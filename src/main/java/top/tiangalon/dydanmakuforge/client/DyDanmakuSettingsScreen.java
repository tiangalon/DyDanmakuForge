package top.tiangalon.dydanmakuforge.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.tiangalon.dydanmakuforge.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/** 游戏内可编辑的 DyDanmaku 配置界面。 */
public final class DyDanmakuSettingsScreen extends Screen {
    private static final int MAX_VISIBLE_KEYWORDS = 6;
    private static final List<String> FILTER_MODES = List.of("disabled", "blacklist", "whitelist");

    private final Screen parent;
    private final String configDir;
    private final List<String> keywords = new ArrayList<>();
    private final List<Button> keywordButtons = new ArrayList<>();
    private EditBox sessionIdInput;
    private EditBox keywordInput;
    private Button modeButton;
    private Button editButton;
    private Button deleteButton;
    private String filterMode;
    private String statusMessage = "";
    private int selectedKeyword = -1;
    private int keywordScroll;
    private int visibleKeywordRows;

    public DyDanmakuSettingsScreen(Screen parent) {
        super(Component.literal("DyDanmaku 设置"));
        this.parent = parent;
        this.configDir = ClientRuntime.getConfigDir().toString();
        ConfigManager.FilterConfig filter = ConfigManager.getFilterConfig(configDir);
        this.filterMode = filter.mode;
        this.keywords.addAll(filter.keywords);
    }

    @Override
    protected void init() {
        String pendingSessionId = sessionIdInput == null ? null : sessionIdInput.getValue();
        String pendingKeyword = keywordInput == null ? "" : keywordInput.getValue();
        keywordButtons.clear();
        int left = Math.max(20, width / 2 - 190);
        int contentWidth = Math.min(380, width - 40);
        visibleKeywordRows = Math.max(1, Math.min(MAX_VISIBLE_KEYWORDS, (height - 205) / 22));

        String sessionId = ConfigManager.getSessionId(configDir);
        sessionIdInput = new EditBox(font, left, 48, contentWidth - 75, 20, Component.literal("DySessionId"));
        sessionIdInput.setMaxLength(4096);
        sessionIdInput.setValue(pendingSessionId != null ? pendingSessionId : sessionId == null ? "" : sessionId);
        addRenderableWidget(sessionIdInput);
        addRenderableWidget(Button.builder(Component.literal("保存"), button -> saveSessionId())
                .bounds(left + contentWidth - 70, 48, 70, 20).build());

        modeButton = Button.builder(filterModeText(), button -> cycleFilterMode())
                .bounds(left, 92, contentWidth, 20).build();
        addRenderableWidget(modeButton);

        keywordInput = new EditBox(font, left, 136, contentWidth - 225, 20, Component.literal("过滤关键词"));
        keywordInput.setMaxLength(256);
        keywordInput.setValue(pendingKeyword);
        addRenderableWidget(keywordInput);
        addRenderableWidget(Button.builder(Component.literal("新增"), button -> addKeyword())
                .bounds(left + contentWidth - 220, 136, 70, 20).build());
        editButton = Button.builder(Component.literal("修改"), button -> editKeyword())
                .bounds(left + contentWidth - 145, 136, 70, 20).build();
        addRenderableWidget(editButton);
        deleteButton = Button.builder(Component.literal("删除"), button -> deleteKeyword())
                .bounds(left + contentWidth - 70, 136, 70, 20).build();
        addRenderableWidget(deleteButton);

        for (int row = 0; row < visibleKeywordRows; row++) {
            final int rowIndex = row;
            Button keywordButton = Button.builder(Component.empty(), button -> selectKeyword(keywordScroll + rowIndex))
                    .bounds(left, 164 + row * 22, contentWidth, 20).build();
            keywordButtons.add(keywordButton);
            addRenderableWidget(keywordButton);
        }

        addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose())
                .bounds(left + contentWidth - 80, height - 30, 80, 20).build());
        refreshKeywordButtons();
    }

    private void saveSessionId() {
        if (ConfigManager.setSessionId(configDir, sessionIdInput.getValue())) {
            statusMessage = "DySessionId 已保存，下次连接时生效";
        } else {
            statusMessage = "DySessionId 保存失败，请查看日志";
        }
    }

    private void cycleFilterMode() {
        int index = FILTER_MODES.indexOf(filterMode);
        filterMode = FILTER_MODES.get((index + 1) % FILTER_MODES.size());
        modeButton.setMessage(filterModeText());
        saveFilter("过滤模式已保存");
    }

    private void addKeyword() {
        String keyword = keywordInput.getValue().trim();
        if (keyword.isEmpty()) {
            statusMessage = "请输入要新增的关键词";
            return;
        }
        if (keywords.contains(keyword)) {
            statusMessage = "该关键词已存在";
            return;
        }
        keywords.add(keyword);
        selectedKeyword = keywords.size() - 1;
        ensureSelectedKeywordVisible();
        keywordInput.setValue("");
        saveFilter("关键词已新增");
        refreshKeywordButtons();
    }

    private void editKeyword() {
        if (selectedKeyword < 0 || selectedKeyword >= keywords.size()) {
            statusMessage = "请先在下方选择一个关键词";
            return;
        }
        String keyword = keywordInput.getValue().trim();
        if (keyword.isEmpty()) {
            statusMessage = "请输入修改后的关键词";
            return;
        }
        int duplicateIndex = keywords.indexOf(keyword);
        if (duplicateIndex >= 0 && duplicateIndex != selectedKeyword) {
            statusMessage = "该关键词已存在";
            return;
        }
        keywords.set(selectedKeyword, keyword);
        keywordInput.setValue("");
        saveFilter("关键词已修改");
        refreshKeywordButtons();
    }

    private void deleteKeyword() {
        if (selectedKeyword < 0 || selectedKeyword >= keywords.size()) {
            statusMessage = "请先在下方选择一个关键词";
            return;
        }
        keywords.remove(selectedKeyword);
        if (keywords.isEmpty()) {
            selectedKeyword = -1;
        } else {
            selectedKeyword = Math.min(selectedKeyword, keywords.size() - 1);
        }
        keywordScroll = Math.min(keywordScroll, Math.max(0, keywords.size() - visibleKeywordRows));
        keywordInput.setValue("");
        saveFilter("关键词已删除");
        refreshKeywordButtons();
    }

    private void selectKeyword(int index) {
        if (index < 0 || index >= keywords.size()) {
            return;
        }
        selectedKeyword = index;
        keywordInput.setValue(keywords.get(index));
        refreshKeywordButtons();
    }

    private void saveFilter(String successMessage) {
        statusMessage = ConfigManager.setFilterConfig(configDir, filterMode, keywords)
                ? successMessage
                : "过滤器设置保存失败，请查看日志";
    }

    private void ensureSelectedKeywordVisible() {
        if (selectedKeyword < keywordScroll) {
            keywordScroll = selectedKeyword;
        } else if (selectedKeyword >= keywordScroll + visibleKeywordRows) {
            keywordScroll = selectedKeyword - visibleKeywordRows + 1;
        }
    }

    private void refreshKeywordButtons() {
        editButton.active = selectedKeyword >= 0;
        deleteButton.active = selectedKeyword >= 0;
        for (int row = 0; row < keywordButtons.size(); row++) {
            Button button = keywordButtons.get(row);
            int index = keywordScroll + row;
            button.visible = index < keywords.size();
            button.active = button.visible;
            if (button.visible) {
                String prefix = index == selectedKeyword ? "§a▶ §f" : "";
                button.setMessage(Component.literal(prefix + keywords.get(index)));
            }
        }
    }

    private Component filterModeText() {
        String displayName = switch (filterMode) {
            case "blacklist" -> "黑名单（屏蔽匹配关键词）";
            case "whitelist" -> "白名单（仅显示匹配关键词）";
            default -> "关闭（不过滤弹幕）";
        };
        return Component.literal("弹幕过滤器：" + displayName);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int left = Math.max(20, width / 2 - 190);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.drawString(font, "DySessionId", left, 36, 0xFFAAAAAA, false);
        graphics.drawString(font, "过滤模式（点击切换）", left, 80, 0xFFAAAAAA, false);
        graphics.drawString(font, "关键词（选择列表项后可修改或删除）", left, 124, 0xFFAAAAAA, false);
        if (keywords.size() > visibleKeywordRows) {
            graphics.drawString(font, "滚轮查看更多（" + (keywordScroll + 1) + "-"
                    + Math.min(keywordScroll + visibleKeywordRows, keywords.size()) + "/" + keywords.size() + "）",
                    left, 166 + visibleKeywordRows * 22, 0xFFAAAAAA, false);
        }
        if (!statusMessage.isEmpty()) {
            graphics.drawString(font, statusMessage, left, height - 27, 0xFF55FF55, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= 160 && mouseY <= 164 + visibleKeywordRows * 22
                && keywords.size() > visibleKeywordRows) {
            int maxScroll = keywords.size() - visibleKeywordRows;
            keywordScroll = Math.max(0, Math.min(maxScroll,
                    keywordScroll + (verticalAmount < 0 ? 1 : -1)));
            refreshKeywordButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER && keywordInput.isFocused()) {
            if (selectedKeyword >= 0) {
                editKeyword();
            } else {
                addKeyword();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
