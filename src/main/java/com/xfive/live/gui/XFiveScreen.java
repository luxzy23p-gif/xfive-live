package com.xfive.live.gui;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.hud.DynamicIslandHud;
import com.xfive.live.hud.LiveChatHud;
import com.xfive.live.hud.LyricsHud;
import com.xfive.live.hud.MusicHud;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Menu pengaturan X FIVE LIVE di dalam game, dibuka dengan tombol Right Shift.
 * Kolom kiri: tampilan dan volume. Kolom kanan: kontrol musik.
 * HUD (musik, Dynamic Island, lirik, chat) digeser langsung dengan mouse: tahan klik kiri lalu geser.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class XFiveScreen extends Screen {
    private static final int BTN_W = 200;
    private static final int BTN_H = 20;
    private static final int ROW = 24;
    private static final int TOP = 40;
    private static final String[] LYRICS_POS = {"Atas", "Tengah", "Bawah"};

    private int leftX;
    private int rightX;

    /** Kunci posisi HUD yang sedang diseret, atau null jika tidak ada yang diseret. */
    private String dragKey = null;
    private int dragOffX;
    private int dragOffY;
    private int dragW;
    private int dragH;

    /** Satu HUD yang bisa digeser: kunci posisi di config, nama tampilan, dan kotaknya di layar. */
    private record Item(String key, String label, int[] box) {
    }

    public XFiveScreen() {
        super(Component.literal("X FIVE LIVE"));
    }

    @Override
    protected void init() {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        XFiveLiveClient.refreshMusic();
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
        addRenderableWidget(plain(rightX, row(r++), BTN_W, "Putar / Jeda", () -> {
            XFiveLiveClient.refreshMusic();
            XFiveLiveClient.MUSIC.togglePause();
        }));
        addRenderableWidget(plain(rightX, row(r++), BTN_W, "Lagu berikutnya",
                () -> XFiveLiveClient.MUSIC.next()));
        addRenderableWidget(plain(rightX, row(r++), BTN_W, "Lagu sebelumnya",
                () -> XFiveLiveClient.MUSIC.previous()));
        addRenderableWidget(plain(rightX, row(r++), BTN_W, "Perbarui daftar lagu",
                () -> XFiveLiveClient.refreshMusic()));
        addRenderableWidget(plain(rightX, row(r++), BTN_W, "Daftar lagu",
                () -> Minecraft.getInstance().setScreenAndShow(new SongListScreen(this))));
        addRenderableWidget(plain(rightX, row(r++), BTN_W, "Kontrol Dynamic Island",
                () -> Minecraft.getInstance().setScreenAndShow(new MusicControlScreen(this))));
        addRenderableWidget(labelled(rightX, row(r++), BTN_W,
                () -> "Posisi lirik: " + LYRICS_POS[c.lyricsPosition],
                () -> {
                    c.lyricsPosition = (c.lyricsPosition + 1) % LYRICS_POS.length;
                    resetLyricsBox();
                }));
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

        GlassPanelRenderer.text(g, font, "Geser kotak HUD dengan mouse", rightX, row(8), GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "(tahan klik kiri, lalu geser)", rightX, row(8) + 11,
                GlassPanelRenderer.MUTED);

        drawHud(g, font, mouseX, mouseY);
    }

    /** Menggambar HUD asli di posisinya, lalu kotak sorot di atasnya agar mudah digeser. */
    private void drawHud(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        if (c.enableMusic) {
            if (c.dynamicIsland) {
                // Dynamic Island hanya muncul saat musik diputar, jadi di menu digambar sebagai kotak kosong.
                int[] b = DynamicIslandHud.box();
                g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3],
                        GlassPanelRenderer.argb(GlassPanelRenderer.BACKGROUND, 0.7f));
            } else {
                MusicHud.render(g);
            }
        }
        if (c.enableLyrics) {
            LyricsHud.render(g);
        }
        if (c.enableChat) {
            LiveChatHud.render(g);
        }

        int accent = GlassPanelRenderer.accent(c.theme);
        for (Item it : hudItems()) {
            int[] b = it.box();
            boolean active = it.key().equals(dragKey) || inside(b, mouseX, mouseY);
            outline(g, b, GlassPanelRenderer.argb(accent, active ? 1f : 0.4f));
            int labelY = b[1] >= 12 ? b[1] - 11 : b[1] + b[3] + 3;
            GlassPanelRenderer.text(g, font, it.label(), b[0], labelY,
                    active ? GlassPanelRenderer.WHITE : GlassPanelRenderer.MUTED);
        }
    }

    /** HUD yang aktif dan bisa digeser, dalam urutan gambar (yang terakhir ada di atas). */
    private static List<Item> hudItems() {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        List<Item> out = new ArrayList<>(3);
        if (c.enableMusic) {
            if (c.dynamicIsland) {
                out.add(new Item("dynamicIsland", "Dynamic Island", DynamicIslandHud.box()));
            } else {
                out.add(new Item("musicHud", "Musik", MusicHud.box()));
            }
        }
        if (c.enableLyrics) {
            out.add(new Item("lyricsHud", "Lirik", LyricsHud.box()));
        }
        if (c.enableChat) {
            out.add(new Item("chatHud", "Chat", LiveChatHud.box()));
        }
        return out;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Tombol di layar didahulukan. HUD hanya digeser jika klik mengenai area kosong.
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        List<Item> items = hudItems();
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            int[] b = it.box();
            if (inside(b, event.x(), event.y())) {
                dragKey = it.key();
                dragOffX = (int) event.x() - b[0];
                dragOffY = (int) event.y() - b[1];
                dragW = b[2];
                dragH = b[3];
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragKey == null) {
            return super.mouseDragged(event, dx, dy);
        }
        // Posisi dijaga agar kotak tidak keluar dari layar.
        int nx = Math.max(0, Math.min(width - dragW, (int) event.x() - dragOffX));
        int ny = Math.max(0, Math.min(height - dragH, (int) event.y() - dragOffY));
        int[] pos = XFiveLiveClient.CONFIG.hudPositions.get(dragKey);
        pos[0] = nx;
        pos[1] = ny;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragKey == null) {
            return super.mouseReleased(event);
        }
        dragKey = null;
        XFiveLiveClient.CONFIG.save();
        return true;
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

    private static boolean inside(int[] b, double x, double y) {
        return x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3];
    }

    private static void outline(GuiGraphicsExtractor g, int[] b, int argb) {
        int x1 = b[0];
        int y1 = b[1];
        int x2 = b[0] + b[2];
        int y2 = b[1] + b[3];
        g.fill(x1, y1, x2, y1 + 1, argb);
        g.fill(x1, y2 - 1, x2, y2, argb);
        g.fill(x1, y1, x1 + 1, y2, argb);
        g.fill(x2 - 1, y1, x2, y2, argb);
    }

    /** Mengembalikan posisi lirik ke preset Atas/Tengah/Bawah. */
    private static void resetLyricsBox() {
        int[] pos = XFiveLiveClient.CONFIG.hudPositions.get("lyricsHud");
        pos[0] = -1;
        pos[1] = -1;
    }

    private static String tail(String s, int max) {
        return s.length() <= max ? s : "..." + s.substring(s.length() - max);
    }

    private int row(int index) {
        return TOP + index * ROW;
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
