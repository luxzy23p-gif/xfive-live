package com.xfive.live.music;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * Pemutar audio berbasis javax.sound.sampled.Clip. Clip memutar di thread miliknya sendiri,
 * jadi tidak memblokir render thread Minecraft.
 *
 * Batasan: javax.sound hanya tersedia di JVM desktop. Di Android (Zalith) API ini tidak
 * tersedia, dan MP3/OGG butuh decoder tambahan. Build ini hanya memutar WAV.
 */
public final class AudioPlayer {
    private Clip clip;
    private float volume = 0.8f;
    private long pausedAtMicros = 0L;
    private boolean wantPlaying = false;
    private String lastError = null;

    public boolean load(Path file) {
        close();
        lastError = null;
        if (file == null) {
            lastError = "Tidak ada file";
            return false;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".wav")) {
            lastError = "Format belum didukung di build ini (hanya WAV): " + name;
            return false;
        }
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(file.toFile())) {
            clip = AudioSystem.getClip();
            clip.open(stream); // Clip memuat seluruh data ke memori, stream aman ditutup setelahnya
            pausedAtMicros = 0L;
            applyVolume();
            return true;
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            close();
            return false;
        }
    }

    public boolean isLoaded() {
        return clip != null;
    }

    public void play() {
        if (clip == null) {
            return;
        }
        clip.setMicrosecondPosition(pausedAtMicros);
        clip.start();
        wantPlaying = true;
    }

    public void pause() {
        if (clip == null) {
            return;
        }
        if (clip.isRunning()) {
            pausedAtMicros = clip.getMicrosecondPosition();
        }
        clip.stop();
        wantPlaying = false;
    }

    public void stop() {
        if (clip == null) {
            return;
        }
        clip.stop();
        pausedAtMicros = 0L;
        clip.setMicrosecondPosition(0L);
        wantPlaying = false;
    }

    public void seekMs(long ms) {
        if (clip == null) {
            return;
        }
        long length = clip.getMicrosecondLength();
        long target = Math.max(0L, Math.min(length, ms * 1000L));
        pausedAtMicros = target;
        clip.setMicrosecondPosition(target);
    }

    public boolean isPlaying() {
        return clip != null && clip.isRunning();
    }

    /** True ketika lagu seharusnya berputar tetapi Clip sudah berhenti sendiri (lagu habis). */
    public boolean isFinished() {
        return wantPlaying && clip != null && !clip.isRunning();
    }

    public long positionMs() {
        if (clip == null) {
            return 0L;
        }
        long micros = clip.isRunning() ? clip.getMicrosecondPosition() : pausedAtMicros;
        return micros / 1000L;
    }

    public long durationMs() {
        return clip == null ? 0L : clip.getMicrosecondLength() / 1000L;
    }

    public void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
        applyVolume();
    }

    public float volume() {
        return volume;
    }

    public String lastError() {
        return lastError;
    }

    public void close() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
        wantPlaying = false;
        pausedAtMicros = 0L;
    }

    private void applyVolume() {
        if (clip == null || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float db = volume <= 0f ? gain.getMinimum() : (float) (20.0 * Math.log10(volume));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
    }
}
