package com.xfive.live.gui;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.hud.DynamicIslandHud;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Daftar lagu bergaya playlist: baris dengan sampul berwarna, judul, artis, dan penanda
 * equalizer pada lagu yang sedang diputar. Ketuk baris untuk memutar. Geser dengan jari atau
 * roda mouse. Ada kotak cari di atas dan kotak tambah folder/file lagu di bawah.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class SongListScreen extends Screen {
    private static final int ROW = 30;
    private static final int PANEL_MAX_W = 340;
    private static final int[] COVER_COLORS = {0x965CFF, 0x40DFFF, 0xFF4FD8, 0x5CFF8A, 0xFFD84D, 0xFF7A5C};

    private final Screen parent;
    private EditBox search;
    private EditBox pathInput;
    private String pendingPath = "";
    private String pendingSearch = "";

    private double scroll = 0;
    private boolean pressedInList = false;
    private boolean dragged = false;
    private double pressX;
    private double pressY;
    private double lastY;

    /** parent adalah layar yang dibuka lagi saat Kembali. Null berarti kembali ke game. */
    public SongListScreen(Screen parent) {
        super(Component.literal("Daftar lagu"));
        this.parent = parent;
    }

    private int panelW() {
        return Math.min(width - 16, PANEL_MAX_W);
    }

    private int panelX() {
        return (width - panelW()) / 2;
    }

    private int listTop() {
        return 50;
    }

    private int listBottom() {
        return height - 58;
    }

    @Override
    protected void init() {
        XFiveLiveClient.refreshMusic();
        int x = panelX();
        int w = panelW();

        search = new EditBox(GlassPanelRenderer.font(), x, 24, w, 18, Component.literal("Cari lagu"));
        search.setMaxLength(60);
        search.setHint(Component.literal("Cari judul atau artis"));
        search.setValue(pendingSearch);
        search.setResponder(text -> {
            pendingSearch = text;
            scroll = 0;
        });
        addRenderableWidget(search);

        int inputW = w - 70;
        int inputY = height - 50;
        pathInput = new EditBox(GlassPanelRenderer.font(), x, inputY, inputW, 18, Component.literal("Path lagu"));
        pathInput.setMaxLength(300);
        pathInput.setHint(Component.literal("Tambah path folder atau file"));
        pathInput.setValue(pendingPath);
        addRenderableWidget(pathInput);
        addRenderableWidget(Button.builder(Component.literal("Tambah"), b -> addPath())
                .bounds(x + inputW + 4, inputY, w - inputW - 4, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Kembali"), b -> onClose())
                .bounds(width / 2 - 60, height - 26, 120, 20).build());
    }

    /** Indeks lagu yang cocok dengan kata cari. */
    private List<Integer> visibleIndexes() {
        List<PlaylistManager.Track> tracks = XFiveLiveClient.MUSIC.playlist().tracks();
        String q = pendingSearch == null ? "" : pendingSearch.strip().toLowerCase();
        List<Integer> out = new ArrayList<>(tracks.size());
        for (int i = 0; i < tracks.size(); i++) {
            PlaylistManager.Track t = tracks.get(i);
            if (q.isEmpty() || t.title().toLowerCase().contains(q) || t.artist().toLowerCase().contains(q)) {
                out.add(i);
            }
        }
        return out;
    }

    private double maxScroll(int count) {
        return Math.max(0, count * ROW - (listBottom() - listTop()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = GlassPanelRenderer.font();
        int x = panelX();
        int w = panelW();
        int top = listTop();
        int bottom = listBottom();
        int accent = DynamicIslandHud.accentColor();

        g.fill(0, 0, width, height, GlassPanelRenderer.argb(GlassPanelRenderer.BACKGROUND, 0.92f));

        List<Integer> shown = visibleIndexes();
        int total = XFiveLiveClient.MUSIC.playlist().size();
        GlassPanelRenderer.text(g, font, "Daftar lagu", x, 8, GlassPanelRenderer.WHITE);
        String count = shown.size() == total ? total + " lagu" : shown.size() + " dari " + total;
        GlassPanelRenderer.text(g, font, count, x + w - font.width(count), 8, GlassPanelRenderer.MUTED);

        scroll = Math.max(0, Math.min(maxScroll(shown.size()), scroll));
        int current = XFiveLiveClient.MUSIC.playlist().currentIndex();
        boolean playing = XFiveLiveClient.MUSIC.isPlaying();
        List<PlaylistManager.Track> tracks = XFiveLiveClient.MUSIC.playlist().tracks();
        double time = System.nanoTime() / 1_000_000_000.0;

        if (shown.isEmpty()) {
            String msg = total == 0 ? "Belum ada lagu. Tambah path di bawah." : "Tidak ada yang cocok.";
            GlassPanelRenderer.text(g, font, msg, x, top + 8, GlassPanelRenderer.MUTED);
        }

        for (int r = 0; r < shown.size(); r++) {
            int y = top + (int) Math.round(r * ROW - scroll);
            if (y < top || y + ROW > bottom) {
                continue; // baris yang terpotong tidak digambar
            }
            int idx = shown.get(r);
            PlaylistManager.Track t = tracks.get(idx);
            boolean isCurrent = idx == current;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW;
            if (isCurrent) {
                g.fill(x, y, x + w, y + ROW - 2, GlassPanelRenderer.argb(accent, 0.22f));
            } else if (hover) {
                g.fill(x, y, x + w, y + ROW - 2, GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.08f));
            }

            // nomor, atau equalizer pada lagu yang sedang diputar
            int nx = x + 4;
            int cy = y + (ROW - 2) / 2;
            if (isCurrent && playing) {
                for (int i = 0; i < 3; i++) {
                    double wave = 0.5 + 0.5 * Math.sin(time * 6 + i * 1.9);
                    int bh = 3 + (int) Math.round(wave * 8);
                    int bx = nx + i * 4;
                    g.fill(bx, cy + 5 - bh, bx + 2, cy + 5, GlassPanelRenderer.argb(accent, 1f));
                }
            } else {
                String num = String.valueOf(idx + 1);
                GlassPanelRenderer.text(g, font, num, nx, cy - 4,
                        isCurrent ? accent : GlassPanelRenderer.MUTED);
            }

            // sampul persegi berwarna dengan huruf awal
            int cover = ROW - 8;
            int cx = x + 22;
            int cyTop = y + 2;
            int color = COVER_COLORS[Math.floorMod(t.title().hashCode(), COVER_COLORS.length)];
            g.fill(cx, cyTop, cx + cover, cyTop + cover, GlassPanelRenderer.argb(color, 0.9f));
            g.fill(cx, cyTop + cover - cover / 3, cx + cover, cyTop + cover,
                    GlassPanelRenderer.argb(0x000000, 0.25f));
            String initial = t.title().isEmpty() ? "?" : t.title().substring(0, 1).toUpperCase();
            GlassPanelRenderer.text(g, font, initial, cx + (cover - font.width(initial)) / 2,
                    cyTop + (cover - 9) / 2 - 1, GlassPanelRenderer.WHITE);

            int tx = cx + cover + 8;
            int maxW = x + w - 8 - tx;
            GlassPanelRenderer.text(g, font, fit(font, t.title(), maxW), tx, y + 4,
                    isCurrent ? accent : GlassPanelRenderer.WHITE);
            GlassPanelRenderer.text(g, font, fit(font, t.artist(), maxW), tx, y + 15, GlassPanelRenderer.MUTED);
        }

        // batang geser tipis di kanan
        double max = maxScroll(shown.size());
        if (max > 0) {
            int trackH = bottom - top;
            int thumbH = Math.max(14, (int) (trackH * (trackH / (trackH + max))));
            int thumbY = top + (int) ((trackH - thumbH) * (scroll / max));
            g.fill(x + w + 2, thumbY, x + w + 4, thumbY + thumbH, GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.4f));
        }

        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(XFiveLiveClient.MUSIC.status(), 48), x, bottom + 4,
                GlassPanelRenderer.MUTED);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mx = event.x();
        double my = event.y();
        if (my >= listTop() && my < listBottom() && mx >= panelX() && mx < panelX() + panelW()) {
            pressedInList = true;
            dragged = false;
            pressX = mx;
            pressY = my;
            lastY = my;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!pressedInList) {
            return super.mouseDragged(event, dx, dy);
        }
        double my = event.y();
        if (Math.abs(my - pressY) > 4 || Math.abs(event.x() - pressX) > 4) {
            dragged = true;
        }
        if (dragged) {
            scroll -= my - lastY; // geser jari ke atas = daftar naik
        }
        lastY = my;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!pressedInList) {
            return super.mouseReleased(event);
        }
        pressedInList = false;
        if (!dragged) {
            int row = (int) Math.floor((event.y() - listTop() + scroll) / ROW);
            List<Integer> shown = visibleIndexes();
            if (row >= 0 && row < shown.size()) {
                play(shown.get(row));
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * ROW;
        return true;
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

    /** Menambah path dari kotak teks. Jika gagal, isi kotak dibiarkan supaya bisa diperbaiki. */
    private void addPath() {
        String raw = pathInput.getValue();
        String msg = XFiveLiveClient.addSource(raw);
        pendingPath = msg.startsWith("Ditambahkan") ? "" : raw;
        pendingSearch = search == null ? "" : search.getValue();
        rebuildWidgets();
        XFiveLiveClient.MUSIC.setStatus(msg);
    }

    private static void play(int index) {
        XFiveLiveClient.MUSIC.playlist().select(index);
        XFiveLiveClient.MUSIC.playCurrent();
    }

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
}
