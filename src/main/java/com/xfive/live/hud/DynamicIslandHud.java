package com.xfive.live.hud;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Pop-up gaya Dynamic Island di bagian atas layar, dengan dua tampilan:
 * - Kartu: sampul, judul, artis, gelombang suara, bar progres, waktu, dan tombol sebelumnya/jeda/berikutnya.
 * - Pil: kapsul kecil berisi judul, melebar sebentar saat lagu baru mulai.
 * Muncul saat musik diputar (atau saat jeda jika "tampil saat jeda" aktif).
 * Tombol di kartu hanya bisa diketuk di layar kontrol (F7), karena HUD di game tidak menerima klik.
 *
 * onTick() menentukan keadaan dari sisi game. render() hanya menggambar dan menganimasikan.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class DynamicIslandHud {
    private static final long EXPAND_MS = 3000L;
    private static final int BASE_COMPACT_W = 130;
    private static final int BASE_EXPANDED_W = 240;
    private static final int BASE_HEIGHT = 30;
    private static final int CARD_H = 84; // tinggi dasar. Isi kartu mengikuti tinggi yang dipilih.
    private static final float POP_SECONDS = 1.1f;
    public static final int TOP = 6;
    private static final float SPEED = 4f; // perubahan per detik (0..1)

    /** Pilihan warna. Indeks 0 mengikuti tema, indeks lain memakai warna tetap. */
    public static final String[] COLOR_NAMES = {"Ikut tema", "Ungu", "Cyan", "Pink", "Hijau", "Kuning"};
    private static final int[] COLORS = {0, 0x965CFF, 0x40DFFF, 0xFF4FD8, 0x5CFF8A, 0xFFD84D};

    private static boolean wasPlaying = false;
    private static PlaylistManager.Track lastTrack = null;
    private static long expandUntil = 0L;
    private static long popStart = 0L;

    private static float visible = 0f; // 0 = hilang, 1 = tampil penuh
    private static float expand = 0f;  // 0 = kompak, 1 = lebar (hanya tampilan pil)
    private static long lastRenderNs = 0L;
    private static boolean lastShowTarget = false;

    private DynamicIslandHud() {
    }

    /** Warna aksen Dynamic Island saat ini. */
    public static int accentColor() {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        int i = c.islandColor;
        if (i <= 0 || i >= COLORS.length) {
            return GlassPanelRenderer.accent(c.theme);
        }
        return COLORS[i];
    }

    /** Menaruh kotak berukuran w x h di posisi tersimpan. Nilai -1 berarti tengah atas layar. */
    private static int[] place(int w, int h) {
        int[] pos = XFiveLiveClient.CONFIG.hudPositions.get("dynamicIsland");
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int x = pos[0] < 0 ? (sw - w) / 2 : pos[0];
        int y = pos[1] < 0 ? TOP : pos[1];
        return new int[] {x, y, w, h};
    }

    /** Kotak kartu lengkap: {x, y, lebar, tinggi}. */
    public static int[] cardBox() {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        return place(c.islandW, c.islandH);
    }

    /** Kotak Dynamic Island saat ini, sesuai tampilan yang dipilih (kartu atau pil). */
    public static int[] box() {
        if (XFiveLiveClient.CONFIG.islandCard) {
            return cardBox();
        }
        XFiveConfig c = XFiveLiveClient.CONFIG;
        return place(c.islandW, Math.max(24, Math.round(c.islandH * BASE_HEIGHT / (float) CARD_H)));
    }

    /** Dipanggil dari client tick. Hanya membaca status musik, tanpa menggambar. */
    public static void onTick() {
        boolean playing = XFiveLiveClient.MUSIC.isPlaying();
        PlaylistManager.Track track = XFiveLiveClient.MUSIC.currentTrack();
        boolean loading = XFiveLiveClient.MUSIC.isLoading();
        boolean changed = track != null && !sameTrack(track, lastTrack) && (playing || loading);
        if (playing && (!wasPlaying || !sameTrack(track, lastTrack))) {
            expandUntil = System.currentTimeMillis() + EXPAND_MS;
        }
        boolean showTarget = XFiveLiveClient.CONFIG.enableMusic
                && (playing || XFiveLiveClient.MUSIC.hasError() || loading
                || (XFiveLiveClient.CONFIG.islandShowPaused && track != null));
        if (changed || (showTarget && !lastShowTarget)) {
            popStart = System.currentTimeMillis();
        }
        lastShowTarget = showTarget;
        wasPlaying = playing;
        lastTrack = track;
    }

    public static void render(GuiGraphicsExtractor g) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        float dt = advanceTime();
        boolean failed = XFiveLiveClient.MUSIC.hasError();
        PlaylistManager.Track t = XFiveLiveClient.MUSIC.currentTrack();
        boolean show = c.enableMusic && (wasPlaying || failed || XFiveLiveClient.MUSIC.isLoading() || (c.islandShowPaused && t != null));
        boolean wide = failed || System.currentTimeMillis() < expandUntil;
        // Frame-rate-independent smoothing prevents visible jumps when FPS fluctuates.
        // Clamp long pauses (screen changes / lag spikes) so animation never snaps abruptly.
        float smoothDt = Math.min(dt, 0.05f);
        visible = smoothApproach(visible, show ? 1f : 0f, smoothDt, 9.0f);
        expand = smoothApproach(expand, wide ? 1f : 0f, smoothDt, SPEED * 2.2f);
        if (visible <= 0.01f) {
            return;
        }

        float age = c.animations ? (System.currentTimeMillis() - popStart) / 1000f : 99f;
        float pt = clamp01(age / POP_SECONDS);
        float k = 0.72f + 0.28f * easeOutCubic(pt);    // scale halus tanpa overshoot yang memicu jitter
        float fade = easeOutCubic(clamp01(age / 0.42f));
        float textT = clamp01((age - 0.35f) / 0.5f);   // teks menyusul setelah kartu hampir penuh
        if (age >= POP_SECONDS) {
            k = 1f;
        }
        float alpha = visible * fade * c.hudOpacity * c.customTransparency;
        int[] b = box();
        String title;
        String sub;
        if (failed) {
            title = "Gagal memutar";
            sub = XFiveLiveClient.MUSIC.status();
        } else if (t == null) {
            title = "Memutar";
            sub = "";
        } else {
            title = t.title();
            sub = wasPlaying ? t.artist() : XFiveLiveClient.MUSIC.status();
        }
        long dur = XFiveLiveClient.MUSIC.durationMs();
        long pos = XFiveLiveClient.MUSIC.positionMs();
        float progress = dur > 0 ? Math.min(1f, (float) pos / dur) : 0f;
        Font font = GlassPanelRenderer.font();

        if (c.islandCard) {
            int w = Math.max(40, Math.round(b[2] * k));
            int h = Math.max(30, Math.round(b[3] * k));
            int x = b[0] + (b[2] - w) / 2;
            int y = b[1] + (b[3] - h) / 2 - Math.round((1f - pt) * 12f); // meluncur turun pelan
            drawCard(g, font, x, y, w, h, title, sub, progress, pos, dur, wasPlaying,
                    accentColor(), alpha, textT);
            return;
        }
        int compactW = Math.round(BASE_COMPACT_W * c.islandW / 220f);
        // Pil kompak dipusatkan di dalam kotak lebar, supaya tetap di tengah saat melebar.
        int w = Math.round(compactW + (b[2] - compactW) * expand);
        int x = b[0] + (b[2] - w) / 2;
        drawPill(g, font, x, b[1], w, b[3], title, sub, progress, expand, accentColor(), alpha);
    }

    // ---- Tampilan kartu ----

    /** Kartu tanpa animasi teks (dipakai layar kontrol). */
    public static void drawCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
                                String title, String artist, float progress, long posMs, long durMs,
                                boolean playing, int accent, float alpha) {
        drawCard(g, font, x, y, w, h, title, artist, progress, posMs, durMs, playing, accent, alpha, 1f);
    }

    /** Menggambar kartu lengkap. Isi mengikuti tinggi h (84 = dasar). textT 0..1 mengatur munculnya teks. */
    public static void drawCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
                                String title, String artist, float progress, long posMs, long durMs,
                                boolean playing, int accent, float alpha, float textT) {
        int dy = Math.round((1f - textT) * 6f);
        float s = h / (float) CARD_H;
        roundRect(g, x - 1, y - 1, w + 2, h + 2, u(13, s), GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.2f * alpha));
        roundRect(g, x, y, w, h, u(12, s), GlassPanelRenderer.argb(GlassPanelRenderer.BACKGROUND, alpha));

        // sampul bulat berwarna, mirip sampul album
        int art = u(38, s);
        int ax = x + u(10, s);
        int ay = y + u(10, s);
        roundRect(g, ax, ay, art, art, u(8, s), GlassPanelRenderer.argb(accent, 0.9f * alpha));
        int acx = ax + art / 2;
        int acy = ay + art / 2;
        disc(g, acx, acy, Math.round(art * 0.42f), GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.35f * alpha));
        disc(g, acx, acy, Math.round(art * 0.30f), GlassPanelRenderer.argb(accent, 0.9f * alpha));
        disc(g, acx, acy, Math.round(art * 0.18f), GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.55f * alpha));
        disc(g, acx, acy, Math.round(art * 0.08f), GlassPanelRenderer.argb(accent, alpha));

        // judul dan artis
        int tx = ax + art + u(10, s);
        int maxW = x + w - u(38, s) - tx;
        if (textT > 0f) {
            GlassPanelRenderer.text(g, font, fit(font, title, maxW), tx, y + u(13, s) + dy, GlassPanelRenderer.WHITE);
            GlassPanelRenderer.text(g, font, fit(font, artist, maxW), tx, y + u(26, s) + dy, GlassPanelRenderer.MUTED);
        }

        // gelombang suara kecil di kanan atas, bergerak saat musik berputar
        double t = System.nanoTime() / 1_000_000_000.0;
        int[] base = {5, 11, 7, 13};
        for (int i = 0; i < base.length; i++) {
            double wave = playing ? 0.55 + 0.45 * Math.sin(t * 7 + i * 1.7) : 0.5;
            int bh = Math.max(2, (int) Math.round(u(base[i], s) * wave));
            int bx = x + w - u(30, s) + i * u(5, s);
            int mid = y + u(20, s);
            g.fill(bx, mid - bh / 2, bx + Math.max(1, u(2, s)), mid - bh / 2 + bh, GlassPanelRenderer.argb(accent, alpha));
        }

        // bar progres dengan titik geser
        int bx1 = x + u(10, s);
        int bx2 = x + w - u(10, s);
        int by = y + u(52, s);
        int th = Math.max(2, u(3, s));
        g.fill(bx1, by, bx2, by + th, GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.25f * alpha));
        int kx = bx1 + Math.round((bx2 - bx1) * progress);
        g.fill(bx1, by, kx, by + th, GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, alpha));
        disc(g, kx, by + th / 2, Math.max(2, u(4, s)), GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, alpha));

        // waktu
        String left = formatTime(posMs);
        String right = formatTime(durMs);
        if (textT > 0f) {
            GlassPanelRenderer.text(g, font, left, bx1, y + u(60, s) + dy, GlassPanelRenderer.MUTED);
            GlassPanelRenderer.text(g, font, right, bx2 - font.width(right), y + u(60, s) + dy, GlassPanelRenderer.MUTED);
        }

        // tombol sebelumnya, jeda/putar, berikutnya
        int cy = y + h - u(13, s);
        int cx = x + w / 2;
        int gap = u(40, s);
        int half = Math.max(3, u(6, s));
        int white = GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, alpha);
        g.fill(cx - gap - half, cy - half, cx - gap - half + 2, cy + half, white);
        triangle(g, cx - gap - half + 2, cy, half, half * 2 - 2, false, white);
        g.fill(cx + gap + half - 2, cy - half, cx + gap + half, cy + half, white);
        triangle(g, cx + gap - half + 1, cy, half, half * 2 - 2, true, white);
        if (playing) {
            int bw = Math.max(2, u(3, s));
            g.fill(cx - bw - 1, cy - half, cx - 1, cy + half, white);
            g.fill(cx + 1, cy - half, cx + bw + 1, cy + half, white);
        } else {
            triangle(g, cx - half + 1, cy, half, half * 2, true, white);
        }
    }

    /**
     * Mengetahui bagian kartu yang diketuk: 1 sebelumnya, 2 putar/jeda, 3 berikutnya,
     * 4 bar progres, 0 tidak ada. b adalah kotak kartu dari cardBox().
     */
    public static int hit(int[] b, double mx, double my) {
        float s = b[3] / (float) CARD_H;
        int cy = b[1] + b[3] - u(13, s);
        int cx = b[0] + b[2] / 2;
        int gap = u(40, s);
        int reach = u(14, s);
        if (Math.abs(my - cy) <= u(12, s)) {
            if (Math.abs(mx - (cx - gap)) <= reach) {
                return 1;
            }
            if (Math.abs(mx - cx) <= reach) {
                return 2;
            }
            if (Math.abs(mx - (cx + gap)) <= reach) {
                return 3;
            }
        }
        int by = b[1] + u(52, s);
        if (my >= by - u(5, s) && my <= by + u(9, s) && mx >= b[0] + u(6, s) && mx <= b[0] + b[2] - u(6, s)) {
            return 4;
        }
        return 0;
    }

    /** Posisi ketukan di bar progres sebagai pecahan 0..1. */
    public static float seekFraction(int[] b, double mx) {
        float s = b[3] / (float) CARD_H;
        int x1 = b[0] + u(10, s);
        int x2 = b[0] + b[2] - u(10, s);
        return (float) Math.max(0.0, Math.min(1.0, (mx - x1) / Math.max(1, x2 - x1)));
    }

    // ---- Tampilan pil ----

    /**
     * Menggambar pil kecil. expand 0 = hanya judul, 1 = judul, artis, dan bar progres.
     */
    public static void drawPill(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
                                String title, String sub, float progress, float expand, int accent, float alpha) {
        // garis neon tipis di luar, lalu latar gelap transparan di dalam
        pill(g, x - 1, y - 1, w + 2, h + 2, GlassPanelRenderer.argb(accent, alpha * 0.6f));
        pill(g, x, y, w, h, GlassPanelRenderer.argb(GlassPanelRenderer.BACKGROUND, alpha));

        if (expand < 0.5f) {
            String s = GlassPanelRenderer.trim(title, 18);
            int tx = x + (w - font.width(s)) / 2;
            GlassPanelRenderer.text(g, font, s, tx, y + (h - 9) / 2, GlassPanelRenderer.WHITE);
        } else {
            GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(title, 30), x + 14,
                    y + Math.round(h * 0.17f), GlassPanelRenderer.WHITE);
            GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(sub, 34), x + 14,
                    y + Math.round(h * 0.5f) - 1, GlassPanelRenderer.MUTED);
        }

        int barX = x + 14;
        int barW = w - 28;
        int barY = y + h - 5;
        g.fill(barX, barY, barX + barW, barY + 2, GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.15f * alpha));
        g.fill(barX, barY, barX + (int) (barW * progress), barY + 2, GlassPanelRenderer.argb(accent, alpha));
    }

    // ---- Bantuan menggambar ----

    private static int u(int v, float s) {
        return Math.round(v * s);
    }

    /** Persegi dengan sudut membulat, satu fill per baris. */
    private static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            float edge = Math.min(i + 0.5f, h - i - 0.5f);
            int inset = 0;
            if (edge < r) {
                float d = r - edge;
                inset = Math.round(r - (float) Math.sqrt(Math.max(0f, r * r - d * d)));
            }
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, argb);
        }
    }

    private static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int half = Math.round((float) Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, argb);
        }
    }

    /** Segitiga rata di satu sisi. right = true menunjuk ke kanan (tombol putar dan berikutnya). */
    private static void triangle(GuiGraphicsExtractor g, int x, int cy, int half, int width, boolean right, int argb) {
        for (int i = 0; i < width; i++) {
            float frac = right ? 1f - (float) i / width : (float) i / width;
            int hh = Math.max(1, Math.round(half * frac));
            g.fill(x + i, cy - hh, x + i + 1, cy + hh, argb);
        }
    }

    /** Memotong teks agar muat dalam maxPx piksel. */
    private static String fit(Font font, String s, int maxPx) {
        if (font.width(s) <= maxPx) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && font.width(t + "…") > maxPx) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private static String formatTime(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    /** Menggambar pil (kapsul) dengan satu fill per baris, tanpa alokasi objek per frame. */
    private static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
        roundRect(g, x, y, w, h, h / 2, argb);
    }

    /** Membandingkan lagu lewat path, supaya refresh daftar tidak dianggap lagu baru. */
    private static boolean sameTrack(PlaylistManager.Track a, PlaylistManager.Track b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.path().equals(b.path());
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float easeOutCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    /** Exponential interpolation: stable at both low and high frame rates. */
    private static float smoothApproach(float current, float target, float dt, float response) {
        float blend = 1f - (float) Math.exp(-Math.max(0f, dt) * response);
        float next = current + (target - current) * blend;
        return Math.abs(target - next) < 0.001f ? target : next;
    }

    private static float advanceTime() {
        long now = System.nanoTime();
        float dt = lastRenderNs == 0L ? 0f : Math.min(0.1f, (now - lastRenderNs) / 1_000_000_000f);
        lastRenderNs = now;
        return dt;
    }
}
