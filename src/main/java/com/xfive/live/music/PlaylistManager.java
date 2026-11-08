package com.xfive.live.music;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Daftar lagu dari satu folder. Folder dipindai hanya saat scan() dipanggil
 * (bukan per frame). Nama file "Artis - Judul.mp3" dibaca sebagai artis dan judul.
 */
public final class PlaylistManager {
    public static final List<String> SUPPORTED_EXTENSIONS = List.of(".mp3", ".wav", ".ogg");

    public record Track(Path path, String title, String artist) {
    }

    private final List<Track> tracks = new ArrayList<>();
    private int index = -1;

    public static boolean isSupported(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public synchronized int scan(Path dir) {
        tracks.clear();
        index = -1;
        if (dir == null || !Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(PlaylistManager::isSupported)
                    .sorted()
                    .forEach(p -> tracks.add(parse(p)));
        } catch (IOException e) {
            tracks.clear();
        }
        if (!tracks.isEmpty()) {
            index = 0;
        }
        return tracks.size();
    }

    static Track parse(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        int sep = base.indexOf(" - ");
        if (sep > 0) {
            return new Track(file, base.substring(sep + 3).strip(), base.substring(0, sep).strip());
        }
        return new Track(file, base.strip(), "Unknown Artist");
    }

    public synchronized int size() {
        return tracks.size();
    }

    public synchronized List<Track> tracks() {
        return List.copyOf(tracks);
    }

    public synchronized int currentIndex() {
        return index;
    }

    public synchronized Track current() {
        return index >= 0 && index < tracks.size() ? tracks.get(index) : null;
    }

    public synchronized Track next() {
        if (tracks.isEmpty()) {
            return null;
        }
        index = (index + 1) % tracks.size();
        return tracks.get(index);
    }

    public synchronized Track previous() {
        if (tracks.isEmpty()) {
            return null;
        }
        index = (index - 1 + tracks.size()) % tracks.size();
        return tracks.get(index);
    }

    public synchronized boolean select(int newIndex) {
        if (newIndex < 0 || newIndex >= tracks.size()) {
            return false;
        }
        index = newIndex;
        return true;
    }
}
