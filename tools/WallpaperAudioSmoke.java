import dev.wallpapermenu.wallpaper.WallpaperAudio;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Silent fixture only: never intentionally sends sound to the user's speakers. */
public class WallpaperAudioSmoke {
    public static void main(String[] args) throws Exception {
        Path video = Files.createTempFile("wallpaper-no-audio-", ".mp4");
        try {
            Process generator = new ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-y",
                    "-f", "lavfi", "-i", "color=size=160x90:rate=10", "-t", "0.2",
                    "-an", video.toString()).inheritIO().start();
            if (!generator.waitFor(20, TimeUnit.SECONDS)) {
                generator.destroyForcibly();
                throw new AssertionError("Fixture generation timed out");
            }
            if (generator.exitValue() != 0) throw new AssertionError("Fixture generation failed");
            try (WallpaperAudio audio = new WallpaperAudio(video)) {
                Thread.sleep(1000);
                if (audio.failure() != null) throw new AssertionError(audio.failure());
            }
            for (int i = 0; i < 10; i++) new WallpaperAudio(video).close();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean alive;
            do {
                alive = Thread.getAllStackTraces().keySet().stream()
                        .anyMatch(t -> t.isAlive() && t.getName().equals("wallpaper-audio"));
                if (alive) Thread.sleep(20);
            } while (alive && System.nanoTime() < deadline);
            if (alive) throw new AssertionError("Audio thread leaked");
            Files.delete(video);
            System.out.println("PASS: no-audio video, immediate close x10, no worker/file leak; speakers not tested");
        } finally {
            Files.deleteIfExists(video);
        }
    }
}
