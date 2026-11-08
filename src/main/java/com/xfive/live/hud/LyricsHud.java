package com.xfive.live.hud;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.lyrics.LyricsManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** HUD lirik: baris sebelumnya, baris aktif (disorot), dan baris berikutnya. */
public final class LyricsHud {
    private LyricsHud() {
    }

    public static void render(GuiGraphicsExtractor g) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        if (!c.enableLyrics) {
            return;
        }
        LyricsManager ly = XFiveLiveClient.LYRICS;
        if (ly.isEmpty()) {
            return;
        }
        int idx = ly.indexAt(XFiveLiveClient.MUSIC.positionMs());
        if (idx < 0) {
            return;
        }
        Font font = GlassPanelRenderer.font();
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int lineH = c.lyricsFontSize + 3; // jarak baris, belum menskala teks
        int baseY = switch (c.lyricsPosition) {
            case 0 -> 20;
            case 1 -> sh / 2 - lineH;
            default -> sh - 70;
        };
        int highlight = c.highlightColor;
        float alpha = c.hudOpacity * c.customTransparency;
        int accent = GlassPanelRenderer.accent(c.theme);

        drawCentered(g, font, ly.textAt(idx - 1), sw, baseY, GlassPanelRenderer.MUTED);
        drawCentered(g, font, ly.textAt(idx), sw, baseY + lineH, highlight);
        drawCentered(g, font, ly.textAt(idx + 1), sw, baseY + lineH * 2, GlassPanelRenderer.MUTED);
        if (!c.lyricsMinimal) {
            int w = Math.min(sw - 16, 260);
            g.fill((sw - w) / 2, baseY - 4, (sw + w) / 2, baseY - 3, GlassPanelRenderer.argb(accent, alpha));
        }
    }

    private static void drawCentered(GuiGraphicsExtractor g, Font font, String s, int sw, int y, int rgb) {
        if (s == null || s.isEmpty()) {
            return;
        }
        int x = (sw - font.width(s)) / 2;
        GlassPanelRenderer.text(g, font, s, x, y, rgb);
    }
}
