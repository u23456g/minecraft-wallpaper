package dev.wallpapermenu.wallpaper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebInputMappingTest {
    @Test
    void mapsVisibleContainAreaToSourceCoordinates() {
        var point = WebInputMapping.map(480, 270, 0, 90, 960, 540, 960, 540).orElseThrow();
        assertEquals(480, point.x());
        assertEquals(180, point.y());
    }

    @Test
    void rejectsLetterboxAndRightBottomBoundary() {
        assertTrue(WebInputMapping.map(20, 100, 40, 0, 800, 450, 960, 540).isEmpty());
        assertTrue(WebInputMapping.map(840, 449, 40, 0, 800, 450, 960, 540).isEmpty());
    }
}
