import dev.wallpapermenu.wallpaper.VideoDecoder;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/** Real FFmpeg regression: changing FPS while the previous decoder remains alive. */
public class VideoFpsSwitchSmoke {
    private static void awaitFrame(VideoDecoder decoder) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (decoder.failure() != null) throw new AssertionError(decoder.failure());
            byte[] frame = decoder.pollFrame();
            if (frame != null) {
                if (frame.length != VideoDecoder.WIDTH * VideoDecoder.HEIGHT * 4) throw new AssertionError("Incomplete frame");
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("No frame after FPS change");
    }
    public static void main(String[] args) throws Exception {
        var video = Files.createTempFile("wallpaper-fps-", ".mp4");
        try {
            var generator = new ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-y", "-f", "lavfi", "-i",
                    "testsrc2=size=160x90:rate=30", "-t", "0.5", "-pix_fmt", "yuv420p", video.toString()).inheritIO().start();
            if (!generator.waitFor(20, TimeUnit.SECONDS)) {
                generator.destroyForcibly();
                throw new AssertionError("Fixture generation timed out");
            }
            if (generator.exitValue() != 0) throw new AssertionError("Fixture generation failed");
            try (var old = new VideoDecoder(video, 60)) {
                awaitFrame(old);
                try (var next = new VideoDecoder(video, 120)) {
                    old.close();
                    awaitFrame(next);
                    try (var last = new VideoDecoder(video, 30)) {
                        next.close();
                        awaitFrame(last);
                    }
                }
            }
            System.out.println("PASS: 60 -> 120 -> 30 FPS produces complete frames after decoder switches");
        } finally {
            Files.deleteIfExists(video);
        }
    }
}
