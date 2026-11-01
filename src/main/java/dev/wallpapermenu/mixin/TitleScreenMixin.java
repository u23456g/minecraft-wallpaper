package dev.wallpapermenu.mixin;

import dev.wallpapermenu.WallpaperBackground;
import dev.wallpapermenu.WallpaperMenuClient;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) { super(title); }

    @Unique
    private float wallpaperMenu$titleOpacity() {
        int transparency = Math.clamp(WallpaperMenuClient.CONFIG.titleTransparency, 0, 100);
        return (100 - transparency) / 100.0f;
    }

    @Unique
    private void wallpaperMenu$scaleTitle(DrawContext context) {
        float scale = Math.clamp(WallpaperMenuClient.CONFIG.titleScalePercent, 50, 150) / 100.0f;
        context.getMatrices().push();
        context.getMatrices().translate(width / 2, 30, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.getMatrices().translate(-width / 2, -30, 0);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IF)V"))
    private void wallpaperMenu$logo(LogoDrawer logo, DrawContext context, int screenWidth, float alpha) {
        wallpaperMenu$scaleTitle(context);
        try {
            float opacity = wallpaperMenu$titleOpacity();
            if (opacity > 0) logo.draw(context, screenWidth, alpha * opacity);
        }
        finally { context.getMatrices().pop(); }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/SplashTextRenderer;render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/client/font/TextRenderer;I)V"))
    private void wallpaperMenu$splash(SplashTextRenderer splash, DrawContext context, int screenWidth, TextRenderer renderer, int alpha) {
        wallpaperMenu$scaleTitle(context);
        try {
            float opacity = wallpaperMenu$titleOpacity();
            int fadedAlpha = Math.round((alpha >>> 24) * opacity);
            // Text rendering treats near-zero alpha as opaque; skip it instead.
            if (fadedAlpha >= 4) splash.render(context, screenWidth, renderer, fadedAlpha << 24);
        }
        finally { context.getMatrices().pop(); }
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void wallpaperMenu$background(DrawContext context, float delta, CallbackInfo ci) {
        if (WallpaperBackground.render(context, width, height)) ci.cancel();
    }
}
