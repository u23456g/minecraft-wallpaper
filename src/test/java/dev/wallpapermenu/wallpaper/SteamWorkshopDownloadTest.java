package dev.wallpapermenu.wallpaper;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SteamWorkshopDownloadTest {
    @Test void validatesArgumentsAndReadsOnlyTargetAppAndItemResults() {
        assertEquals(java.util.List.of("Steam.exe", "-silent", "+workshop_download_item", "431960", "123"),
                SteamWorkshopDownload.command(Path.of("Steam.exe"), "123"));
        assertThrows(IllegalArgumentException.class, () -> SteamWorkshopDownload.command(Path.of("Steam.exe"), "123 & bad"));
        assertEquals("", SteamWorkshopDownload.result("[AppID 999] Download item 123 result : OK", "123"));
        assertEquals("", SteamWorkshopDownload.result("[AppID 431960] Download item 124 result : OK", "123"));
        assertEquals("OK", SteamWorkshopDownload.result("[AppID 431960] Download item 123 result : OK\n", "123"));
        assertEquals("Access Denied", SteamWorkshopDownload.result("[AppID 431960] Download item 123 result : Access Denied", "123"));
    }
}
