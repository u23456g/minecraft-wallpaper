import dev.wallpapermenu.wallpaper.VideoDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Corrupt media must fail boundedly without leaking a decoder or file handle. */
public class VideoDecoderFailureSmoke {
    public static void main(String[] args) throws Exception {
        Path video = Files.createTempFile("wallpaper-corrupt-", ".mp4");
        try {
            Files.writeString(video, "not an mp4");
            try (VideoDecoder decoder = new VideoDecoder(video)) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (decoder.failure() == null && System.nanoTime() < deadline) Thread.sleep(20);
                if (decoder.failure() == null) throw new AssertionError("Corrupt video did not fail within 10 seconds");
                if (decoder.pollFrame() != null) throw new AssertionError("Corrupt input produced a frame");
                System.out.println("Observed expected decode failure: " + decoder.failure());
            }
            Files.delete(video);
            System.out.println("PASS: corrupt MP4 failure reported; decoder closed and input deleted");
        } finally {
            Files.deleteIfExists(video);
        }
    }
}
