package com.xfive.live.music;

import java.nio.file.Path;

/** Menggabungkan playlist dan pemutar: play, pause, next, previous, volume, dan auto-next. */
public final class MusicManager {
    private static final long RESTART_THRESHOLD_MS = 3_000L;

    private final PlaylistManager playlist = new PlaylistManager();
    private final AudioPlayer player = new AudioPlayer();
    private Path folder;
    private String status = "Belum ada musik";
    private PlaylistManager.Track loadedTrack;
    private boolean error = false;

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
        if (loadedTrack != track) {
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

    public void next() {
        playlist.next();
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

    public void setVolume(float volume) {
        player.setVolume(volume);
    }

    public float volume() {
        return player.volume();
    }

    /** Dipanggil dari client tick. Pindah ke lagu berikutnya saat lagu habis. */
    public void tick() {
        if (player.isFinished()) {
            next();
        }
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
