package com.xfive.live.gui;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.hud.DynamicIslandHud;
import com.xfive.live.music.MusicManager;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Layar kontrol Dynamic Island. Kartu di atas bisa diketuk: sebelumnya, jeda/putar, berikutnya,
 * dan bar progres untuk pindah posisi. Di bawahnya ada berhenti, ulang, acak, ukuran, warna,
 * tampil saat jeda, dan pilihan tampilan. Dibuka dengan F7 atau tombol di menu Right Shift.
 * HUD di dalam game tidak bisa diklik, jadi kontrolnya ada di layar ini.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class MusicControlScreen extends Screen {
    private static final int BTN_H = 20;
    private static final int GAP = 4;
    private static final int W_PAIR = 110;
    private static final int STACK_H = 92; // tinggi seluruh tumpukan tombol
    private static final String[] REPEAT_NAMES = {"Mati", "Semua", "Satu"};

    private final Screen parent;
    private Button repeatBtn;
    private Button shuffleBtn;
    private Button sizeBtn;
    private Button colorBtn;
    private Button pausedBtn;
    private Button styleBtn;

    /** parent adalah layar yang dibuka lagi saat Tutup. Null berarti kembali ke game. */
    public MusicControlScreen(Screen parent) {
        super(Component.literal("Kontrol Dynamic Island"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MusicManager m = XFiveLiveClient.MUSIC;
        int[] card = DynamicIslandHud.cardBox();
        int top = stackTop(card);
        int x2 = width / 2 - (2 * W_PAIR + GAP) / 2;

        addRenderableWidget(plain(x2, top, 2 * W_PAIR + GAP, "Berhenti", m::stop));
        repeatBtn = dynamic(x2, top + 24, W_PAIR, XFiveLiveClient::cycleRepeat);
        addRenderableWidget(repeatBtn);
        shuffleBtn = dynamic(x2 + W_PAIR + GAP, top + 24, W_PAIR, XFiveLiveClient::toggleShuffle);
        addRenderableWidget(shuffleBtn);
        sizeBtn = dynamic(x2, top + 48, W_PAIR, XFiveLiveClient::cycleIslandSize);
        addRenderableWidget(sizeBtn);
        colorBtn = dynamic(x2 + W_PAIR + GAP, top + 48, W_PAIR, XFiveLiveClient::cycleIslandColor);
        addRenderableWidget(colorBtn);
        pausedBtn = dynamic(x2, top + 72, W_PAIR, XFiveLiveClient::toggleIslandShowPaused);
        addRenderableWidget(pausedBtn);
        styleBtn = dynamic(x2 + W_PAIR + GAP, top + 72, W_PAIR, XFiveLiveClient::toggleIslandCard);
        addRenderableWidget(styleBtn);

        addRenderableWidget(plain(width / 2 - 60, height - 28, 120, "Tutup", this::onClose));
        refreshLabels();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        refreshLabels();
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        MusicManager m = XFiveLiveClient.MUSIC;
        Font font = GlassPanelRenderer.font();
        PlaylistManager.Track t = m.currentTrack();
        String title = t == null ? "Belum ada lagu" : t.title();
        String artist = t == null ? m.status() : t.artist();
        long dur = m.durationMs();
        long pos = m.positionMs();
        float progress = dur > 0 ? Math.min(1f, (float) pos / dur) : 0f;
        int[] card = DynamicIslandHud.cardBox();
        DynamicIslandHud.drawCard(g, font, card[0], card[1], card[2], card[3], title, artist, progress, pos, dur,
                m.isPlaying(), DynamicIslandHud.accentColor(), 1f);

        String info = t == null ? "" : "Lagu " + (m.playlist().currentIndex() + 1) + " dari " + m.playlist().size();
        GlassPanelRenderer.text(g, font, info, 10, height - 50, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "Status: " + GlassPanelRenderer.trim(m.status(), 50), 10, height - 38,
                GlassPanelRenderer.MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Tombol biasa didahulukan, lalu bagian kartu (sebelumnya, jeda, berikutnya, bar progres).
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        MusicManager m = XFiveLiveClient.MUSIC;
        int[] card = DynamicIslandHud.cardBox();
        int part = DynamicIslandHud.hit(card, event.x(), event.y());
        if (part == 1) {
            m.previous();
        } else if (part == 2) {
            m.togglePause();
        } else if (part == 3) {
            m.next();
        } else if (part == 4) {
            m.seekTo(DynamicIslandHud.seekFraction(card, event.x()));
        }
        return part != 0;
    }

    @Override
    public void onClose() {
        if (parent == null) {
            super.onClose();
        } else {
            Minecraft.getInstance().setScreenAndShow(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Label tombol mengikuti keadaan terkini. */
    private void refreshLabels() {
        if (repeatBtn == null) {
            return;
        }
        XFiveConfig c = XFiveLiveClient.CONFIG;
        MusicManager m = XFiveLiveClient.MUSIC;
        repeatBtn.setMessage(Component.literal("Ulang: " + REPEAT_NAMES[m.repeatMode()]));
        shuffleBtn.setMessage(Component.literal("Acak: " + onOff(m.shuffle())));
        sizeBtn.setMessage(Component.literal("Ukuran: " + DynamicIslandHud.SIZE_NAMES[c.islandSize]));
        colorBtn.setMessage(Component.literal("Warna: " + DynamicIslandHud.COLOR_NAMES[c.islandColor]));
        pausedBtn.setMessage(Component.literal("Saat jeda: " + onOff(c.islandShowPaused)));
        styleBtn.setMessage(Component.literal("Tampilan: " + (c.islandCard ? "Kartu" : "Pil")));
    }

    /** Tumpukan tombol diletakkan di bawah kartu, atau di atasnya jika kartu sudah di bawah layar. */
    private int stackTop(int[] card) {
        int below = card[1] + card[3] + 8;
        if (below + STACK_H <= height - 34) {
            return below;
        }
        return Math.max(30, card[1] - 8 - STACK_H);
    }

    private static String onOff(boolean on) {
        return on ? "ON" : "OFF";
    }

    private static Button plain(int x, int y, int w, String label, Runnable action) {
        return Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, w, BTN_H).build();
    }

    /** Tombol dengan label kosong. Labelnya diisi oleh refreshLabels(). */
    private static Button dynamic(int x, int y, int w, Runnable action) {
        return Button.builder(Component.literal(""), b -> action.run())
                .bounds(x, y, w, BTN_H).build();
    }
}
