package com.xfive.live.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Panel gaya glassmorphism: latar gelap transparan dengan garis neon tipis.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class GlassPanelRenderer {
    public static final int BACKGROUND = 0x10121C;
    public static final int PURPLE = 0x965CFF;
    public static final int CYAN = 0x40DFFF;
    public static final int WHITE = 0xFFFFFF;
    public static final int MUTED = 0xA9B2C8;

    private GlassPanelRenderer() {
    }

    public static int argb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int accent(String theme) {
        return "cyan".equals(theme) ? CYAN : PURPLE;
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Panel kaca (liquid glass) untuk HUD. Lihat Glass.panel. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha, int accentRgb) {
        Glass.panel(g, x, y, w, h, 6, alpha, accentRgb);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int rgb) {
        g.text(font, s, x, y, 0xFF000000 | (rgb & 0xFFFFFF), true);
    }

    public static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
