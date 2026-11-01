package dev.wallpapermenu.wallpaper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FrameBudgetTest {
    @Test
    void matchesHighResolutionFramebuffersInsteadOfUpscaling() {
        assertArrayEquals(new int[]{2560, 1440}, FrameBudget.captureSize(2560));
    }

    @Test
    void clampsSmallAndOversizedFramebuffers() {
        assertArrayEquals(new int[]{1920, 1080}, FrameBudget.captureSize(1280));
        assertArrayEquals(new int[]{2560, 1440}, FrameBudget.captureSize(3840));
    }

    @Test
    void keepsEvenWidths() {
        assertEquals(1920, FrameBudget.clampEven(1921));
        assertEquals(2560, FrameBudget.clampEven(2561));
    }

    @Test
    void parsesMediaSessionLines() {
        MediaSession.Track track = MediaSession.parseLine("S|Playing|Song Title|Artist");
        assertEquals("Playing", track.status());
        assertEquals("Song Title", track.title());
        assertEquals("Artist", track.artist());

        assertEquals("", MediaSession.parseLine("S|Paused|Instrumental|").artist());
        assertNull(MediaSession.parseLine("warning: nothing playing"));
        assertNull(MediaSession.parseLine(null));
    }

    @Test
    void prefersPlayingSessionButFallsBackToPaused() {
        MediaSession.Track paused = new MediaSession.Track("Paused", "Old track", "A");
        MediaSession.Track playing = new MediaSession.Track("Playing", "Live track", "B");
        assertEquals(playing, MediaSession.selectTrack(List.of(paused, playing)));
        assertEquals(paused, MediaSession.selectTrack(List.of(paused)));
        assertNull(MediaSession.selectTrack(List.of(new MediaSession.Track("Playing", "  ", "B"))));
        assertNull(MediaSession.selectTrack(List.of(new MediaSession.Track("Paused", "Launcher noise", ""))));
        assertNull(MediaSession.selectTrack(List.of()));
    }
}
