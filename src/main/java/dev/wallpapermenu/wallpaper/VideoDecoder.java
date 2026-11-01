package dev.wallpapermenu.wallpaper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded single-frame mailbox; decoding never touches Minecraft or OpenGL. */
public final class VideoDecoder implements AutoCloseable {
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    private final AtomicReference<byte[]> latest = new AtomicReference<>();
    private final Process process;
    private final Thread worker;
    private final int width;
    private final int height;
    private volatile boolean closed;
    private volatile String failure;

    public VideoDecoder(Path video, int fps) throws IOException {
        this(video, WIDTH, HEIGHT, fps);
    }

    public VideoDecoder(Path video, int width, int height, int fps) throws IOException {
        // ponytail: reuse installed FFmpeg; bundled codecs can replace this backend later.
        int safeFps = Math.clamp(fps, 30, 120);
        this.width = Math.max(2, width);
        this.height = Math.max(2, height);
        process = new ProcessBuilder("ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error",
                "-re", "-stream_loop", "-1", "-i", video.toAbsolutePath().toString(),
                "-an", "-vf", "fps=" + safeFps + ",scale=" + this.width + ":" + this.height
                        + ":force_original_aspect_ratio=increase,crop=" + this.width + ":" + this.height,
                "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        worker = new Thread(this::decode, "wallpaper-video-decoder");
        worker.setDaemon(true);
        worker.start();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean bgra() {
        return false;
    }

    private void decode() {
        int frameBytes = width * height * 4;
        try (var input = process.getInputStream()) {
            while (!closed) {
                byte[] frame = input.readNBytes(frameBytes);
                if (frame.length != frameBytes) {
                    if (!closed) failure = "FFmpeg stopped before a complete frame";
                    break;
                }
                latest.set(frame);
            }
        } catch (IOException exception) {
            if (!closed) failure = exception.getMessage();
        } finally {
            process.destroy();
        }
    }

    public VideoDecoder(Path video) throws IOException {
        this(video, 60);
    }

    public byte[] pollFrame() {
        return latest.getAndSet(null);
    }

    public String failure() {
        return failure;
    }

    @Override
    public void close() {
        closed = true;
        var children = process.descendants().toList();
        children.forEach(ProcessHandle::destroyForcibly);
        for (ProcessHandle child : children) {
            try {
                child.onExit().get(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // Continue reaping the launcher even if a child has already exited.
            }
        }
        process.destroyForcibly();
        worker.interrupt();
        try {
            process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            worker.join(2000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        latest.set(null);
    }
}
