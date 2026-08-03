package top.tiangalon.dydanmakuforge.config;

import com.moandjiezana.toml.Toml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static top.tiangalon.dydanmakuforge.DyDanmakuForge.LOGGER;

public class ConfigManager {

    public enum MessageType {
        CHAT("chat", "chat", "消息", "WebcastChatMessage"),
        MEMBER("member", "member", "入场", "WebcastMemberMessage"),
        ROOM_STATS("stats", "roomStats", "统计", "WebcastRoomUserSeqMessage"),
        LIKE("like", "like", "点赞", "WebcastLikeMessage"),
        GIFT("gift", "gift", "礼物", "WebcastGiftMessage"),
        FANSCLUB("fansclub", "fansclub", "粉丝团", "WebcastFansclubMessage");

        private final String commandName;
        private final String configKey;
        private final String displayName;
        private final String methodName;

        MessageType(String commandName, String configKey, String displayName, String methodName) {
            this.commandName = commandName;
            this.configKey = configKey;
            this.displayName = displayName;
            this.methodName = methodName;
        }

        public String getCommandName() {
            return commandName;
        }

        public String getConfigKey() {
            return configKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static MessageType fromArgument(String argument) {
            if (argument == null) {
                return null;
            }
            for (MessageType type : values()) {
                if (type.commandName.equalsIgnoreCase(argument)
                        || type.configKey.equalsIgnoreCase(argument)
                        || type.displayName.equals(argument)) {
                    return type;
                }
            }
            return null;
        }

        public static MessageType fromMethod(String method) {
            for (MessageType type : values()) {
                if (type.methodName.equals(method)) {
                    return type;
                }
            }
            return null;
        }
    }

    /** 方法名到模板配置键的映射 */
    public static final Map<String, String> METHOD_TO_TEMPLATE_KEY = new HashMap<>();
    static {
        METHOD_TO_TEMPLATE_KEY.put("WebcastChatMessage",       "Chat");
        METHOD_TO_TEMPLATE_KEY.put("WebcastMemberMessage",      "Member");
        METHOD_TO_TEMPLATE_KEY.put("WebcastRoomUserSeqMessage", "RoomStats");
        METHOD_TO_TEMPLATE_KEY.put("WebcastLikeMessage",        "Like");
        METHOD_TO_TEMPLATE_KEY.put("WebcastGiftMessage",        "Gift");
        METHOD_TO_TEMPLATE_KEY.put("WebcastFansclubMessage",    "Fansclub");
    }

    /**
     * 弹幕过滤器配置
     */
    public static class FilterConfig {
        /** 过滤模式: "disabled"(禁用), "blacklist"(黑名单), "whitelist"(白名单) */
        public String mode = "disabled";
        /** 过滤关键词列表 */
        public List<String> keywords = new ArrayList<>();

        public boolean isEnabled() {
            return ("blacklist".equals(mode) || "whitelist".equals(mode)) && keywords != null && !keywords.isEmpty();
        }
    }

    /**
     * 消息类型可见性配置
     */
    public static class MethodVisibilityConfig {
        public boolean chat = true;      // WebcastChatMessage
        public boolean member = true;    // WebcastMemberMessage
        public boolean roomStats = true; // WebcastRoomUserSeqMessage
        public boolean like = true;      // WebcastLikeMessage
        public boolean gift = true;      // WebcastGiftMessage
        public boolean fansclub = true;  // WebcastFansclubMessage

        /**
         * 根据 method 名称判断是否应该显示该类型的消息
         */
        public boolean isMethodEnabled(String method) {
            MessageType type = MessageType.fromMethod(method);
            return type == null || isEnabled(type);
        }

        public boolean isEnabled(MessageType type) {
            switch (type) {
                case CHAT:       return chat;
                case MEMBER:     return member;
                case ROOM_STATS: return roomStats;
                case LIKE:       return like;
                case GIFT:       return gift;
                case FANSCLUB:   return fansclub;
                default:         return true;
            }
        }

        public void setEnabled(MessageType type, boolean enabled) {
            switch (type) {
                case CHAT:       chat = enabled; break;
                case MEMBER:     member = enabled; break;
                case ROOM_STATS: roomStats = enabled; break;
                case LIKE:       like = enabled; break;
                case GIFT:       gift = enabled; break;
                case FANSCLUB:   fansclub = enabled; break;
                default:
            }
        }
    }

