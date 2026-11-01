import dev.wallpapermenu.wallpaper.WallpaperAudio;
import dev.wallpapermenu.wallpaper.WallpaperConfig;
import java.nio.file.Files;

/** Explicit smoke test for PCM volume and legacy configuration migration. */
public class VolumeSmoke {
    public static void main(String[] args) throws Exception {
        for (int volume : new int[]{0, 25, 50, 100}) {
            byte[] samples = {0, 64, 0, (byte) 192}; // +16384, -16384, little endian.
            WallpaperAudio.scalePcm(samples, samples.length, volume);
            int positive = (short) ((samples[0] & 255) | samples[1] << 8);
            int negative = (short) ((samples[2] & 255) | samples[3] << 8);
            if (positive != 16384 * volume / 100 || negative != -16384 * volume / 100)
                throw new AssertionError("PCM gain: " + volume);
        }
        var file = Files.createTempFile("wallpaper-volume-", ".json");
        try {
            Files.writeString(file, "{\"audioEnabled\":true}");
            if (WallpaperConfig.load(file).audioVolume() != 100) throw new AssertionError("Legacy on");
            Files.writeString(file, "{\"audioEnabled\":false}");
            if (WallpaperConfig.load(file).audioVolume() != 0) throw new AssertionError("Legacy off");
            Files.writeString(file, "{\"audioEnabled\":true,\"audioVolumePercent\":25}");
            var config = WallpaperConfig.load(file);
            if (config.audioVolume() != 25) throw new AssertionError("Explicit volume");
            config.save(file);
            if (WallpaperConfig.load(file).audioVolume() != 25) throw new AssertionError("Round trip");
        } finally { Files.deleteIfExists(file); }
        System.out.println("PASS: PCM 0/25/50/100%, legacy on/off, explicit volume and save round trip");
    }
}
