package com.xfive.live.gui;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Menu pengaturan X FIVE LIVE di dalam game, dibuka dengan tombol Right Shift.
 * Kolom kiri: tampilan dan volume. Kolom kanan: kontrol musik dan posisi HUD.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class XFiveScreen extends Screen {
    private static final int BTN_W = 200;
    private static final int BTN_H = 20;
    private static final int HALF_W = 64;
    private static final int ROW = 24;
    private static final int TOP = 40;
    private static final int NUDGE = 5;
    private static final String[] HUD_KEYS = {"musicHud", "chatHud"};
    private static final String[] HUD_NAMES = {"Musik", "Chat"};
    private static final String[] LYRICS_POS = {"Atas", "Tengah", "Bawah"};

    private int selectedHud = 0;
    private int leftX;
    private int rightX;

    public XFiveScreen() {
        super(Component.literal("X FIVE LIVE"));
    }

    @Override
    protected void init() {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        leftX = width / 2 - BTN_W - 4;
        rightX = width / 2 + 4;

        int r = 0;
        addRenderableWidget(toggle(leftX, row(r++), "Mod", () -> c.enableMod, v -> c.enableMod = v));
        addRenderableWidget(toggle(leftX, row(r++), "HUD", () -> c.showHud, v -> c.showHud = v));
        addRenderableWidget(toggle(leftX, row(r++), "Musik", () -> c.enableMusic, v -> c.enableMusic = v));
        addRenderableWidget(toggle(leftX, row(r++), "Lirik", () -> c.enableLyrics, v -> c.enableLyrics = v));
        addRenderableWidget(toggle(leftX, row(r++), "Chat", () -> c.enableChat, v -> c.enableChat = v));
        addRenderableWidget(toggle(leftX, row(r++), "Dynamic Island", () -> c.dynamicIsland, v -> c.dynamicIsland = v));
        addRenderableWidget(labelled(leftX, row(r++), BTN_W,
                () -> "Tema: " + ("cyan".equals(c.theme) ? "Cyan" : "Ungu"),
                () -> c.theme = "cyan".equals(c.theme) ? "purple" : "cyan"));
        addRenderableWidget(new VolumeSlider(leftX, row(r++), BTN_W, BTN_H));
        addRenderableWidget(new OpacitySlider(leftX, row(r++), BTN_W, BTN_H));

        r = 0;
        addRenderableWidget(plain(rightX, row(r++), BTN_W, "Putar / Jeda",
                () -> XFiveLiveClient.MUSIC.togglePause()));
        addRenderableWidget(plain(rightX, row(r++), BTN_W, "Lagu berikutnya",
                () -> XFiveLiveClient.MUSIC.next()));
        addRenderableWidget(plain(rightX, row(r++), BTN_W, "Lagu sebelumnya",
                () -> XFiveLiveClient.MUSIC.previous()));
        addRenderableWidget(plain(rightX, row(r++), BTN_W, "Muat ulang daftar lagu",
                () -> XFiveLiveClient.reloadMusic()));
        addRenderableWidget(plain(rightX, row(r++), BTN_W, "Daftar lagu",
                () -> Minecraft.getInstance().setScreenAndShow(new SongListScreen(this))));
        addRenderableWidget(labelled(rightX, row(r++), BTN_W,
                () -> "Posisi lirik: " + LYRICS_POS[c.lyricsPosition],
                () -> c.lyricsPosition = (c.lyricsPosition + 1) % LYRICS_POS.length));
        addRenderableWidget(labelled(rightX, row(r++), BTN_W,
                () -> "Geser HUD: " + HUD_NAMES[selectedHud],
                () -> selectedHud = (selectedHud + 1) % HUD_KEYS.length));

        int rUp = row(r++);
        addRenderableWidget(plain(rightX + (BTN_W - HALF_W) / 2, rUp, HALF_W, "Atas", () -> nudge(0, -NUDGE)));
        int rMid = row(r++);
        addRenderableWidget(plain(rightX, rMid, HALF_W, "Kiri", () -> nudge(-NUDGE, 0)));
        addRenderableWidget(plain(rightX + BTN_W - HALF_W, rMid, HALF_W, "Kanan", () -> nudge(NUDGE, 0)));
        int rDown = row(r++);
        addRenderableWidget(plain(rightX + (BTN_W - HALF_W) / 2, rDown, HALF_W, "Bawah", () -> nudge(0, NUDGE)));

        addRenderableWidget(plain(rightX, row(r++), BTN_W, "Reset posisi HUD",
                () -> XFiveLiveClient.CONFIG.resetHudPositions()));

        addRenderableWidget(plain(width / 2 - 60, height - 28, 120, "Tutup", this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Font font = GlassPanelRenderer.font();
        GlassPanelRenderer.text(g, font, "X FIVE LIVE", leftX, 22, GlassPanelRenderer.PURPLE);
        PlaylistManager.Track t = XFiveLiveClient.MUSIC.currentTrack();
        String now = t == null ? XFiveLiveClient.MUSIC.status() : t.title() + " - " + t.artist();
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(now, 60), leftX, height - 50,
                GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "Status: " + GlassPanelRenderer.trim(XFiveLiveClient.MUSIC.status(), 50),
                leftX, height - 38, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "Lirik: " + (XFiveLiveClient.LYRICS.isEmpty()
                ? "tidak ditemukan untuk lagu ini"
                : XFiveLiveClient.LYRICS.lines().size() + " baris"),
                leftX, height - 86, GlassPanelRenderer.MUTED);
        Path folder = XFiveLiveClient.musicFolder;
        String folderText = folder == null ? "-" : tail(folder.toString(), 46);
        GlassPanelRenderer.text(g, font, "Folder lagu: " + folderText, leftX, height - 74,
                GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "Jumlah lagu: " + XFiveLiveClient.MUSIC.playlist().size(), leftX,
                height - 62, GlassPanelRenderer.MUTED);
    }

    @Override
    public void onClose() {
        XFiveLiveClient.CONFIG.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String tail(String s, int max) {
        return s.length() <= max ? s : "..." + s.substring(s.length() - max);
    }

    private int row(int index) {
        return TOP + index * ROW;
    }

    private void nudge(int dx, int dy) {
        int[] pos = XFiveLiveClient.CONFIG.hudPositions.get(HUD_KEYS[selectedHud]);
        pos[0] = Math.max(0, Math.min(width - 40, pos[0] + dx));
        pos[1] = Math.max(0, Math.min(height - 20, pos[1] + dy));
    }

    private static Button toggle(int x, int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
        return labelled(x, y, BTN_W,
                () -> name + ": " + (get.getAsBoolean() ? "ON" : "OFF"),
                () -> set.accept(!get.getAsBoolean()));
    }

    /** Tombol yang labelnya ikut berubah setiap ditekan. */
    private static Button labelled(int x, int y, int w, Supplier<String> label, Runnable action) {
        return Button.builder(Component.literal(label.get()), b -> {
            action.run();
            b.setMessage(Component.literal(label.get()));
        }).bounds(x, y, w, BTN_H).build();
    }

    private static Button plain(int x, int y, int w, String label, Runnable action) {
        return Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, w, BTN_H).build();
    }

    private static final class VolumeSlider extends AbstractSliderButton {
        VolumeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), XFiveLiveClient.CONFIG.defaultVolume);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Volume: " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            XFiveLiveClient.CONFIG.defaultVolume = (float) value;
            XFiveLiveClient.MUSIC.setVolume((float) value);
        }
    }

    private static final class OpacitySlider extends AbstractSliderButton {
        OpacitySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (XFiveLiveClient.CONFIG.hudOpacity - 0.1) / 0.9);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Transparansi HUD: " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            XFiveLiveClient.CONFIG.hudOpacity = (float) (0.1 + value * 0.9);
        }
    }
}
