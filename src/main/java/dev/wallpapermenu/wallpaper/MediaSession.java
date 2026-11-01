package dev.wallpapermenu.wallpaper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reads the Windows system media session (what Spotify/browsers report to SMTC) so the menu can
 * show the current song title and artist.
 *
 * <p>Minecraft runs on Java 21 where the WinRT projections are not callable, so the query is done
 * by a short-lived PowerShell helper on a background thread and cached here.
 */
public final class MediaSession {
    private static final long POLL_INTERVAL_MS = 4_000L;
    private static volatile String title = "";
    private static volatile String artist = "";
    private static volatile boolean started;
    private static volatile boolean loggedFirstPoll;

    private MediaSession() {
    }

    /** One media session as reported by Windows. */
    public record Track(String status, String title, String artist) {
        public boolean playing() {
            return "Playing".equalsIgnoreCase(status);
        }
    }

    public static synchronized void start() {
        if (started) return;
        started = true;
        Thread worker = new Thread(MediaSession::pollLoop, "wallpaper-media-session");
        worker.setDaemon(true);
        worker.start();
    }

    private static void pollLoop() {
        while (true) {
            try {
                Track track = selectTrack(query());
                String nextTitle = track == null ? "" : track.title();
                String nextArtist = track == null ? "" : track.artist();
                if (!nextTitle.equals(title) || !nextArtist.equals(artist)) {
                    dev.wallpapermenu.WallpaperMenuClient.LOGGER.info("MediaSession track: {} | {}", nextTitle, nextArtist);
                }
                title = nextTitle;
                artist = nextArtist;
            } catch (RuntimeException exception) {
                dev.wallpapermenu.WallpaperMenuClient.LOGGER.warn("MediaSession poll failed", exception);
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static List<Track> query() {
        ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-Command", SCRIPT);
        builder.redirectErrorStream(true);
        List<Track> tracks = new ArrayList<>();
        try {
            Process process = builder.start();
            String output;
            try (var stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            process.waitFor(6, TimeUnit.SECONDS);
            for (String line : output.split("\\R")) {
                Track parsed = parseLine(line);
                if (parsed != null) tracks.add(parsed);
            }
            if (!loggedFirstPoll) {
                loggedFirstPoll = true;
                dev.wallpapermenu.WallpaperMenuClient.LOGGER.info(
                        "MediaSession probe: {} chars, {} track(s), raw=[{}]", output.length(), tracks.size(), output.strip());
            }
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return tracks;
    }

    /**
     * Prefers the session that is actually playing. A paused session only counts when it reports an
     * artist, because idle launchers publish a paused session with no artist and would otherwise
     * leave junk text on the menu forever.
     */
    public static Track selectTrack(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) return null;
        Track pausedFallback = null;
        for (Track track : tracks) {
            if (track == null || track.title() == null || track.title().isBlank()) continue;
            if (track.playing()) return track;
            if (pausedFallback == null && track.artist() != null && !track.artist().isBlank()) {
                pausedFallback = track;
            }
        }
        return pausedFallback;
    }

    public static String title() {
        return title;
    }

    public static String artist() {
        return artist;
    }

    /** "Title — Artist" when a track is available, otherwise an empty string. */
    public static String displayText() {
        String currentTitle = title;
        if (currentTitle.isEmpty()) return "";
        String currentArtist = artist;
        return currentArtist.isEmpty() ? currentTitle : currentTitle + " — " + currentArtist;
    }

    /** Parses one PowerShell SMTC line; exposed for tests. */
    public static Track parseLine(String line) {
        if (line == null || !line.startsWith("S|")) return null;
        String[] parts = line.split("\\|", 4);
        String status = parts.length > 1 ? parts[1].trim() : "";
        String trackTitle = parts.length > 2 ? parts[2].trim() : "";
        String trackArtist = parts.length > 3 ? parts[3].trim() : "";
        return new Track(status, trackTitle, trackArtist);
    }

    private static final String SCRIPT = String.join("; ", List.of(
            "Add-Type -AssemblyName System.Runtime.WindowsRuntime",
            "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null",
            "$g = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
            "function Await-WinRt($operation, $resultType) { $task = $g.MakeGenericMethod($resultType).Invoke($null, @($operation)); if (-not $task.Wait(3000)) { return $null }; return $task.Result }",
            "$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]",
            "$manager = Await-WinRt ($managerType::RequestAsync()) $managerType",
            "foreach ($session in $manager.GetSessions()) { $props = Await-WinRt ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]); if ($props -and $props.Title) { Write-Output ('S|' + $session.GetPlaybackInfo().PlaybackStatus + '|' + $props.Title + '|' + $props.Artist) } }"));
}
