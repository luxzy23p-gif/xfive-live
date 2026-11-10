package com.xfive.live.gui;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Daftar lagu yang terbaca. Ketuk judul untuk memutar. Lagu yang sedang diputar diberi tanda ">".
 * Di bawah ada kotak untuk menambah folder atau file lagu langsung dari game.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class SongListScreen extends Screen {
    private static final int PAGE_SIZE = 5;
    private static final int BTN_W = 300;
    private static final int INPUT_W = BTN_W - 80;
    private static final int TOP = 50;
    private static final int ROW = 24;

    private final Screen parent;
    private int page = 0;
    private String pendingPath = "";
    private EditBox pathInput;

    public SongListScreen(Screen parent) {
        super(Component.literal("Daftar lagu"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        XFiveLiveClient.refreshMusic();
        List<PlaylistManager.Track> tracks = XFiveLiveClient.MUSIC.playlist().tracks();
        int current = XFiveLiveClient.MUSIC.playlist().currentIndex();
        page = Math.max(0, Math.min(page, pageCount(tracks.size()) - 1));

        int x = width / 2 - BTN_W / 2;
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= tracks.size()) {
                break;
            }
            PlaylistManager.Track t = tracks.get(idx);
            String mark = idx == current ? "> " : "";
            String label = mark + (idx + 1) + ". " + GlassPanelRenderer.trim(t.title() + " - " + t.artist(), 42);
            addRenderableWidget(Button.builder(Component.literal(label), b -> play(idx))
                    .bounds(x, TOP + i * ROW, BTN_W, 20).build());
        }

        int navY = TOP + PAGE_SIZE * ROW + 6;
        addRenderableWidget(Button.builder(Component.literal("< Sebelumnya"), b -> changePage(-1))
                .bounds(x, navY, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Berikutnya >"), b -> changePage(1))
                .bounds(x + BTN_W - 140, navY, 140, 20).build());

        int inputY = navY + 36;
        pathInput = new EditBox(GlassPanelRenderer.font(), x, inputY, INPUT_W, 20, Component.literal("Path lagu"));
        pathInput.setMaxLength(300);
        pathInput.setValue(pendingPath);
        addRenderableWidget(pathInput);
        addRenderableWidget(Button.builder(Component.literal("Tambah"), b -> addPath())
                .bounds(x + INPUT_W + 4, inputY, BTN_W - INPUT_W - 4, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Kembali"), b -> onClose())
                .bounds(width / 2 - 60, inputY + 28, 120, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Font font = GlassPanelRenderer.font();
        int total = XFiveLiveClient.MUSIC.playlist().size();
        int x = width / 2 - BTN_W / 2;
        if (total == 0) {
            GlassPanelRenderer.text(g, font, "Belum ada lagu di folder musik.", x, 30, GlassPanelRenderer.MUTED);
        } else {
            GlassPanelRenderer.text(g, font,
                    "Halaman " + (page + 1) + " dari " + pageCount(total) + " (" + total + " lagu)",
                    x, 30, GlassPanelRenderer.MUTED);
        }
        GlassPanelRenderer.text(g, font, "Status: " + GlassPanelRenderer.trim(XFiveLiveClient.MUSIC.status(), 50),
                x, 40, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "Tambah folder atau file lagu:", x, 200, GlassPanelRenderer.MUTED);
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
        keepTypedPath();
        int pages = pageCount(XFiveLiveClient.MUSIC.playlist().size());
        page = Math.max(0, Math.min(pages - 1, page + delta));
        rebuildWidgets();
    }

    /** Menambah path dari kotak teks. Jika gagal, isi kotak dibiarkan supaya bisa diperbaiki. */
    private void addPath() {
        String raw = pathInput.getValue();
        String msg = XFiveLiveClient.addSource(raw);
        pendingPath = msg.startsWith("Ditambahkan") ? "" : raw;
        rebuildWidgets();
        XFiveLiveClient.MUSIC.setStatus(msg);
    }

    private void keepTypedPath() {
        if (pathInput != null) {
            pendingPath = pathInput.getValue();
        }
    }

    private static void play(int index) {
        XFiveLiveClient.MUSIC.playlist().select(index);
        XFiveLiveClient.MUSIC.playCurrent();
    }

    private static int pageCount(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }
}
