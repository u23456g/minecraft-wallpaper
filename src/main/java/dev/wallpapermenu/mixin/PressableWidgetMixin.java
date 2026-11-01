package dev.wallpapermenu.mixin;

import dev.wallpapermenu.GlassButtons;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {
    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"))
    private void wallpaperMenu$glass(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        var widget = (PressableWidget) (Object) this;
        if (GlassButtons.available()) {
            GlassButtons.draw(context, x, y, width, height, widget.isSelected(), widget.active);
        } else {
            context.drawGuiTexture(texture, x, y, width, height);
        }
    }
}
