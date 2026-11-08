package dev.wallpapermenu.wallpaper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Uses the user's logged-in desktop Steam client; never handles account credentials. */
public final class SteamWorkshopDownload {
    private SteamWorkshopDownload() { }


    public static List<String> command(Path steam, String itemId) {
        if (itemId == null || !itemId.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException("Invalid Workshop item ID");
        }
        return List.of(steam.toString(), "-silent", "+workshop_download_item", "431960", itemId);
    }

    public static void request(List<Path> steamRoots, String itemId) throws IOException, InterruptedException {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            throw new IOException("自动下载目前仅支持 Windows Steam 客户端");
        }
        Path steam = steamRoots.stream().map(root -> root.resolve("Steam.exe"))
                .filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new IOException("未找到 Steam.exe，请安装并登录 Steam"));
        Path log = steam.getParent().resolve("logs/workshop_log.txt");
        long offset = Files.isRegularFile(log) ? Files.size(log) : 0;
        Process launcher = new ProcessBuilder(command(steam, itemId))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if (!launcher.waitFor(15, TimeUnit.SECONDS)) {
            // Never kill Steam, including when starting a new logged-out client.
            throw new IOException("Steam 启动中，请登录后点击重试下载");
        }
        if (launcher.exitValue() != 0) throw new IOException("Steam 下载命令返回 " + launcher.exitValue());
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(log)) {
                try (var channel = java.nio.channels.FileChannel.open(log)) {
                    if (channel.size() < offset) offset = 0;
                    long remaining = channel.size() - offset;
                    if (remaining > 1024 * 1024) throw new IOException("Steam 日志增长过大，请检查客户端下载状态");
                    var bytes = java.nio.ByteBuffer.allocate((int) remaining);
                    channel.position(offset);
                    while (bytes.hasRemaining() && channel.read(bytes) > 0) { }
                    bytes.flip();
                    String text = java.nio.charset.StandardCharsets.UTF_8.decode(bytes).toString();
                    String result = result(text, itemId);
                    if ("OK".equals(result)) return;
                    if (!result.isEmpty()) throw new IOException("Steam 下载失败：" + result);
                }
            }
            Thread.sleep(1000);
        }
        throw new IOException("等待 Steam 下载结果超时，请确认登录、所有权和网络后重试");
    }

    public static String result(String freshLog, String itemId) {
        command(Path.of("Steam.exe"), itemId);
        var matcher = java.util.regex.Pattern.compile("\\[AppID 431960\\] Download item "
                + itemId + " result : ([^\\r\\n]+)").matcher(freshLog);
        String result = "";
        while (matcher.find()) result = matcher.group(1).trim();
        return result;
    }
}
