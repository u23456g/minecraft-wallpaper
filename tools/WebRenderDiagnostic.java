import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class WebRenderDiagnostic {
    public static void main(String[] args) throws Exception {
        Path edge = Path.of("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe");
        Path html = Path.of("E:/Steam/steamapps/workshop/content/431960/3007669757/index.html");
        Path profile = Path.of(System.getProperty("java.io.tmpdir"), "wallpaper-web-diagnostic-profile");
        Process browser = new ProcessBuilder(edge.toString(), "--headless=new", "--disable-gpu", "--no-sandbox", "--allow-file-access-from-files", "--remote-debugging-port=9337", "--user-data-dir=" + profile, "about:blank").redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            URI ws = null;
            for (int i = 0; i < 100 && ws == null; i++) {
                try {
                    var response = http.send(java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:9337/json/list")).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    ws = JsonParser.parseString(response.body()).getAsJsonArray().get(0).getAsJsonObject().get("webSocketDebuggerUrl").getAsString() instanceof String s ? URI.create(s) : null;
                } catch (Exception ignored) { Thread.sleep(100); }
            }
            AtomicInteger nextId = new AtomicInteger();
            java.util.concurrent.ConcurrentHashMap<Integer, CompletableFuture<JsonObject>> replies = new java.util.concurrent.ConcurrentHashMap<>();
            WebSocket socket = http.newWebSocketBuilder().buildAsync(ws, new WebSocket.Listener() {
                StringBuilder text = new StringBuilder();
                public java.util.concurrent.CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                    text.append(data);
                    if (last) {
                        JsonObject msg = JsonParser.parseString(text.toString()).getAsJsonObject(); text.setLength(0);
                        if (msg.has("id")) { var future = replies.remove(msg.get("id").getAsInt()); if (future != null) future.complete(msg.getAsJsonObject("result")); }
                    }
                    socket.request(1); return CompletableFuture.completedFuture(null);
                }
            }).get(10, TimeUnit.SECONDS);
            java.util.function.BiFunction<String, JsonObject, JsonObject> command = (method, params) -> {
                int id = nextId.incrementAndGet(); JsonObject msg = new JsonObject(); msg.addProperty("id", id); msg.addProperty("method", method); msg.add("params", params);
                var future = new CompletableFuture<JsonObject>(); replies.put(id, future); socket.sendText(msg.toString(), true);
                try { return future.get(5, TimeUnit.SECONDS); } catch (Exception e) { throw new RuntimeException(e); }
            };
            command.apply("Page.enable", new JsonObject());
            JsonObject viewport = new JsonObject(); viewport.addProperty("width", 960); viewport.addProperty("height", 540); viewport.addProperty("deviceScaleFactor", 1); viewport.addProperty("mobile", false);
            command.apply("Emulation.setDeviceMetricsOverride", viewport);
            JsonObject script = new JsonObject(); script.addProperty("source", """
                    (() => {
                      const install = () => {
                        if (!document.documentElement || document.getElementById('wallpaper-menu-viewport-style')) return;
                        const s = document.createElement('style');
                        s.id = 'wallpaper-menu-viewport-style';
                        s.textContent = 'p { padding-left:0 !important; padding-right:0 !important; } #object_clock { width:100vw !important; margin:0 auto !important; } .tab::before { content:"" !important; }';
                        document.documentElement.appendChild(s);
                      };
                      new MutationObserver(install).observe(document, {childList:true, subtree:true});
                      install();
                    })();
                    """);
            command.apply("Page.addScriptToEvaluateOnNewDocument", script);
            JsonObject nav = new JsonObject(); nav.addProperty("url", html.toUri().toString()); command.apply("Page.navigate", nav);
            Thread.sleep(2000);
            JsonObject shot = new JsonObject(); shot.addProperty("format", "png");
            String data = command.apply("Page.captureScreenshot", shot).get("data").getAsString();
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(data)));
            ImageIO.write(image, "png", Path.of("G:/minecraft-wallpaper/build/web-render-diagnostic.png").toFile());
            System.out.println("saved " + image.getWidth() + "x" + image.getHeight());
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(2, TimeUnit.SECONDS);
        } finally { browser.descendants().forEach(ProcessHandle::destroyForcibly); browser.destroyForcibly(); }
    }
}
