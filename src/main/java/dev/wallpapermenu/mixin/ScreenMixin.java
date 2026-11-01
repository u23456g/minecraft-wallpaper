package dev.wallpapermenu.mixin;

import dev.wallpapermenu.NowPlayingOverlay;
import dev.wallpapermenu.WallpaperBackground;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow public int width;
    @Shadow public int height;
    @Shadow protected TextRenderer textRenderer;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void wallpaperMenu$background(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Object screen = this;
        MinecraftClient client = MinecraftClient.getInstance();
        boolean showWallpaper = !(screen instanceof GameMenuScreen);
        if ((showWallpaper || WallpaperBackground.isLoading((Screen) screen))
                && WallpaperBackground.render(context, width, height)) ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void wallpaperMenu$nowPlaying(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        NowPlayingOverlay.render(context, textRenderer, width, height);
    }
}
