package com.xfive.live.hud;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Pop-up gaya Dynamic Island di bagian atas layar.
 * - Muncul saat musik mulai diputar.
 * - Melebar selama 3 detik saat lagu baru mulai, lalu menyusut menjadi pil kecil.
 * - Menghilang (memudar) saat musik dijeda atau berhenti.
 *
 * onTick() menentukan keadaan dari sisi game. render() hanya menggambar dan menganimasikan.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class DynamicIslandHud {
    private static final long EXPAND_MS = 3000L;
    private static final int COMPACT_W = 130;
    private static final int EXPANDED_W = 240;
    private static final int HEIGHT = 30;
    private static final int TOP = 6;
    private static final float SPEED = 4f; // perubahan per detik (0..1)

    private static boolean wasPlaying = false;
    private static PlaylistManager.Track lastTrack = null;
    private static long expandUntil = 0L;

    private static float visible = 0f; // 0 = hilang, 1 = tampil penuh
    private static float expand = 0f;  // 0 = kompak, 1 = lebar
    private static long lastRenderNs = 0L;

    private DynamicIslandHud() {
    }

    /** Dipanggil dari client tick. Hanya membaca status musik, tanpa menggambar. */
    public static void onTick() {
        boolean playing = XFiveLiveClient.MUSIC.isPlaying();
        PlaylistManager.Track track = XFiveLiveClient.MUSIC.currentTrack();
        if (playing && (!wasPlaying || track != lastTrack)) {
            expandUntil = System.currentTimeMillis() + EXPAND_MS;
        }
        wasPlaying = playing;
        lastTrack = track;
    }

    public static void render(GuiGraphicsExtractor g) {
        float dt = advanceTime();
        boolean show = XFiveLiveClient.CONFIG.enableMusic && wasPlaying;
        boolean wide = System.currentTimeMillis() < expandUntil;
        visible = approach(visible, show ? 1f : 0f, dt * SPEED);
        expand = approach(expand, wide ? 1f : 0f, dt * SPEED);
        if (visible <= 0.01f) {
            return;
        }

        float alpha = visible * XFiveLiveClient.CONFIG.hudOpacity * XFiveLiveClient.CONFIG.customTransparency;
        int accent = GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme);
        int w = Math.round(COMPACT_W + (EXPANDED_W - COMPACT_W) * expand);
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int x = (sw - w) / 2;

        // garis neon tipis di luar, lalu latar gelap transparan di dalam
        pill(g, x - 1, TOP - 1, w + 2, HEIGHT + 2, GlassPanelRenderer.argb(accent, alpha * 0.6f));
        pill(g, x, TOP, w, HEIGHT, GlassPanelRenderer.argb(GlassPanelRenderer.BACKGROUND, alpha));

        Font font = GlassPanelRenderer.font();
        PlaylistManager.Track t = XFiveLiveClient.MUSIC.currentTrack();
        String title = t == null ? "Memutar" : t.title();
        String artist = t == null ? "" : t.artist();

        if (expand < 0.5f) {
            String s = GlassPanelRenderer.trim(title, 18);
            int tx = x + (w - font.width(s)) / 2;
            GlassPanelRenderer.text(g, font, s, tx, TOP + (HEIGHT - 9) / 2, GlassPanelRenderer.WHITE);
        } else {
            GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(title, 30), x + 14, TOP + 5,
                    GlassPanelRenderer.WHITE);
            GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(artist, 34), x + 14, TOP + 15,
                    GlassPanelRenderer.MUTED);
        }

        long dur = XFiveLiveClient.MUSIC.durationMs();
        float progress = dur > 0 ? Math.min(1f, (float) XFiveLiveClient.MUSIC.positionMs() / dur) : 0f;
        int barX = x + 14;
        int barW = w - 28;
        int barY = TOP + HEIGHT - 5;
        g.fill(barX, barY, barX + barW, barY + 2, GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.15f * alpha));
        g.fill(barX, barY, barX + (int) (barW * progress), barY + 2, GlassPanelRenderer.argb(accent, alpha));
    }

    /** Menggambar pil (kapsul) dengan satu fill per baris, tanpa alokasi objek per frame. */
    private static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
        float r = h / 2f;
        for (int i = 0; i < h; i++) {
            float dy = i + 0.5f - r;
            float cap = (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
            int x1 = Math.round(x + r - cap);
            int x2 = Math.round(x + w - r + cap);
            if (x2 > x1) {
                g.fill(x1, y + i, x2, y + i + 1, argb);
            }
        }
    }

    private static float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    private static float advanceTime() {
        long now = System.nanoTime();
        float dt = lastRenderNs == 0L ? 0f : Math.min(0.1f, (now - lastRenderNs) / 1_000_000_000f);
        lastRenderNs = now;
        return dt;
    }
}
