package top.tiangalon.dydanmakuforge.net;

import top.tiangalon.dydanmakuforge.client.ClientRuntime;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static top.tiangalon.dydanmakuforge.DyDanmakuForge.LOGGER;

public final class DyDanmakuRequest {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final Pattern TTWID_PATTERN = Pattern.compile("(?:^|;\\s*)ttwid=([^;]+)");

    private DyDanmakuRequest() {
    }

    public static Map<String, String> getParams(String liveId) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://live.douyin.com/" + liveId))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", "__ac_nonce=0" + generateToken(20) + "; /=live.douyin.com")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                throw new IOException("抖音直播页返回 HTTP " + response.statusCode());
            }

            String body = response.body();
            int statusIndex = requireIndex(body, "\\\"status_str\\\":\\\"");
            String detail = body.substring(statusIndex);

            Map<String, String> params = new HashMap<>();
            params.put("live_id", liveId);
            params.put("roomId", extractLastQuoted(body, "roomId\\\":\\\""));
            params.put("user_unique_id", extractQuoted(body, "\\\"user_unique_id\\\":\\\""));
            params.put("live_status", extractQuoted(detail, "\\\"status_str\\\":\\\""));
            params.put("live_title", extractQuoted(detail, "\\\"title\\\":\\\""));
            params.put("nickname", extractQuoted(detail, "\\\"nickname\\\":\\\""));
            params.put("avatar", extractQuoted(detail, "\\\"avatar_thumb\\\":{\\\"url_list\\\":[\\\""));
            params.put("ttwid", extractTtwid(response));
            return params;
        } catch (Exception exception) {
            LOGGER.error("[DyDanmaku]获取直播间 {} 参数失败", liveId, exception);
            return null;
        }
    }

    public static String generateToken(int length) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789=_";
        StringBuilder token = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            token.append(alphabet.charAt((int) (Math.random() * alphabet.length())));
        }
        return token.toString();
    }

    public static void downloadAvatar(String avatarUrl, String roomId) {
        HttpURLConnection connection = null;
        try {
            Path avatarDir = ClientRuntime.getConfigDir().resolve("avatars");
            Files.createDirectories(avatarDir);
            Path avatarPath = avatarDir.resolve(roomId + "_avatar.png");

            URL url = URI.create(avatarUrl.replace("\\u002F", "/")).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("头像请求返回 HTTP " + connection.getResponseCode());
            }

            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                BufferedImage image = ImageIO.read(input);
                if (image == null) {
                    throw new IOException("无法解析头像图像");
                }
                BufferedImage scaled = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = scaled.createGraphics();
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(image, 0, 0, 50, 50, null);
                graphics.dispose();
                if (!ImageIO.write(scaled, "PNG", avatarPath.toFile())) {
                    throw new IOException("无法编码 PNG 头像");
                }
            }

            LOGGER.info("[DyDanmaku]主播头像已保存至 {}", avatarPath);
            ClientRuntime.registerAvatar(avatarPath);
        } catch (Exception exception) {
            LOGGER.warn("[DyDanmaku]下载主播头像失败", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String extractTtwid(HttpResponse<?> response) throws IOException {
        for (String setCookie : response.headers().allValues("set-cookie")) {
            Matcher matcher = TTWID_PATTERN.matcher(setCookie);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new IOException("响应中缺少 ttwid Cookie");
    }

    private static String extractQuoted(String value, String marker) throws IOException {
        int start = requireIndex(value, marker) + marker.length();
        return extractQuotedValue(value, marker, start);
    }

    private static String extractLastQuoted(String value, String marker) throws IOException {
        int index = value.lastIndexOf(marker);
        if (index < 0) {
            throw new IOException("直播页缺少字段: " + marker);
        }
        return extractQuotedValue(value, marker, index + marker.length());
    }

    private static String extractQuotedValue(String value, String marker, int start) throws IOException {
        int end = value.indexOf('\\', start);
        if (end < 0) {
            throw new IOException("字段未闭合: " + marker);
        }
        return value.substring(start, end)
                .replace("\\u0026", "&")
                .replace("\\u003d", "=");
    }

    private static int requireIndex(String value, String marker) throws IOException {
        int index = value.indexOf(marker);
        if (index < 0) {
            throw new IOException("直播页缺少字段: " + marker);
        }
        return index;
    }

}
