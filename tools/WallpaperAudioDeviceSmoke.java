import dev.wallpapermenu.wallpaper.WallpaperAudio;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Real audio-device write check using ONLY silent PCM, not an audible tone. */
public class WallpaperAudioDeviceSmoke {
    public static void main(String[] args) throws Exception {
        Path video = Files.createTempFile("wallpaper-silent-track-", ".mp4");
        try {
            Process generator = new ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-y",
                    "-f", "lavfi", "-i", "color=size=160x90:rate=10",
                    "-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo", "-t", "0.2",
                    "-c:a", "aac", video.toString()).inheritIO().start();
            if (!generator.waitFor(20, TimeUnit.SECONDS)) {
                generator.destroyForcibly();
                throw new AssertionError("Fixture generation timed out");
            }
            if (generator.exitValue() != 0) throw new AssertionError("Fixture generation failed");
            try (WallpaperAudio audio = new WallpaperAudio(video)) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (audio.playedBytes() < 96000 && System.nanoTime() < deadline) {
                    if (audio.failure() != null) throw new AssertionError(audio.failure());
                    Thread.sleep(20);
                }
                if (audio.playedBytes() < 96000) throw new AssertionError("No PCM device writes");
                System.out.println("PASS: silent audio track loop wrote " + audio.playedBytes() + " bytes to SourceDataLine");
            }
        } finally {
            // The audio worker owns subprocess teardown; wait boundedly for its cleanup.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.isAlive() && t.getName().equals("wallpaper-audio"))
                    && System.nanoTime() < deadline) Thread.sleep(20);
            Files.deleteIfExists(video);
        }
    }
}
