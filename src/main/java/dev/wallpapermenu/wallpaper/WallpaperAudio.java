package dev.wallpapermenu.wallpaper;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Path;

/** Optional audio: errors never stop the video or the Minecraft render thread. */
public final class WallpaperAudio implements AutoCloseable {
    private volatile boolean closed;
    private volatile int volumePercent = 100;

    public void setVolume(int percent) { volumePercent = Math.clamp(percent, 0, 100); }

    public static void scalePcm(byte[] buffer, int count, int percent) {
        int gain = Math.clamp(percent, 0, 100);
        for (int i = 0; i + 1 < count; i += 2) {
            int sample = (short) ((buffer[i] & 255) | (buffer[i + 1] << 8));
            int scaled = sample * gain / 100;
            buffer[i] = (byte) scaled;
            buffer[i + 1] = (byte) (scaled >> 8);
        }
    }
    private volatile Process process;
    private volatile SourceDataLine line;
    private volatile String failure;
    private volatile long playedBytes;
    private final Thread worker;
    private final Thread shutdownHook;

    public WallpaperAudio(Path video) { this(video, 100); }

    public WallpaperAudio(Path video, int volume) {
        setVolume(volume);
        worker = new Thread(() -> play(video), "wallpaper-audio");
        worker.setDaemon(true);
        shutdownHook = new Thread(this::close, "wallpaper-audio-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        worker.start();
    }

    private void play(Path video) {
        try {
            process = new ProcessBuilder("ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error",
                    "-stream_loop", "-1", "-i", video.toAbsolutePath().toString(), "-vn",
                    "-f", "s16le", "-acodec", "pcm_s16le", "-ar", "48000", "-ac", "2", "pipe:1")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (closed) {
                cleanup();
                return;
            }
            try (var input = process.getInputStream()) {
                // Read before opening the device: a silent video needs no audio device.
                byte[] buffer = new byte[8192];
                int count = input.readNBytes(buffer, 0, buffer.length);
                if (closed || count == 0) return;
                line = AudioSystem.getSourceDataLine(new AudioFormat(48000, 16, 2, true, false));
                line.open();
                if (closed) return;
                line.start();
                do {
                    scalePcm(buffer, count, volumePercent);
                    int offset = 0;
                    while (!closed && offset < count) {
                        int written = line.write(buffer, offset, count - offset);
                        if (written <= 0) break;
                        offset += written;
                        playedBytes += written;
                    }
                } while (!closed && (count = input.readNBytes(buffer, 0, buffer.length)) > 0);
            }
        } catch (Exception exception) {
            if (!closed) failure = exception.toString();
        } finally {
            cleanup();
        }
    }

    public String failure() { return failure; }
    public long playedBytes() { return playedBytes; }

    private void cleanup() {
        SourceDataLine device = line;
        if (device != null) {
            device.stop();
            device.flush();
            device.close();
        }
        Process decoder = process;
        if (decoder != null) {
            decoder.descendants().forEach(ProcessHandle::destroyForcibly);
            decoder.destroyForcibly();
        }
    }

    @Override
    public void close() {
        closed = true;
        cleanup();
        worker.interrupt();
        boolean interrupted = false;
        while (worker.isAlive()) {
            try { worker.join(); }
            catch (InterruptedException exception) { interrupted = true; }
        }
        cleanup();
        try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
        catch (IllegalStateException ignored) { /* JVM shutdown already in progress. */ }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
