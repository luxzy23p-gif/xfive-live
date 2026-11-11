package com.xfive.live.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Gambar bergaya "liquid glass" yang ringan: kaca gelap transparan dengan kilap di atas,
 * garis tepi terang di sisi atas, dan bayangan tipis. Semuanya hanya fill per baris,
 * tanpa shader, tanpa tekstur, tanpa alokasi per frame.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class Glass {
    private Glass() {
    }

    public static int argb(int rgb, float alpha) {
        return GlassPanelRenderer.argb(rgb, alpha);
    }

    /** Campuran dua warna ARGB per kanal. t = 0 memberi a, t = 1 memberi b. */
    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 255;
        int ar = (a >>> 16) & 255;
        int ag = (a >>> 8) & 255;
        int ab = a & 255;
        int ba = (b >>> 24) & 255;
        int br = (b >>> 16) & 255;
        int bg = (b >>> 8) & 255;
        int bb = b & 255;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** Berapa piksel baris i dipotong dari kiri dan kanan oleh sudut membulat. */
    private static int inset(int i, int h, int r) {
        float edge = Math.min(i + 0.5f, h - i - 0.5f);
        if (edge >= r) {
            return 0;
        }
        float d = r - edge;
        return Math.round(r - (float) Math.sqrt(Math.max(0f, r * r - d * d)));
    }

    /** Persegi membulat berisi gradien vertikal dari warna atas ke warna bawah. */
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int band = h > 90 ? 2 : 1; // panel tinggi digambar per dua baris agar lebih ringan
        int i = 0;
        while (i < h) {
            boolean corner = i < r || i >= h - r;
            int step = corner ? 1 : Math.min(band, h - i - r > 0 ? h - i - r : 1);
            int ins = inset(i, h, r);
            float t = h <= 1 ? 0f : (float) i / (h - 1);
            g.fill(x + ins, y + i, x + w - ins, y + i + step, mix(top, bottom, t));
            i += step;
        }
    }

    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb) {
        roundRect(g, x, y, w, h, r, argb, argb);
    }

    /** Garis tepi 1 piksel mengikuti sudut membulat, dengan gradien atas ke bawah. */
    public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 2 || h <= 2) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            int ins = inset(i, h, r);
            float t = (float) i / (h - 1);
            int c = mix(top, bottom, t);
            if (i == 0 || i == h - 1) {
                g.fill(x + ins, y + i, x + w - ins, y + i + 1, c);
                continue;
            }
            int next = i < h / 2 ? inset(i + 1, h, r) : inset(i - 1, h, r);
            int run = Math.max(1, ins - next + 1);
            g.fill(x + ins, y + i, x + ins + run, y + i + 1, c);
            g.fill(x + w - ins - run, y + i, x + w - ins, y + i + 1, c);
        }
    }

    public static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int half = Math.round((float) Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, argb);
        }
    }

    /**
     * Panel kaca: bayangan lembut, badan gradien dengan kilap di bagian atas,
     * garis kilau di tepi atas, dan rim terang. alpha 0..1 mengatur kepekatan keseluruhan.
     */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float alpha, int accent) {
        if (alpha <= 0.01f || w <= 2 || h <= 2) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        roundRect(g, x, y + 2, w, h, r, argb(0x000000, 0.20f * alpha));                  // bayangan
        roundRect(g, x, y, w, h, r, argb(0x3A4466, 0.66f * alpha), argb(0x0B0E19, 0.84f * alpha)); // badan
        int gloss = Math.max(2, h * 2 / 5);                                                 // kilap atas
        roundRect(g, x + 1, y + 1, w - 2, gloss, Math.max(0, r - 1),
                argb(0xFFFFFF, 0.13f * alpha), argb(0xFFFFFF, 0.01f * alpha));
        roundRect(g, x + r / 2 + 1, y + h - 3, Math.max(2, w - r - 2), 2, 1, argb(accent, 0.16f * alpha)); // cahaya aksen bawah
        border(g, x, y, w, h, r, argb(0xFFFFFF, 0.50f * alpha), argb(0xFFFFFF, 0.10f * alpha));
    }

    /** Kaca untuk kontrol kecil (tombol, baris). lift 0..1 menambah terang saat disorot atau ditekan. */
    public static void control(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float alpha,
                               float lift, int tint, float tintAmount) {
        if (alpha <= 0.01f || w <= 2 || h <= 2) {
            return;
        }
        int top = mix(argb(0xFFFFFF, (0.20f + 0.14f * lift) * alpha), argb(tint, 0.70f * alpha), tintAmount);
        int bot = mix(argb(0xFFFFFF, (0.07f + 0.08f * lift) * alpha), argb(tint, 0.40f * alpha), tintAmount);
        roundRect(g, x, y, w, h, r, top, bot);
        border(g, x, y, w, h, r, argb(0xFFFFFF, (0.45f + 0.2f * lift) * alpha), argb(0xFFFFFF, 0.12f * alpha));
    }
}
