package dev.wallpapermenu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.DefaultResourcePack;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Only replaces vanilla button backgrounds; resource-pack overrides always win. */
public final class GlassButtons {
    private static SimpleFramebuffer sample;
    private static final String[] BUTTONS = {"button", "button_disabled", "button_highlighted"};

    private GlassButtons() { }

    public static boolean available() {
        if (!WallpaperMenuClient.CONFIG.glassButtonsEnabled) return false;
        var resources = MinecraftClient.getInstance().getResourceManager();
        // ponytail: inspect the resolved resources, so reloads need no separate cache invalidation.
        for (String button : BUTTONS) {
            String path = "textures/gui/sprites/widget/" + button + ".png";
            for (String suffix : new String[]{"", ".mcmeta"}) {
                var resource = resources.getResource(Identifier.ofVanilla(path + suffix));
                if (resource.isPresent() && !(resource.get().getPack() instanceof DefaultResourcePack)) return false;
            }
        }
        return true;
    }

    public static void draw(DrawContext context, int x, int y, int width, int height, boolean selected, boolean active) {
        if (width <= 0 || height <= 0) return;
        var client = MinecraftClient.getInstance();
        var main = client.getFramebuffer();
        float widgetAlpha = RenderSystem.getShaderColor()[3];
        context.draw();
        int sw = Math.max(1, width / 4);
        int sh = Math.max(1, height / 4);
        if (sample == null) {
            sample = new SimpleFramebuffer(sw, sh, false, MinecraftClient.IS_SYSTEM_MAC);
            sample.setTexFilter(GL11.GL_LINEAR);
        } else if (sample.textureWidth != sw || sample.textureHeight != sh) {
            sample.resize(sw, sh, MinecraftClient.IS_SYSTEM_MAC);
            sample.setTexFilter(GL11.GL_LINEAR);
        }
        double scale = client.getWindow().getScaleFactor();
        int left = (int) Math.round(x * scale);
        int bottom = main.textureHeight - (int) Math.round((y + height) * scale);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sample.fbo);
        GL30.glBlitFramebuffer(left, bottom, (int) Math.round((x + width) * scale),
                main.textureHeight - (int) Math.round(y * scale), 0, 0, sw, sh,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        main.beginWrite(true);
        if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);

        float opacity = widgetAlpha * (1 - Math.clamp(WallpaperMenuClient.CONFIG.glassButtonTransparency, 0, 100) / 100f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, sample.getColorAttachment());
        RenderSystem.setShaderColor(1, 1, 1, opacity);
        var matrix = context.getMatrices().peek().getPositionMatrix();
        var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        float radius = Math.min(5, Math.min(width, height) / 2f);
        // Half-GUI-pixel strips clip both the sampled background and the overlay to the same curve.
        for (float row = 0; row < height; row += 0.5f) {
            float end = Math.min(height, row + 0.5f);
            float inset = cornerInset((row + end) / 2, height, radius);
            float u0 = inset / width;
            float u1 = 1 - u0;
            buffer.vertex(matrix, x + inset, y + end, 0).texture(u0, 1 - end / height);
            buffer.vertex(matrix, x + width - inset, y + end, 0).texture(u1, 1 - end / height);
            buffer.vertex(matrix, x + width - inset, y + row, 0).texture(u1, 1 - row / height);
            buffer.vertex(matrix, x + inset, y + row, 0).texture(u0, 1 - row / height);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.setShaderColor(1, 1, 1, 1);
        int tint = Math.round(opacity * (selected ? 100 : 65));
        int fill = (tint << 24) | (active ? 0xE0EAF5 : 0x77808A);
        int border = (Math.round(opacity * (selected ? 210 : 100)) << 24) | 0xFFFFFF;
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var overlay = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (float row = 0; row < height; row += 0.5f) {
            float end = Math.min(height, row + 0.5f);
            float mid = (row + end) / 2;
            float outer = cornerInset(mid, height, radius);
            if (mid < 1 || mid > height - 1) {
                strip(overlay, matrix, x + outer, x + width - outer, y + row, y + end, border);
            } else {
                float inner = 1 + cornerInset(mid - 1, height - 2, Math.max(0, radius - 1));
                strip(overlay, matrix, x + outer, x + inner, y + row, y + end, border);
                strip(overlay, matrix, x + inner, x + width - inner, y + row, y + end, fill);
                strip(overlay, matrix, x + width - inner, x + width - outer, y + row, y + end, border);
            }
        }
        BufferRenderer.drawWithGlobalProgram(overlay.end());
        RenderSystem.enableDepthTest();
    }

    private static float cornerInset(float row, float height, float radius) {
        float distance = Math.max(0, radius - Math.min(row, height - row));
        return radius - (float) Math.sqrt(Math.max(0, radius * radius - distance * distance));
    }

    private static void strip(net.minecraft.client.render.BufferBuilder buffer, org.joml.Matrix4f matrix,
                              float left, float right, float top, float bottom, int color) {
        if (right <= left) return;
        buffer.vertex(matrix, left, bottom, 0).color(color);
        buffer.vertex(matrix, right, bottom, 0).color(color);
        buffer.vertex(matrix, right, top, 0).color(color);
        buffer.vertex(matrix, left, top, 0).color(color);
    }

    public static void close() {
        if (sample != null) {
            sample.delete();
            sample = null;
        }
    }
}
