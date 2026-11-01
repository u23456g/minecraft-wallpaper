package dev.wallpapermenu.wallpaper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live check that the Wallpaper Engine scene window really yields pixels. Skips when the engine or
 * a scene wallpaper is not installed, so CI stays green on machines without Wallpaper Engine.
 */
class SceneDecoderLiveTest {
    private static final Path SCENE = Path.of("E:\\Steam\\steamapps\\workshop\\content\\431960\\1128847064");

    @Test
    void capturesScenePixelsFromTheOffscreenWindow() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"), "Windows only");
        assumeTrue(Files.isRegularFile(SCENE.resolve("project.json")), "scene wallpaper not installed");

        byte[] frame;
        try (SceneDecoder decoder = new SceneDecoder(SCENE, 1280, 720, 30, false)) {
            long deadline = System.currentTimeMillis() + 25_000L;
            do {
                frame = decoder.pollFrame();
                if (frame == null) Thread.sleep(200L);
                if (decoder.failure() != null) throw new IllegalStateException(decoder.failure());
            } while (frame == null && System.currentTimeMillis() < deadline);
        }

        assertNotNull(frame, "no scene frame was captured");
        assertTrue(frame.length >= 1280 * 720 * 4, "scene frame has an unexpected size");
        Set<Integer> distinct = new HashSet<>();
        for (int i = 0; i + 3 < frame.length; i += 4 * 997) {
            distinct.add((frame[i] & 255) << 16 | (frame[i + 1] & 255) << 8 | (frame[i + 2] & 255));
        }
        assertTrue(distinct.size() > 8, "captured scene frame looks blank: " + distinct.size() + " colours");
    }
}
