package com.xfive.live.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * Toolkit UI sederhana (immediate mode) bertema liquid glass. Setiap tombol, saklar, slider, dan baris
 * punya animasi halus: menyorot saat kursor di atasnya, menyusut saat ditekan lalu memantul kembali.
 * Elemen digambar dan didaftarkan tiap frame; klik memakai daftar dari frame terakhir.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class GlassUi {
    private record Hit(String id, int x, int y, int w, int h, boolean slider, Runnable action, DoubleConsumer onSlide) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, float[]> state = new HashMap<>();
    private Hit pressed;
    private boolean cancelled;
    private long lastNs = 0L;
    private float dt = 0.016f;
    private int mx;
    private int my;
    private float alpha = 1f;
    private int accent = GlassPanelRenderer.PURPLE;

    /** Dipanggil sekali di awal tiap frame. */
    public void begin(int mouseX, int mouseY, int accentRgb, float masterAlpha) {
        long now = System.nanoTime();
        dt = lastNs == 0L ? 0.016f : Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        mx = mouseX;
        my = mouseY;
        accent = accentRgb;
        alpha = masterAlpha;
        hits.clear();
    }

    /** Mengubah kepekatan elemen yang digambar berikutnya (dipakai untuk transisi halaman). */
    public void setAlpha(float masterAlpha) {
        alpha = masterAlpha;
    }

    public float dt() {
        return dt;
    }

    /** Nilai yang mendekati target dengan halus (eksponensial), terikat pada kunci. */
    public float ease(String key, float target, float speed) {
        float[] s = state.computeIfAbsent(key, k -> new float[] {target});
        s[0] += (target - s[0]) * (1f - (float) Math.exp(-speed * dt));
        return s[0];
    }

    /** Sama dengan ease, tetapi memantul sedikit (pegas) saat melewati target. */
    private float spring(String key, float target) {
        float[] s = state.computeIfAbsent(key, name -> new float[] {target, 0f});
        if (s.length < 2) {
            s = new float[] {s[0], 0f};
            state.put(key, s);
        }
        float d = Math.min(dt, 0.02f); // langkah kecil menjaga pegas tetap stabil
        float stiff = 240f;
        float damp = 18f;
        s[1] += ((target - s[0]) * stiff - s[1] * damp) * d;
        s[0] += s[1] * d;
        return s[0];
    }

    private boolean isPressed(String id) {
        return pressed != null && pressed.id().equals(id) && !cancelled;
    }

    private boolean over(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- Elemen ----

    public void button(String id, Font font, int x, int y, int w, int h, String label, boolean active, Runnable action) {
        hits.add(new Hit(id, x, y, w, h, false, action, null));
        float hv = ease(id + "#h", over(x, y, w, h) ? 1f : 0f, 14f);
        float pr = spring(id + "#p", isPressed(id) ? 1f : 0f);
        float act = ease(id + "#a", active ? 1f : 0f, 12f);
        int in = Math.round(Math.max(0f, pr) * 2.2f);
        int bx = x + in;
        int by = y + in;
        int bw = w - 2 * in;
        int bh = h - 2 * in;
        Glass.control(g(), bx, by, bw, bh, Math.min(bh / 2, 8), alpha, Math.min(1f, hv * 0.6f + Math.max(0f, pr)),
                accent, act * 0.55f);
        if (alpha > 0.55f) {
            int tw = font.width(label);
            GlassPanelRenderer.text(g(), font, label, bx + (bw - tw) / 2, by + (bh - 8) / 2 + Math.round(pr),
                    GlassPanelRenderer.WHITE);
        }
    }

    public void toggle(String id, Font font, int x, int y, int w, int h, String label, boolean on, Runnable action) {
        hits.add(new Hit(id, x, y, w, h, false, action, null));
        float hv = ease(id + "#h", over(x, y, w, h) ? 1f : 0f, 14f);
        float pr = spring(id + "#p", isPressed(id) ? 1f : 0f);
        float t = ease(id + "#t", on ? 1f : 0f, 16f);
        int in = Math.round(Math.max(0f, pr) * 2f);
        int bx = x + in;
        int by = y + in;
        int bw = w - 2 * in;
        int bh = h - 2 * in;
        Glass.control(g(), bx, by, bw, bh, Math.min(bh / 2, 8), alpha, Math.min(1f, hv * 0.6f + Math.max(0f, pr)),
                accent, 0f);
        int tw = 26;
        int th = 12;
        int tx = bx + bw - tw - 6;
        int ty = by + (bh - th) / 2;
        Glass.roundRect(g(), tx, ty, tw, th, th / 2, Glass.mix(Glass.argb(0xFFFFFF, 0.16f * alpha),
                Glass.argb(accent, 0.85f * alpha), t));
        int kx = tx + 6 + Math.round((tw - 12) * t);
        Glass.disc(g(), kx, ty + th / 2, 4, Glass.argb(0xFFFFFF, alpha));
        if (alpha > 0.55f) {
            GlassPanelRenderer.text(g(), font, label, bx + 7, by + (bh - 8) / 2, GlassPanelRenderer.WHITE);
        }
    }

    /** Slider dengan label dan nilai. value 0..1. onSlide menerima nilai baru saat digeser. */
    public void slider(String id, Font font, int x, int y, int w, int h, String label, String valueText,
                       float value, DoubleConsumer onSlide) {
        hits.add(new Hit(id, x, y, w, h, true, null, onSlide));
        float hv = ease(id + "#h", (over(x, y, w, h) || isPressed(id)) ? 1f : 0f, 14f);
        Glass.control(g(), x, y, w, h, Math.min(h / 2, 8), alpha, hv * 0.6f, accent, 0f);
        int tx1 = x + 7;
        int tx2 = x + w - 7;
        int ty = y + h - 6;
        Glass.roundRect(g(), tx1, ty, tx2 - tx1, 3, 1, Glass.argb(0xFFFFFF, 0.20f * alpha));
        int fill = Math.round((tx2 - tx1) * Math.max(0f, Math.min(1f, value)));
        Glass.roundRect(g(), tx1, ty, Math.max(2, fill), 3, 1, Glass.argb(accent, 0.95f * alpha));
        float kr = 3f + 1.5f * ease(id + "#k", isPressed(id) ? 1f : 0f, 18f);
        Glass.disc(g(), tx1 + fill, ty + 1, Math.round(kr), Glass.argb(0xFFFFFF, alpha));
        if (alpha > 0.55f) {
            GlassPanelRenderer.text(g(), font, label, x + 7, y + 3, GlassPanelRenderer.WHITE);
            GlassPanelRenderer.text(g(), font, valueText, x + w - 7 - font.width(valueText), y + 3,
                    GlassPanelRenderer.MUTED);
        }
    }

    /** Bar geser tipis tanpa kotak (progres lagu, volume). Area ketuk lebih tinggi agar mudah disentuh. */
    public void seek(String id, int x, int y, int w, int h, float value, DoubleConsumer onSlide) {
        hits.add(new Hit(id, x - 7, y, w + 14, h, true, null, onSlide));
        boolean hot = over(x - 7, y, w + 14, h) || isPressed(id);
        float k = ease(id + "#k", hot ? 1f : 0f, 16f);
        int th = 3;
        int ty = y + h / 2 - 1;
        Glass.roundRect(g(), x, ty, w, th, 1, Glass.argb(0xFFFFFF, 0.22f * alpha));
        int fill = Math.round(w * Math.max(0f, Math.min(1f, value)));
        Glass.roundRect(g(), x, ty, Math.max(2, fill), th, 1, Glass.argb(accent, 0.95f * alpha));
        Glass.disc(g(), x + fill, ty + 1, Math.round(2.5f + 2f * k), Glass.argb(0xFFFFFF, alpha));
    }

    /**
     * Mendaftarkan area ketuk tanpa menggambar (untuk baris daftar). Mengembalikan {sorot, tekan}
     * yang sudah dihaluskan, supaya pemanggil bisa menggambar sendiri dengan animasi yang sama.
     */
    public float[] row(String id, int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(id, x, y, w, h, false, action, null));
        float hv = ease(id + "#h", over(x, y, w, h) ? 1f : 0f, 14f);
        float pr = spring(id + "#p", isPressed(id) ? 1f : 0f);
        return new float[] {hv, Math.max(0f, pr)};
    }

    // ---- Masukan ----

    /** Mengembalikan true jika menyentuh elemen. Slider langsung menerapkan nilai. */
    public boolean down(double x, double y) {
        cancelled = false;
        pressed = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(x, y)) {
                pressed = h;
                if (h.slider()) {
                    slide(h, x);
                }
                return true;
            }
        }
        return false;
    }

    /** True jika masih menyeret slider (tidak boleh dipakai untuk menggulir). */
    public boolean drag(double x, double y) {
        if (pressed != null && pressed.slider() && !cancelled) {
            slide(pressed, x);
            return true;
        }
        return false;
    }

    public boolean hasPress() {
        return pressed != null && !cancelled;
    }

    /** Membatalkan ketukan (dipakai saat jari mulai menggulir). */
    public void cancelPress() {
        cancelled = true;
    }

    /** Melepas. Aksi dijalankan jika masih di atas elemen yang sama. */
    public boolean up(double x, double y) {
        Hit h = pressed;
        boolean ok = h != null && !cancelled;
        pressed = null;
        cancelled = false;
        if (ok && !h.slider() && h.contains(x, y) && h.action() != null) {
            h.action().run();
        }
        return ok;
    }

    private static void slide(Hit h, double x) {
        double v = (x - (h.x() + 7)) / Math.max(1, h.w() - 14);
        h.onSlide().accept(Math.max(0.0, Math.min(1.0, v)));
    }

    private GuiGraphicsExtractor current;

    /** Kanvas untuk frame ini, diatur lewat canvas() sebelum menggambar. */
    public void canvas(GuiGraphicsExtractor g) {
        this.current = g;
    }

    private GuiGraphicsExtractor g() {
        return current;
    }
}
