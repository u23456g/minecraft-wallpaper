package dev.wallpapermenu.wallpaper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Captures a Wallpaper Engine HTML project through an installed Chromium browser. */
public final class ChromiumWebDecoder implements AutoCloseable {
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    private final Path profile;
    private final Process browser;
    private final WebSocket socket;
    private final HttpClient http;
    private final AtomicInteger commandId = new AtomicInteger();
    private CompletableFuture<WebSocket> outgoing = CompletableFuture.completedFuture(null);

    private synchronized CompletableFuture<WebSocket> send(String message) {
        outgoing = outgoing.thenCompose(ignored -> socket.sendText(message, true));
        return outgoing;
    }
    private final Map<Integer, CompletableFuture<JsonObject>> replies = new ConcurrentHashMap<>();
    private final AtomicReference<byte[]> latest = new AtomicReference<>();
    private final ConcurrentLinkedQueue<JsonObject> inputEvents = new ConcurrentLinkedQueue<>();
    private volatile long captureIntervalNanos;
    private volatile long lastSuccessfulFrameNanos;
    private long lastAcceptedFrameNanos;
    private long reportStartedNanos;
    private int successfulFrames;
    private final ThreadPoolExecutor frameExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), r -> {
        Thread thread = new Thread(r, "wallpaper-web-frames");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.DiscardOldestPolicy());
    private final Thread worker;
    private volatile boolean closed;
    private volatile String failure;

    public ChromiumWebDecoder(Path htmlFile) throws IOException {
        this(htmlFile, 30);
    }

    public ChromiumWebDecoder(Path htmlFile, int fps) throws IOException {
        this(htmlFile, fps, true);
    }

    public ChromiumWebDecoder(Path htmlFile, int fps, boolean audioEnabled) throws IOException {
        this(htmlFile, fps, audioEnabled ? 100 : 0);
    }

    public ChromiumWebDecoder(Path htmlFile, int fps, int volume) throws IOException {
        captureIntervalNanos = TimeUnit.SECONDS.toNanos(1) / Math.clamp(fps, 30, 120);
        Path executable = findBrowser();
        profile = Files.createTempDirectory("wallpaper-menu-web-");
        int port = freePort();
        Process started = null;
        WebSocket connected = null;
        try {
            started = new ProcessBuilder(executable.toString(), "--headless=new", "--disable-gpu", "--no-sandbox",
                    "--disable-dev-shm-usage", "--hide-scrollbars", "--autoplay-policy=no-user-gesture-required",
                    "--allow-file-access-from-files", "--remote-allow-origins=*", "--remote-debugging-port=" + port,
                    "--user-data-dir=" + profile, "--autoplay-policy=no-user-gesture-required", "about:blank")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            browser = started;
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            URI endpoint = awaitPageEndpoint(port);
            connected = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(endpoint, new Listener()).get(7, TimeUnit.SECONDS);
            socket = connected;
            command("Page.enable", new JsonObject());
            // This optional web-host SDK resolves to a UNC file host under file:// and blocks HTML parsing.
            // Keep the wallpaper's local scripts/assets intact; navigation already falls back without window.toy.
            command("Network.enable", new JsonObject());
            JsonObject blocked = new JsonObject();
            com.google.gson.JsonArray blockedUrls = new com.google.gson.JsonArray();
            blockedUrls.add("file://s1.hdslb.com/*");
            blocked.add("urls", blockedUrls);
            command("Network.setBlockedURLs", blocked);
            JsonObject viewport = new JsonObject();
            viewport.addProperty("width", WIDTH);
            viewport.addProperty("height", HEIGHT);
            viewport.addProperty("deviceScaleFactor", 1);
            viewport.addProperty("mobile", false);
            command("Emulation.setDeviceMetricsOverride", viewport);
            JsonObject script = new JsonObject();
            script.addProperty("source", """
                    (() => {
                      const install = () => {
                        if (!document.documentElement || document.getElementById('wallpaper-menu-viewport-style')) return;
                        const style = document.createElement('style');
                        style.id = 'wallpaper-menu-viewport-style';
                        style.textContent = 'p { padding-left: 0 !important; padding-right: 0 !important; } #object_clock { width: max-content !important; margin: auto !important; }';
                        document.documentElement.appendChild(style);
                      };
                      new MutationObserver(install).observe(document, { childList: true, subtree: true });
                      install();
                    })();
                    """);
            command("Page.addScriptToEvaluateOnNewDocument", script);
            JsonObject audioScript = new JsonObject();
            audioScript.addProperty("source", audioScript(volume));
            command("Page.addScriptToEvaluateOnNewDocument", audioScript);
            JsonObject screencast = new JsonObject();
            screencast.addProperty("format", "jpeg");
            screencast.addProperty("quality", 75);
            screencast.addProperty("maxWidth", WIDTH);
            screencast.addProperty("maxHeight", HEIGHT);
            screencast.addProperty("everyNthFrame", 1);
            command("Page.startScreencast", screencast);
            JsonObject navigate = new JsonObject();
            navigate.addProperty("url", htmlFile.toUri().toString());
            command("Page.navigate", navigate);
        } catch (Exception exception) {
            if (connected != null) connected.abort();
            if (started != null) started.destroyForcibly();
            deleteProfile(profile);
            throw new IOException("Could not start Chromium web wallpaper renderer", exception);
        }
        lastSuccessfulFrameNanos = System.nanoTime();
        worker = new Thread(this::capture, "wallpaper-web-capture");
        worker.setDaemon(true);
        worker.start();
    }

    private static String audioScript(int percent) {
        return "window.__wmVolume=" + Math.clamp(percent, 0, 100) / 100.0 + ";" + """
            window.__wmGains = [];
            for (const name of ['AudioContext','webkitAudioContext']) {
              const Base = window[name];
              if (!Base) continue;
              window[name] = class extends Base {
                constructor(...args) {
                  super(...args);
                  const gain = this.createGain();
                  gain.gain.value = window.__wmVolume;
                  gain.connect(this.destination);
                  window.__wmGains.push(gain);
                  const connect = AudioNode.prototype.connect;
                  // Reroute only nodes in this context, never the gain's own destination edge.
                  if (!window.__wmAudioPatched) {
                    window.__wmAudioPatched = true;
                    AudioNode.prototype.connect = function(target, ...rest) {
                      const g = window.__wmGains.find(g => g.context === this.context);
                      return connect.call(this, g && this !== g && target === this.context.destination ? g : target, ...rest);
                    };
                  }
                }
              };
            }
            setInterval(() => document.querySelectorAll('audio,video').forEach(e => {
              if (e.volume !== window.__wmVolume) e.volume = window.__wmVolume;
            }), 100);
            """;
    }

    public void setVolume(int percent) {
        JsonObject params = new JsonObject();
        params.addProperty("expression", "window.__wmVolume=" + Math.clamp(percent, 0, 100) / 100.0
                + ";(window.__wmGains||[]).forEach(g=>g.gain.value=window.__wmVolume);"
                + "document.querySelectorAll('audio,video').forEach(e=>e.volume=window.__wmVolume)");
        sendCommandWithoutWaiting("Runtime.evaluate", params);
    }

    private static Path findBrowser() throws IOException {
        String[] candidates = {
                System.getenv("PROGRAMFILES(X86)") == null ? "" : System.getenv("PROGRAMFILES(X86)") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("PROGRAMFILES") == null ? "" : System.getenv("PROGRAMFILES") + "\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "/usr/bin/chromium", "/usr/bin/google-chrome", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
        };
        for (String candidate : candidates) {
            if (!candidate.isBlank() && Files.isRegularFile(Path.of(candidate))) return Path.of(candidate);
        }
        throw new IOException("Install Microsoft Edge or Google Chrome to render web wallpapers");
    }

    private URI awaitPageEndpoint(int port) throws Exception {
        URI listUri = URI.create("http://127.0.0.1:" + port + "/json/list");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && browser.isAlive()) {
            try {
                String json = http.send(java.net.http.HttpRequest.newBuilder(listUri).timeout(Duration.ofSeconds(1)).GET().build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                var targets = JsonParser.parseString(json).getAsJsonArray();
                for (var item : targets) {
                    JsonObject target = item.getAsJsonObject();
                    if ("page".equals(target.get("type").getAsString())) return URI.create(target.get("webSocketDebuggerUrl").getAsString());
                }
            } catch (Exception ignored) { }
            Thread.sleep(100);
        }
        throw new IOException("Chromium DevTools endpoint did not become available");
    }

    private JsonObject command(String method, JsonObject params) throws Exception {
        int id = commandId.incrementAndGet();
        JsonObject message = new JsonObject();
        message.addProperty("id", id);
        message.addProperty("method", method);
        message.add("params", params);
        CompletableFuture<JsonObject> reply = new CompletableFuture<>();
        replies.put(id, reply);
        send(message.toString()).get(3, TimeUnit.SECONDS);
        JsonObject response = reply.get(5, TimeUnit.SECONDS);
        if (response.has("error")) throw new IOException(response.get("error").toString());
        return response.has("result") ? response.getAsJsonObject("result") : new JsonObject();
    }

    private void sendCommandWithoutWaiting(String method, JsonObject params) {
        if (closed || socket.isOutputClosed()) return;
        JsonObject message = new JsonObject();
        message.addProperty("id", commandId.incrementAndGet());
        message.addProperty("method", method);
        message.add("params", params);
        send(message.toString()).exceptionally(error -> {
            if (!closed) failure = "Could not send " + method + ": " + error;
            return null;
        });
    }

    private void capture() {
        while (!closed && browser.isAlive()) {
            try {
                flushInputEvents();
            } catch (Exception exception) {
                if (!closed) failure = "Could not send web input: " + exception;
                return;
            }
            if (System.nanoTime() - lastSuccessfulFrameNanos > TimeUnit.SECONDS.toNanos(180)) {
                if (!closed) failure = "No screencast frames received for 180 seconds";
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                if (!closed) failure = "Screenshot capture interrupted";
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (!closed && !browser.isAlive()) failure = "Chromium exited";
    }

    private void acceptScreencastFrame(JsonObject params) {
        long now = System.nanoTime();
        lastSuccessfulFrameNanos = now;
        try {
            if (lastAcceptedFrameNanos != 0 && now - lastAcceptedFrameNanos < captureIntervalNanos) return;
            lastAcceptedFrameNanos = lastAcceptedFrameNanos == 0 ? now
                    : Math.max(lastAcceptedFrameNanos + captureIntervalNanos, now - captureIntervalNanos);
            String data = params.get("data").getAsString();
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(data)));
            if (source == null) throw new IOException("Chromium returned an invalid screencast frame");
            BufferedImage frame = source;
            if (source.getWidth() != WIDTH || source.getHeight() != HEIGHT) {
                frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D graphics = frame.createGraphics();
                graphics.drawImage(source, 0, 0, WIDTH, HEIGHT, null);
                graphics.dispose();
            }
            int[] pixels = frame.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH);
            byte[] rgba = new byte[pixels.length * 4];
            for (int i = 0, j = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                rgba[j++] = (byte) (pixel >>> 16);
                rgba[j++] = (byte) (pixel >>> 8);
                rgba[j++] = (byte) pixel;
                rgba[j++] = (byte) (pixel >>> 24);
            }
            latest.set(rgba);
            if (reportStartedNanos == 0) reportStartedNanos = now;
            successfulFrames++;
            if (now - reportStartedNanos >= TimeUnit.SECONDS.toNanos(10)) {
                double measuredFps = successfulFrames * (double) TimeUnit.SECONDS.toNanos(1) / (now - reportStartedNanos);
                org.slf4j.LoggerFactory.getLogger("wallpaper_menu").info("Web wallpaper capture rate: configured={} fps, measured={} fps",
                        Math.round(TimeUnit.SECONDS.toNanos(1) / (double) captureIntervalNanos),
                        Math.round(measuredFps * 10.0) / 10.0);
                successfulFrames = 0;
                reportStartedNanos = now;
            }
        } catch (Exception exception) {
            failure = "Could not decode screencast frame: " + exception;
        }
    }

    public void setFps(int fps) {
        captureIntervalNanos = TimeUnit.SECONDS.toNanos(1) / Math.clamp(fps, 30, 120);
    }

    public byte[] pollFrame() { return latest.getAndSet(null); }
    public String failure() { return failure; }

    public void dispatchMouse(String type, double x, double y, int button, int clickCount) {
        if (!closed && x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            JsonObject event = new JsonObject();
            event.addProperty("method", "Input.dispatchMouseEvent");
            JsonObject params = new JsonObject();
            params.addProperty("type", type);
            params.addProperty("x", x);
            params.addProperty("y", y);
            if (!type.equals("mouseMoved")) {
                params.addProperty("button", buttonName(button));
                params.addProperty("clickCount", clickCount);
            }
            params.addProperty("buttons", type.equals("mousePressed") || type.equals("mouseMoved") && button >= 0
                    ? 1 << Math.max(0, Math.min(button, 2)) : 0);
            event.add("params", params);
            enqueue(event);
        }
    }

    public void dispatchScroll(double x, double y, double deltaX, double deltaY) {
        if (!closed && x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            JsonObject event = new JsonObject();
            event.addProperty("method", "Input.dispatchMouseEvent");
            JsonObject params = new JsonObject();
            params.addProperty("type", "mouseWheel");
            params.addProperty("x", x);
            params.addProperty("y", y);
            params.addProperty("deltaX", deltaX);
            params.addProperty("deltaY", deltaY);
            event.add("params", params);
            enqueue(event);
        }
    }

    public void dispatchKey(String type, int keyCode, int scanCode, int modifiers) {
        if (!closed) {
            JsonObject event = new JsonObject();
            event.addProperty("method", "Input.dispatchKeyEvent");
            JsonObject params = new JsonObject();
            params.addProperty("type", type);
            params.addProperty("windowsVirtualKeyCode", keyCode);
            params.addProperty("nativeVirtualKeyCode", scanCode);
            params.addProperty("modifiers", CdpInputMapping.modifiers(modifiers));
            event.add("params", params);
            enqueue(event);
        }
    }

    public void dispatchText(String text) {
        if (!closed && text != null && !text.isEmpty()) {
            JsonObject event = new JsonObject();
            event.addProperty("method", "Input.insertText");
            JsonObject params = new JsonObject();
            params.addProperty("text", text);
            event.add("params", params);
            enqueue(event);
        }
    }

    private static String buttonName(int button) {
        return switch (button) { case 1 -> "right"; case 2 -> "middle"; default -> "left"; };
    }

    private void enqueue(JsonObject event) {
        if (inputEvents.size() < 256) inputEvents.offer(event);
    }

    private void flushInputEvents() throws Exception {
        JsonObject event;
        while ((event = inputEvents.poll()) != null) {
            int id = commandId.incrementAndGet();
            event.addProperty("id", id);
            send(event.toString()).get(3, TimeUnit.SECONDS);
        }
    }

    @Override
    public void close() {
        closed = true;
        socket.abort();
        frameExecutor.shutdownNow();
        browser.descendants().forEach(ProcessHandle::destroyForcibly);
        browser.destroyForcibly();
        worker.interrupt();
        try { worker.join(2000); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        latest.set(null);
        deleteProfile(profile);
    }

    private static int freePort() throws IOException {
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            return server.getLocalPort();
        }
    }

    private static void deleteProfile(Path path) {
        try (var files = Files.walk(path)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(file -> {
                try { Files.deleteIfExists(file); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder message = new StringBuilder();
        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
        @Override public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                try {
                    JsonObject response = JsonParser.parseString(message.toString()).getAsJsonObject();
                    if ("Page.screencastFrame".equals(response.get("method") != null
                            ? response.get("method").getAsString() : null)) {
                        JsonObject params = response.getAsJsonObject("params");
                        JsonObject ackParams = new JsonObject();
                        ackParams.addProperty("sessionId", params.get("sessionId").getAsInt());
                        sendCommandWithoutWaiting("Page.screencastFrameAck", ackParams);
                        frameExecutor.execute(() -> acceptScreencastFrame(params));
                    }
                    if (response.has("id")) {
                        CompletableFuture<JsonObject> pending = replies.remove(response.get("id").getAsInt());
                        if (pending != null) pending.complete(response);
                    }
                } catch (RuntimeException ignored) { }
                message.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket webSocket, Throwable error) { failure = error.getMessage(); }
    }
}
