import dev.wallpapermenu.wallpaper.*;
import java.nio.file.*;
import java.util.*;

/** Explicit live Steam test. Simulates an empty initial list; never deletes installed wallpapers. */
public class WorkshopAcquisitionSmoke {
    public static void main(String[] args) throws Exception {
        Path steam = Path.of(args[0]);
        String id = args[1];
        Path isolated = Files.createTempDirectory("wallpaper-acquisition-");
        System.out.println("Isolated configuration: " + isolated);
        Path manifest = WallpaperConfig.packManifest(isolated);
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{\"defaultWallpaperId\":\"" + id
                + "\",\"workshopItemId\":\"" + id + "\",\"settings\":{\"glassButtonTransparency\":72}}");
        WallpaperConfig player = WallpaperConfig.load(isolated.resolve("wallpaper-menu.json"));
        player.loadPackManifest(isolated);
        if (!player.needsWorkshop(List.of())) throw new AssertionError("Missing-item branch not reached");
        if (player.glassButtonTransparency != 72) throw new AssertionError("Author settings not applied");
        System.out.println("PASS: simulated missing item and fresh-player preset loaded");
        SteamWorkshopDownload.request(List.of(steam), id);
        System.out.println("PASS: current Steam request reports OK");
        var entries = new WallpaperScanner().scan(SteamWallpaperLocator.discover(List.of(steam)));
        var selected = entries.stream().filter(e -> e.supported() && e.id().equals(player.selectedWallpaperId))
                .findFirst().orElseThrow(() -> new AssertionError("Downloaded project not discoverable"));
        if (player.needsWorkshop(entries)) throw new AssertionError("Acquisition still missing");
        player.save(isolated.resolve("wallpaper-menu.json"));
        System.out.println("PASS: preset resolves to " + selected.mediaFile());
        System.out.println("LIMIT: existing subscribed project; no clean download or Minecraft rendering assertion");
    }
}
