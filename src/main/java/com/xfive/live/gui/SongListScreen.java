package com.xfive.live.gui;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Daftar semua lagu yang terbaca dari folder musik. Ketuk judul untuk memutar lagu itu.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class SongListScreen extends Screen {
    private static final int PAGE_SIZE = 7;
    private static final int BTN_W = 300;
    private static final int TOP = 50;
    private static final int ROW = 24;

    private final Screen parent;
    private int page = 0;

    public SongListScreen(Screen parent) {
        super(Component.literal("Daftar lagu"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<PlaylistManager.Track> tracks = XFiveLiveClient.MUSIC.playlist().tracks();
        page = Math.max(0, Math.min(page, pageCount(tracks.size()) - 1));

        int x = width / 2 - BTN_W / 2;
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= tracks.size()) {
                break;
            }
            PlaylistManager.Track t = tracks.get(idx);
            String label = (idx + 1) + ". " + GlassPanelRenderer.trim(t.title() + " - " + t.artist(), 44);
            addRenderableWidget(Button.builder(Component.literal(label), b -> play(idx))
                    .bounds(x, TOP + i * ROW, BTN_W, 20).build());
        }

        int navY = TOP + PAGE_SIZE * ROW + 8;
        addRenderableWidget(Button.builder(Component.literal("< Sebelumnya"), b -> changePage(-1))
                .bounds(x, navY, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Berikutnya >"), b -> changePage(1))
                .bounds(x + BTN_W - 140, navY, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Kembali"), b -> onClose())
                .bounds(width / 2 - 60, navY + 28, 120, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Font font = GlassPanelRenderer.font();
        int total = XFiveLiveClient.MUSIC.playlist().size();
        int x = width / 2 - BTN_W / 2;
        if (total == 0) {
            GlassPanelRenderer.text(g, font, "Belum ada lagu di folder musik.", x, 30, GlassPanelRenderer.MUTED);
            GlassPanelRenderer.text(g, font, "Masukkan file .mp3/.ogg/.wav, lalu Muat ulang.", x, 40,
                    GlassPanelRenderer.MUTED);
        } else {
            GlassPanelRenderer.text(g, font,
                    "Halaman " + (page + 1) + " dari " + pageCount(total) + " (" + total + " lagu)",
                    x, 30, GlassPanelRenderer.MUTED);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void changePage(int delta) {
        int pages = pageCount(XFiveLiveClient.MUSIC.playlist().size());
        page = Math.max(0, Math.min(pages - 1, page + delta));
        rebuildWidgets();
    }

    private static void play(int index) {
        XFiveLiveClient.MUSIC.playlist().select(index);
        XFiveLiveClient.MUSIC.playCurrent();
    }

    private static int pageCount(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }
}
