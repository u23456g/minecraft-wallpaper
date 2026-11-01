package dev.wallpapermenu.mixin;

import dev.wallpapermenu.WallpaperBackground;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenWebInputMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void wallpaperMenu$webMove(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (WallpaperBackground.isLoading(screen) || !net.minecraft.client.MinecraftClient.getInstance().isWindowFocused()) return;
        WallpaperBackground.dispatchMouse("mouseMoved", mouseX, mouseY, -1, 0);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void wallpaperMenu$clearInput(CallbackInfo ci) {
        WallpaperBackground.clearWebInputFocus();
    }

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true)
    private void wallpaperMenu$webKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (WallpaperBackground.dispatchKey("keyDown", keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }
}