    /**
     * 消息类型自定义输出模板配置
     */
    public static class TemplateConfig {
        /** method 名称 -> 模板字符串 */
        public Map<String, String> templates = new HashMap<>();

        /**
         * 获取指定 method 对应的输出模板
         * @param method 方法名，如 "WebcastChatMessage"
         * @return 模板字符串，若未设置则返回 null
         */
        public String getTemplate(String method) {
            String key = METHOD_TO_TEMPLATE_KEY.get(method);
            if (key != null) {
                return templates.get(key);
            }
            return null;
        }
    }

    /**
     * 用户属性过滤器配置（粉丝团、消费等级）
     */
    public static class UserFilterConfig {
        /** 是否启用用户过滤 */
        public boolean enabled = false;
        /** 是否要求必须有粉丝团 */
        public boolean requireFanClub = false;
        /** 最小粉丝团等级（0=不限制） */
        public int fanClubMinLevel = 0;
        /** 是否要求必须有消费等级 */
        public boolean requirePayGrade = false;
        /** 最小消费等级（0=不限制） */
        public int payGradeMinLevel = 0;
    }

    /**
     * 在 configDirPath 下创建 DyDanmakuSettings.toml（若已存在则跳过）
     */
    public static void createDefaultConfig(String configDirPath) {
        File configFile = new File(configDirPath, "DyDanmakuSettings.toml");
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                String defaultContent = "# 在下方输入抖音直播官网的sessionId\n"
                        + "DySessionId = \"\"\n"
                        + "\n"
                        + "# 弹幕过滤器设置\n"
                        + "# mode: 过滤模式，可选值:\n"
                        + "#   \"disabled\"  - 禁用过滤 (默认)\n"
                        + "#   \"blacklist\" - 黑名单模式，屏蔽包含关键词的弹幕\n"
                        + "#   \"whitelist\" - 白名单模式，仅显示包含关键词的弹幕\n"
                        + "# keywords: 关键词列表，匹配弹幕内容（不区分消息类型）\n"
                        + "[Filter]\n"
                        + "mode = \"blacklist\"\n"
                        + "keywords = [\"关键词1\", \"关键词2\"]\n"
                        + "\n"
                        + "# 消息类型显示开关\n"
                        + "# 设置为 false 则不在聊天框和弹幕列表中显示该类型的消息\n"
                        + "[MethodVisibility]\n"
                        + "chat = true      # 聊天消息\n"
                        + "member = true    # 进入直播间消息\n"
                        + "roomStats = true # 直播间统计消息\n"
                        + "like = true      # 点赞消息\n"
                        + "gift = true      # 礼物消息\n"
                        + "fansclub = true  # 粉丝团消息\n"
                        + "\n"
                        + "# 自定义输出模板\n"
                        + "# 使用 ${变量名} 引用消息中的数据，不设置则使用默认格式\n"
                        + "# 通用用户变量（Chat/Member/Like/Gift 均可用）:\n"
                        + "#   ${nickname} 用户名, ${payGradeLevel} 消费等级(无则为空), ${fansClubLevel} 粉丝团等级(无则为空)\n"
                        + "# 各消息类型专属变量:\n"
                        + "#   Chat:      ${content}\n"
                        + "#   Member:    ${memberCount}, ${actionDescription}, ${userId}\n"
                        + "#   RoomStats: ${totalStr}, ${totalPvForAnchor}\n"
                        + "#   Like:      ${count}\n"
                        + "#   Gift:      ${giftName}, ${giftCombo}, ${comboCount}, ${repeatCount}, ${giftId}, ${giftDescribe}, ${giftDiamondCount}, ${giftType}\n"
                        + "#   Fansclub:  ${content}\n"
                        + "[Template]\n"
                        + "Chat = \"\\u00a7b[消息]\\u00a7f ${nickname}：${content}\"\n"
                        + "Member = \"\\u00a7e[入场]\\u00a7f ${nickname} 进入了直播间\"\n"
                        + "RoomStats = \"\\u00a79[统计]\\u00a7f 当前观看：${totalStr}，累计观看：${totalPvForAnchor}\"\n"
                        + "Like = \"\\u00a7d[点赞]\\u00a7f ${nickname} 点了${count}个赞\"\n"
                        + "Gift = \"\\u00a7a[礼物]\\u00a7f ${nickname} 送出了${giftName}${giftCombo}\"\n"
                        + "Fansclub = \"\\u00a76[粉丝团]\\u00a7f ${content}\"\n"
                        + "\n"
                        + "# 用户属性过滤器（基于粉丝团/消费等级过滤）\n"
                        + "# 启用后，聊天、点赞、礼物消息会根据发送者属性进行过滤\n"
                        + "# requireFanClub: 是否要求必须有粉丝团（true=只显示有粉丝团用户的消息）\n"
                        + "# fanClubMinLevel: 最低粉丝团等级要求（0=不限制，如设为5则只显示粉丝团≥5级的用户消息）\n"
                        + "# requirePayGrade: 是否要求必须有消费等级（true=只显示有消费等级用户的消息）\n"
                        + "# payGradeMinLevel: 最低消费等级要求（0=不限制，如设为5则只显示消费≥5级的用户消息）\n"
                        + "# 多个条件为\"且\"关系，需同时满足\n"
                        + "[UserFilter]\n"
                        + "enabled = false\n"
                        + "requireFanClub = false\n"
                        + "fanClubMinLevel = 0\n"
                        + "requirePayGrade = false\n"
                        + "payGradeMinLevel = 0\n";
                Files.writeString(configFile.toPath(), defaultContent, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.error("[DyDanmaku]创建默认配置失败：{}", configFile, e);
            }
        }
    }

