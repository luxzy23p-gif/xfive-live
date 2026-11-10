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
import net.minecraft.client.gui.components.AbstractSliderButton;
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
    private static final int STACK_H = 140; // tinggi seluruh tumpukan tombol
    private static final String[] REPEAT_NAMES = {"Mati", "Semua", "Satu"};

    private final Screen parent;
    private Button repeatBtn;
    private Button shuffleBtn;
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
        int top = stackTop();
        int x2 = width / 2 - (2 * W_PAIR + GAP) / 2;

        addRenderableWidget(plain(x2, top, 2 * W_PAIR + GAP, "Berhenti", m::stop));
        repeatBtn = dynamic(x2, top + 24, W_PAIR, XFiveLiveClient::cycleRepeat);
        addRenderableWidget(repeatBtn);
        shuffleBtn = dynamic(x2 + W_PAIR + GAP, top + 24, W_PAIR, XFiveLiveClient::toggleShuffle);
        addRenderableWidget(shuffleBtn);
        addRenderableWidget(plain(x2, top + 48, W_PAIR, "Daftar lagu",
                () -> Minecraft.getInstance().setScreenAndShow(new SongListScreen(this))));
        addRenderableWidget(new SizeSlider(x2 + W_PAIR + GAP, top + 48, W_PAIR, BTN_H, true));
        addRenderableWidget(new SizeSlider(x2, top + 72, W_PAIR, BTN_H, false));
        colorBtn = dynamic(x2 + W_PAIR + GAP, top + 72, W_PAIR, XFiveLiveClient::cycleIslandColor);
        addRenderableWidget(colorBtn);
        pausedBtn = dynamic(x2, top + 96, W_PAIR, XFiveLiveClient::toggleIslandShowPaused);
        addRenderableWidget(pausedBtn);
        styleBtn = dynamic(x2 + W_PAIR + GAP, top + 96, W_PAIR, XFiveLiveClient::toggleIslandCard);
        addRenderableWidget(styleBtn);
        addRenderableWidget(plain(x2, top + 120, 2 * W_PAIR + GAP, "Reset ukuran", () -> {
            XFiveLiveClient.resetIslandSize();
            rebuildWidgets();
        }));

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
        int[] card = previewBox();
        DynamicIslandHud.drawCard(g, font, card[0], card[1], card[2], card[3], title, artist, progress, pos, dur,
                m.isPlaying(), DynamicIslandHud.accentColor(), 1f);

        String info = t == null ? "" : "Lagu " + (m.playlist().currentIndex() + 1) + " dari " + m.playlist().size();
        GlassPanelRenderer.text(g, font, info, 10, 10, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "Status: " + GlassPanelRenderer.trim(m.status(), 50), 10, 22,
                GlassPanelRenderer.MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Tombol biasa didahulukan, lalu bagian kartu (sebelumnya, jeda, berikutnya, bar progres).
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        MusicManager m = XFiveLiveClient.MUSIC;
        int[] card = previewBox();
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
        XFiveLiveClient.CONFIG.save();
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
        colorBtn.setMessage(Component.literal("Warna: " + DynamicIslandHud.COLOR_NAMES[c.islandColor]));
        pausedBtn.setMessage(Component.literal("Saat jeda: " + onOff(c.islandShowPaused)));
        styleBtn.setMessage(Component.literal("Tampilan: " + (c.islandCard ? "Kartu" : "Pil")));
    }

    /** Kartu pratinjau selalu di tengah atas layar ini, apa pun posisi HUD di game. */
    private int[] previewBox() {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        int w = Math.min(c.islandW, width - 8);
        return new int[] {(width - w) / 2, 8, w, c.islandH};
    }

    /** Tumpukan tombol menempel di bawah layar, jadi tidak bergeser saat ukuran kartu diubah. */
    private int stackTop() {
        return Math.max(30, height - 34 - STACK_H);
    }

    /** Slider lebar (140..400) atau tinggi (60..160) kartu. Kartu di atas ikut berubah langsung. */
    private static final class SizeSlider extends AbstractSliderButton {
        private final boolean widthMode;

        SizeSlider(int x, int y, int w, int h, boolean widthMode) {
            super(x, y, w, h, Component.empty(), 0.0);
            this.widthMode = widthMode;
            XFiveConfig c = XFiveLiveClient.CONFIG;
            this.value = widthMode ? (c.islandW - 140) / 260.0 : (c.islandH - 60) / 100.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            XFiveConfig c = XFiveLiveClient.CONFIG;
            setMessage(Component.literal(widthMode ? "Lebar: " + c.islandW : "Tinggi: " + c.islandH));
        }

        @Override
        protected void applyValue() {
            XFiveConfig c = XFiveLiveClient.CONFIG;
            if (widthMode) {
                c.islandW = 140 + (int) Math.round(value * 260.0);
            } else {
                c.islandH = 60 + (int) Math.round(value * 100.0);
            }
        }
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
