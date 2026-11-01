package dev.wallpapermenu.mixin;

import dev.wallpapermenu.WallpaperBackground;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParentElement.class)
public interface WebInputMixin {
    @Inject(method = "mouseClicked", at = @At("RETURN"), cancellable = true)
    private void wallpaperMenu$webClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!(this instanceof Screen)) return;
        if (cir.getReturnValueZ()) {
            WallpaperBackground.clearWebInputFocus();
            return;
        }
        if (WallpaperBackground.dispatchMouse("mousePressed", mouseX, mouseY, button, 1)) {
            cir.setReturnValue(true);
        } else {
            WallpaperBackground.clearWebInputFocus();
        }
    }

    @Inject(method = "mouseReleased", at = @At("RETURN"))
    private void wallpaperMenu$webRelease(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof Screen) WallpaperBackground.dispatchMouse("mouseReleased", mouseX, mouseY, button, 1);
    }

    @Inject(method = "mouseDragged", at = @At("RETURN"))
    private void wallpaperMenu$webDrag(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof Screen) WallpaperBackground.dispatchMouse("mouseMoved", mouseX, mouseY, button, 1);
    }

    @Inject(method = "mouseScrolled", at = @At("RETURN"))
    private void wallpaperMenu$webScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof Screen && !cir.getReturnValueZ()) WallpaperBackground.dispatchScroll(mouseX, mouseY, horizontalAmount * 100, -verticalAmount * 100);
    }

    @Inject(method = "keyReleased", at = @At("RETURN"))
    private void wallpaperMenu$keyRelease(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof Screen) WallpaperBackground.dispatchKey("keyUp", keyCode, scanCode, modifiers);
    }

    @Inject(method = "charTyped", at = @At("RETURN"))
    private void wallpaperMenu$webText(char character, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof Screen && !Character.isISOControl(character)) WallpaperBackground.dispatchText(String.valueOf(character));
    }
}
