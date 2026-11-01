package dev.wallpapermenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.wallpapermenu.wallpaper.WallpaperEntry;
import net.minecraft.client.gui.screen.Screen;

public final class WallpaperMenuModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WallpaperSettingsScreen::new;
    }

    static String label(WallpaperEntry entry) {
        return entry.title() + " [" + entry.type().name().toLowerCase(java.util.Locale.ROOT) + "]";
    }
}