    /**
     * 从 DyDanmakuSettings.toml 中读取 DySessionId
     * @return 用户设置的 DySessionId 值，若未设置则返回 null
     */
    public static String getSessionId(String configDirPath) {
        File configFile = new File(configDirPath, "DyDanmakuSettings.toml");
        if (!configFile.exists()) {
            return null;
        }
        try {
            Toml toml = new Toml().read(configFile);
            String sessionId = toml.getString("DySessionId");
            if (sessionId != null && !sessionId.isEmpty()) {
                return sessionId;
            }
        } catch (Exception e) {
            // 读取失败，返回 null
        }
        return null;
    }

    /**
     * 保存 DySessionId。传入空字符串时清空配置。
     */
    public static synchronized boolean setSessionId(String configDirPath, String sessionId) {
        String value = sessionId == null ? "" : sessionId.trim();
        return writeSetting(configDirPath, null, "DySessionId", quoteTomlString(value));
    }

    /**
     * 从 DyDanmakuSettings.toml 中读取弹幕过滤器配置
     * @param configDirPath 配置目录路径
     * @return FilterConfig 过滤配置，若未设置则返回默认（disabled）配置
     */
    public static FilterConfig getFilterConfig(String configDirPath) {
        FilterConfig config = new FilterConfig();
        File configFile = new File(configDirPath, "DyDanmakuSettings.toml");
        if (!configFile.exists()) {
            return config;
        }
        try {
            Toml toml = new Toml().read(configFile);
            String mode = toml.getString("Filter.mode");
            if (isValidFilterMode(mode)) {
                config.mode = mode.toLowerCase();
            }
            List<String> keywords = toml.getList("Filter.keywords");
            if (keywords != null) {
                config.keywords = new ArrayList<>(keywords);
            }
        } catch (Exception e) {
            // 读取失败，返回默认配置
        }
        return config;
    }

