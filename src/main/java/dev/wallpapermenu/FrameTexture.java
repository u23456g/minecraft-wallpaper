package dev.wallpapermenu;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Raw frame texture: one GL upload per frame straight from a heap byte array.
 * Avoids the per-pixel NativeImage loop, so high-resolution wallpapers stay sharp and cheap.
 *
 * <p>The UNPACK pixel-store state is reset before every upload. Minecraft leaves
 * {@code GL_UNPACK_ROW_LENGTH} / skip values set by other upload paths, and a stale non-zero row length
 * makes the driver read past the end of {@link #staging} — that was an access violation, not a GL error.
 */
public final class FrameTexture extends AbstractTexture {
    private int width;
    private int height;
    private ByteBuffer staging;

    public FrameTexture(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.staging = MemoryUtil.memAlloc(this.width * this.height * 4);
    }

    public void upload(byte[] frame, boolean bgra) {
        if (frame == null || frame.length != width * height * 4) return;
        staging.clear();
        staging.put(frame);
        staging.flip();
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        int format = bgra ? GL12.GL_BGRA : GL11.GL_RGBA;
        if (glId == -1) {
            glId = GlStateManager._genTexture();
            GlStateManager._bindTexture(glId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    format, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        } else {
            GlStateManager._bindTexture(glId);
        }
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                format, GL11.GL_UNSIGNED_BYTE, staging);
        GlStateManager._bindTexture(0);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public void load(ResourceManager manager) {
        // Frames arrive from the decoder, never from a resource pack.
    }

    @Override
    public void close() {
        super.close();
        if (staging != null) {
            MemoryUtil.memFree(staging);
            staging = null;
        }
    }
}
