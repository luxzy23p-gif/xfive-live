package com.xfive.live.music;

import java.nio.file.Path;
import java.util.Random;

/**
 * Menggabungkan playlist dan pemutar: putar, jeda, berhenti, berikutnya, sebelumnya,
 * mode ulang (mati, semua, satu), acak, dan volume.
 */
public final class MusicManager {
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;
    private static final long RESTART_THRESHOLD_MS = 3_000L;

    private final PlaylistManager playlist = new PlaylistManager();
    private final AudioPlayer player = new AudioPlayer();
    private final Random random = new Random();
    private Path folder;
    private String status = "Belum ada musik";
    private PlaylistManager.Track loadedTrack;
    private boolean error = false;
    private int repeatMode = REPEAT_ALL;
    private boolean shuffle = false;

    public int setFolder(Path dir) {
        player.stop();
        folder = dir;
        loadedTrack = null;
        int n = playlist.scan(dir);
        status = n == 0 ? "Folder kosong atau tidak ditemukan" : n + " lagu";
        return n;
    }

    public Path folder() {
        return folder;
    }

    /** Memakai daftar sumber campuran (folder dan file). Dipanggil saat startup. */
    public int setSources(java.util.List<Path> sources) {
        player.stop();
        loadedTrack = null;
        int n = playlist.scanSources(sources);
        status = n == 0 ? "Belum ada lagu. Isi folder xfive-music." : n + " lagu";
        return n;
    }

    /** Memuat dan memutar lagu aktif. Mengembalikan false jika gagal (lihat status()). */
    public boolean playCurrent() {
        PlaylistManager.Track track = playlist.current();
        if (track == null) {
            status = "Playlist kosong";
            return false;
        }
        if (loadedTrack == null || !loadedTrack.path().equals(track.path())) {
            if (!player.load(track.path())) {
                status = "Gagal: " + player.lastError();
                error = true;
                loadedTrack = null;
                return false;
            }
            loadedTrack = track;
        }
        player.setVolume(player.volume());
        player.play();
        if (!player.isPlaying()) {
            status = "Gagal mulai: " + player.debugState();
            error = true;
            return false;
        }
        error = false;
        status = "Memutar";
        return true;
    }

    /** Memperbarui daftar lagu TANPA menghentikan lagu yang sedang diputar. */
    public int refresh(java.util.List<Path> sources) {
        PlaylistManager.Track before = playlist.current();
        int n = playlist.scanSources(sources);
        if (before != null) {
            java.util.List<PlaylistManager.Track> list = playlist.tracks();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).path().equals(before.path())) {
                    playlist.select(i);
                    break;
                }
            }
        }
        // Status dari pemutaran (Memutar, Dijeda, Berhenti) tidak ditimpa saat menu dibuka.
        if (loadedTrack == null && !error) {
            status = n == 0 ? "Belum ada lagu. Isi folder xfive-music." : n + " lagu";
        }
        return n;
    }

    public void togglePause() {
        if (player.isPlaying()) {
            player.pause();
            status = "Dijeda";
        } else if (player.isLoaded()) {
            player.play();
            status = "Memutar";
        } else {
            playCurrent();
        }
    }

    /** Menghentikan lagu dan mengembalikan posisi ke awal. Putar lagi akan mulai dari awal. */
    public void stop() {
        player.stop();
        if (playlist.current() != null) {
            status = "Berhenti";
        }
    }

    public void next() {
        int size = playlist.size();
        if (shuffle && size > 1) {
            int current = playlist.currentIndex();
            int pick;
            do {
                pick = random.nextInt(size);
            } while (pick == current);
            playlist.select(pick);
        } else {
            playlist.next();
        }
        playCurrent();
    }

    public void previous() {
        if (player.positionMs() > RESTART_THRESHOLD_MS) {
            player.seekMs(0L);
        } else {
            playlist.previous();
            playCurrent();
        }
    }

    /** Pindah ke posisi tertentu di lagu, dengan fraction 0..1. */
    public void seekTo(float fraction) {
        long dur = player.durationMs();
        if (dur > 0) {
            player.seekMs((long) (Math.max(0f, Math.min(1f, fraction)) * dur));
        }
    }

    public void setVolume(float volume) {
        player.setVolume(volume);
    }

    public float volume() {
        return player.volume();
    }

    /** Dipanggil dari client tick. Mengurus lagu yang sudah habis sesuai mode ulang. */
    public void tick() {
        if (!player.isFinished()) {
            return;
        }
        if (repeatMode == REPEAT_ONE && loadedTrack != null) {
            player.stop();
            playCurrent();
            return;
        }
        boolean lastTrack = playlist.currentIndex() >= playlist.size() - 1;
        if (repeatMode == REPEAT_OFF && !shuffle && lastTrack) {
            player.stop();
            status = "Selesai";
            return;
        }
        next();
    }

    public int repeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(int mode) {
        repeatMode = Math.max(REPEAT_OFF, Math.min(REPEAT_ONE, mode));
    }

    /** Pindah ke mode ulang berikutnya: mati, semua, satu, lalu mati lagi. */
    public int cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3;
        return repeatMode;
    }

    public boolean shuffle() {
        return shuffle;
    }

    public void setShuffle(boolean on) {
        shuffle = on;
    }

    public boolean toggleShuffle() {
        shuffle = !shuffle;
        return shuffle;
    }

    public void setStatus(String text) {
        status = text;
    }

    public PlaylistManager.Track currentTrack() {
        return playlist.current();
    }

    public long positionMs() {
        return player.positionMs();
    }

    public long durationMs() {
        return player.durationMs();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    /** True jika pemutaran terakhir gagal. Dipakai pop-up untuk menampilkan pesan error. */
    public boolean hasError() {
        return error;
    }

    public String status() {
        return status;
    }

    public PlaylistManager playlist() {
        return playlist;
    }

    public void shutdown() {
        player.close();
    }
}