    /**
     * 保存关键词过滤模式和关键词列表。
     */
    public static synchronized boolean setFilterConfig(
            String configDirPath, String mode, List<String> keywords) {
        String normalizedMode = isValidFilterMode(mode) ? mode.toLowerCase() : "disabled";
        List<String> normalizedKeywords = new ArrayList<>();
        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword == null) {
                    continue;
                }
                String normalizedKeyword = keyword.trim();
                if (!normalizedKeyword.isEmpty() && !normalizedKeywords.contains(normalizedKeyword)) {
                    normalizedKeywords.add(normalizedKeyword);
                }
            }
        }

        File configFile = new File(configDirPath, "DyDanmakuSettings.toml");
        if (!configFile.exists()) {
            createDefaultConfig(configDirPath);
        }
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            upsertSettingLine(lines, "Filter", "mode", quoteTomlString(normalizedMode));
            upsertSettingLine(lines, "Filter", "keywords", toTomlStringList(normalizedKeywords));
            Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            LOGGER.error("[DyDanmaku]保存弹幕关键词过滤设置失败：{}", configFile, exception);
            return false;
        }
    }

    /**
     * 从 DyDanmakuSettings.toml 中读取消息类型可见性配置
     * @param configDirPath 配置目录路径
     * @return MethodVisibilityConfig，若未设置则返回全 true 的默认配置
     */
    public static synchronized MethodVisibilityConfig getMethodVisibilityConfig(String configDirPath) {
        MethodVisibilityConfig config = new MethodVisibilityConfig();
        File configFile = new File(configDirPath, "DyDanmakuSettings.toml");
        if (!configFile.exists()) {
            return config;
        }
        try {
            Toml toml = new Toml().read(configFile);
            Boolean chat = toml.getBoolean("MethodVisibility.chat");
            if (chat != null) config.chat = chat;
            Boolean member = toml.getBoolean("MethodVisibility.member");
            if (member != null) config.member = member;
            Boolean roomStats = toml.getBoolean("MethodVisibility.roomStats");
            if (roomStats != null) config.roomStats = roomStats;
            Boolean like = toml.getBoolean("MethodVisibility.like");
            if (like != null) config.like = like;
            Boolean gift = toml.getBoolean("MethodVisibility.gift");
            if (gift != null) config.gift = gift;
            Boolean fansclub = toml.getBoolean("MethodVisibility.fansclub");
            if (fansclub != null) config.fansclub = fansclub;
        } catch (Exception e) {
            // 读取失败，返回默认配置
        }
        return config;
    }

    public static synchronized boolean setMessageTypeEnabled(
            String configDirPath, MessageType type, boolean enabled) {
        MethodVisibilityConfig config = getMethodVisibilityConfig(configDirPath);
        config.setEnabled(type, enabled);
        return writeMethodVisibilityConfig(configDirPath, config);
    }

    public static synchronized boolean setAllMessageTypesEnabled(String configDirPath, boolean enabled) {
        MethodVisibilityConfig config = getMethodVisibilityConfig(configDirPath);
        for (MessageType type : MessageType.values()) {
            config.setEnabled(type, enabled);
        }
        return writeMethodVisibilityConfig(configDirPath, config);
    }

    private static boolean writeMethodVisibilityConfig(
            String configDirPath, MethodVisibilityConfig config) {
        File configFile = new File(configDirPath, "DyDanmakuSettings.toml");
        if (!configFile.exists()) {
            createDefaultConfig(configDirPath);
        }
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            for (MessageType type : MessageType.values()) {
                upsertMethodVisibilityLine(lines, type.getConfigKey(), config.isEnabled(type));
            }
            Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            LOGGER.error("[DyDanmaku]保存消息类型过滤设置失败：{}", configFile, exception);
            return false;
        }
    }

    private static void upsertMethodVisibilityLine(List<String> lines, String key, boolean enabled) {
        upsertSettingLine(lines, "MethodVisibility", key, Boolean.toString(enabled));
    }

    private static boolean writeSetting(
            String configDirPath, String section, String key, String value) {
        File configFile = new File(configDirPath, "DyDanmakuSettings.toml");
        if (!configFile.exists()) {
            createDefaultConfig(configDirPath);
        }
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            upsertSettingLine(lines, section, key, value);
            Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            LOGGER.error("[DyDanmaku]保存配置项 {} 失败：{}", key, configFile, exception);
            return false;
        }
    }

    private static void upsertSettingLine(
            List<String> lines, String section, String key, String value) {
        int sectionStart = -1;
        int sectionEnd = lines.size();
        if (section == null) {
            sectionStart = -1;
            for (int index = 0; index < lines.size(); index++) {
                String trimmed = lines.get(index).trim();
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    sectionEnd = index;
                    break;
                }
            }
        } else {
            String sectionHeader = "[" + section + "]";
            for (int index = 0; index < lines.size(); index++) {
                String trimmed = lines.get(index).trim();
                if (sectionHeader.equals(trimmed)) {
                    sectionStart = index;
                    continue;
                }
                if (sectionStart >= 0 && trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    sectionEnd = index;
                    break;
                }
            }
        }

        if (section != null && sectionStart < 0) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            lines.add("[" + section + "]");
            sectionStart = lines.size() - 1;
            sectionEnd = lines.size();
        }

        for (int index = sectionStart + 1; index < sectionEnd; index++) {
            String original = lines.get(index);
            String setting = original;
            int commentIndex = findTomlCommentIndex(setting);
            if (commentIndex >= 0) {
                setting = setting.substring(0, commentIndex);
            }
            int equalsIndex = setting.indexOf('=');
            if (equalsIndex < 0 || !setting.substring(0, equalsIndex).trim().equals(key)) {
                continue;
            }

            int firstContentIndex = 0;
            while (firstContentIndex < original.length()
                    && Character.isWhitespace(original.charAt(firstContentIndex))) {
                firstContentIndex++;
            }
            String indentation = original.substring(0, firstContentIndex);
            String comment = commentIndex >= 0 ? " " + original.substring(commentIndex).trim() : "";
            lines.set(index, indentation + key + " = " + value + comment);
            return;
        }

        lines.add(sectionEnd, key + " = " + value);
    }

    private static int findTomlCommentIndex(String line) {
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (inString && character == '\\' && !escaped) {
                escaped = true;
                continue;
            }
            if (character == '"' && !escaped) {
                inString = !inString;
            } else if (character == '#' && !inString) {
                return index;
            }
            escaped = false;
        }
        return -1;
    }

    private static boolean isValidFilterMode(String mode) {
        return mode != null && ("disabled".equalsIgnoreCase(mode)
                || "blacklist".equalsIgnoreCase(mode)
                || "whitelist".equalsIgnoreCase(mode));
    }

    private static String toTomlStringList(List<String> values) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(quoteTomlString(values.get(index)));
        }
        return result.append(']').toString();
    }

    private static String quoteTomlString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\': result.append("\\\\"); break;
                case '"': result.append("\\\""); break;
                case '\b': result.append("\\b"); break;
                case '\t': result.append("\\t"); break;
                case '\n': result.append("\\n"); break;
                case '\f': result.append("\\f"); break;
                case '\r': result.append("\\r"); break;
                default: result.append(character);
            }
        }
        return result.append('"').toString();
    }

    /**
     * 从 DyDanmakuSettings.toml 中读取自定义输出模板配置
     * @param configDirPath 配置目录路径
     * @return TemplateConfig，包含各消息类型的模板
     */
    public static TemplateConfig getTemplateConfig(String configDirPath) {
        TemplateConfig config = new TemplateConfig();
        File configFile = new File(configDirPath, "DyDanmakuSettings.toml");
        if (!configFile.exists()) {
            return config;
        }
        try {
            Toml toml = new Toml().read(configFile);
            for (Map.Entry<String, String> entry : METHOD_TO_TEMPLATE_KEY.entrySet()) {
                String key = entry.getValue();
                String template = toml.getString("Template." + key);
                if (template != null && !template.isEmpty()) {
                    config.templates.put(key, template);
                }
            }
        } catch (Exception e) {
            // 读取失败，返回默认配置
        }
        return config;
    }

    /**
     * 从 DyDanmakuSettings.toml 中读取用户属性过滤器配置
     * @param configDirPath 配置目录路径
     * @return UserFilterConfig，若未设置则返回默认（disabled）配置
     */
    public static UserFilterConfig getUserFilterConfig(String configDirPath) {
        UserFilterConfig config = new UserFilterConfig();
        File configFile = new File(configDirPath, "DyDanmakuSettings.toml");
        if (!configFile.exists()) {
            return config;
        }
        try {
            Toml toml = new Toml().read(configFile);
            Boolean enabled = toml.getBoolean("UserFilter.enabled");
            if (enabled != null) config.enabled = enabled;
            Boolean requireFanClub = toml.getBoolean("UserFilter.requireFanClub");
            if (requireFanClub != null) config.requireFanClub = requireFanClub;
            Long fanClubMinLevel = toml.getLong("UserFilter.fanClubMinLevel");
            if (fanClubMinLevel != null) config.fanClubMinLevel = fanClubMinLevel.intValue();
            Boolean requirePayGrade = toml.getBoolean("UserFilter.requirePayGrade");
            if (requirePayGrade != null) config.requirePayGrade = requirePayGrade;
            Long payGradeMinLevel = toml.getLong("UserFilter.payGradeMinLevel");
            if (payGradeMinLevel != null) config.payGradeMinLevel = payGradeMinLevel.intValue();
        } catch (Exception e) {
            // 读取失败，返回默认配置
        }
        return config;
    }
}
