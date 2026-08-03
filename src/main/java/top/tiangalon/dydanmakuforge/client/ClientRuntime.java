package top.tiangalon.dydanmakuforge.client;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

import static top.tiangalon.dydanmakuforge.DyDanmakuForge.LOGGER;

public final class ClientRuntime {
    private static volatile Path configDir;
    private static volatile Consumer<String> messageSink =
            message -> LOGGER.info("[DyDanmaku]{}", message);
    private static volatile Consumer<Path> avatarSink = path -> { };

    private ClientRuntime() {
    }

    public static void initialize(
            Path configDirectory,
            Consumer<String> messages,
            Consumer<Path> avatars) {
        configDir = Objects.requireNonNull(configDirectory, "configDirectory");
        messageSink = Objects.requireNonNull(messages, "messages");
        avatarSink = Objects.requireNonNull(avatars, "avatars");
    }

    public static Path getConfigDir() {
        Path current = configDir;
        if (current == null) {
            throw new IllegalStateException("DyDanmaku 客户端尚未初始化");
        }
        return current;
    }

    public static void output(String message) {
        messageSink.accept(message);
    }

    public static void registerAvatar(Path path) {
        avatarSink.accept(path);
    }
}
