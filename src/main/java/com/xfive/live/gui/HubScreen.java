package com.xfive.live.gui;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.hud.DynamicIslandHud;
import com.xfive.live.hud.LiveChatHud;
import com.xfive.live.hud.LyricsHud;
import com.xfive.live.hud.MusicHud;
import com.xfive.live.music.MusicManager;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.Glass;
import com.xfive.live.render.GlassPanelRenderer;
import com.xfive.live.render.GlassUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Satu jendela untuk semuanya, bertema liquid glass: sidebar (Lagu, Tampilan, Audio, Edit HUD),
 * isi halaman di tengah, dan bar pemutar di bawah (sebelumnya, putar/jeda, berikutnya, progres,
 * acak, ulang, volume). Membuka jendela ini otomatis membebaskan kursor mouse.
 * Edit HUD: geser kotak HUD dengan jari, tarik titik di pojok Dynamic Island untuk mengubah ukuran.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class HubScreen extends Screen {
    public static final int PAGE_SONGS = 0;
    public static final int PAGE_DISPLAY = 1;
    public static final int PAGE_AUDIO = 2;

    private static final int ROW = 30;
    private static final int CELL_H = 22;
    private static final int CELL_GAP = 4;
    private static final int HANDLE = 7;
    private static final String[] PAGE_NAMES = {"Lagu", "Tampilan", "Audio"};
    private static final String[] LYRICS_POS = {"Atas", "Tengah", "Bawah"};
    private static final String[] REPEAT_NAMES = {"Mati", "Semua", "Satu"};
    private static final int[] COVER_COLORS = {0x965CFF, 0x40DFFF, 0xFF4FD8, 0x5CFF8A, 0xFFD84D, 0xFF7A5C};

    private interface Cell {
        void draw(int x, int y, int w);
    }

    private record CellSpec(boolean full, int h, Cell cell) {
    }

    private record Item(String key, String label, int[] box) {
    }

    private final Screen parent;
    private final GlassUi ui = new GlassUi();
    private int page;
    private boolean edit = false;
    private boolean addOpen = false;
    private String pendingSearch = "";
    private String pendingPath = "";
    private EditBox search;
    private EditBox pathInput;

    private float openT = 0f;
    private boolean closing = false;
    private long lastNs = 0L;
    private float pageT = 1f;

    private double listScroll = 0;
    private double listShown = 0;
    private double setScroll = 0;
    private double setShown = 0;
    private int scrollArea = 0; // 1 daftar lagu, 2 halaman pengaturan, 0 tidak ada
    private boolean scrolling = false;
    private double pressY;
    private double lastY;

    // geometri jendela, dihitung ulang tiap frame
    private int winX;
    private int winY;
    private int winW;
    private int winH;
    private int sideW;
    private int mainX;
    private int mainW;
    private int barH = 42;
    private int barY;
    private int contentTop;
    private int contentX;
    private int contentW;
    private int[] viewRect = new int[4];
    private int oy;

    // mode edit HUD
    private String dragKey = null;
    private int dragOffX;
    private int dragOffY;
    private int dragW;
    private int dragH;
    private boolean resizing = false;

    public HubScreen(Screen parent, int page) {
        super(Component.literal("X FIVE LIVE"));
        this.parent = parent;
        this.page = Math.max(0, Math.min(2, page));
    }

    // ---- Susunan ----

    private void layout(int offsetY) {
        oy = offsetY;
        winX = 8;
        winY = 8 + oy;
        winW = width - 16;
        winH = height - 16;
        sideW = Math.max(92, Math.min(128, width / 5));
        mainX = winX + sideW + 6;
        mainW = winX + winW - 6 - mainX;
        barY = winY + winH - 6 - barH;
        contentTop = winY + 8;
        contentX = mainX + 4;
        contentW = mainW - 8;
    }

    private int listTop() {
        return contentTop + 22 + (addOpen ? 22 : 0);
    }

    private int listBottom() {
        return barY - 6;
    }

    @Override
    protected void init() {
        XFiveLiveClient.refreshMusic();
        layout(0);
        search = null;
        pathInput = null;
        if (edit || page != PAGE_SONGS) {
            return;
        }
        int x = contentX;
        int w = contentW - 70;
        search = new EditBox(GlassPanelRenderer.font(), x, contentTop, w, 18, Component.literal("Cari lagu"));
        search.setMaxLength(60);
        search.setHint(Component.literal("Cari judul atau artis"));
        search.setValue(pendingSearch);
        search.setResponder(text -> {
            pendingSearch = text;
            listScroll = 0;
        });
        addWidget(search);
        if (addOpen) {
            pathInput = new EditBox(GlassPanelRenderer.font(), x, contentTop + 22, w, 18, Component.literal("Path lagu"));
            pathInput.setMaxLength(300);
            pathInput.setHint(Component.literal("Path folder atau file lagu"));
            pathInput.setValue(pendingPath);
            addWidget(pathInput);
        }
    }

    private void goPage(int p) {
        if (page == p && !edit) {
            return;
        }
        keepTyped();
        page = p;
        edit = false;
        pageT = 0f;
        setScroll = 0;
        setShown = 0;
        rebuildWidgets();
    }

    private void keepTyped() {
        if (search != null) {
            pendingSearch = search.getValue();
        }
        if (pathInput != null) {
            pendingPath = pathInput.getValue();
        }
    }

    // ---- Render ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        long now = System.nanoTime();
        float dt = lastNs == 0L ? 0.016f : Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        float target = closing ? 0f : 1f;
        openT += (target - openT) * (1f - (float) Math.exp(-14f * dt));
        if (Math.abs(target - openT) < 0.01f) {
            openT = target;
        }
        if (closing && openT <= 0.02f) {
            finishClose();
            return;
        }
        pageT += (1f - pageT) * (1f - (float) Math.exp(-12f * dt));
        if (pageT > 0.995f) {
            pageT = 1f;
        }
        listShown += (listScroll - listShown) * (1f - Math.exp(-16f * dt));
        setShown += (setScroll - setShown) * (1f - Math.exp(-16f * dt));

        XFiveConfig c = XFiveLiveClient.CONFIG;
        int accent = DynamicIslandHud.accentColor();
        float a = openT * openT * (3f - 2f * openT);
        gRef = g;
        ui.canvas(g);
        ui.begin(mouseX, mouseY, accent, a);
        Font font = GlassPanelRenderer.font();

        if (edit) {
            renderEdit(g, font, mouseX, mouseY, accent);
            return;
        }
        layout(Math.round((1f - a) * 16f));
        g.fill(0, 0, width, height, Glass.argb(0x000000, 0.30f * a));
        Glass.panel(g, winX, winY, winW, winH, 14, a, accent);
        renderSidebar(g, font, accent, a);
        renderBar(g, font, accent, a);

        float pa = a * (0.25f + 0.75f * pageT);
        ui.setAlpha(pa);
        int slide = Math.round((1f - pageT) * 14f);
        if (page == PAGE_SONGS) {
            renderSongs(g, font, accent, pa, slide, mouseX, mouseY);
        } else {
            renderCells(g, font, accent, pa, slide);
        }
        ui.setAlpha(a);

        if (a > 0.97f) {
            if (search != null) {
                search.extractRenderState(g, mouseX, mouseY, partialTick);
            }
            if (pathInput != null) {
                pathInput.extractRenderState(g, mouseX, mouseY, partialTick);
            }
        }
        // tombol di samping kotak teks digambar setelah halaman supaya selalu di atas
        if (page == PAGE_SONGS && a > 0.55f) {
            ui.button("add", font, contentX + contentW - 66, contentTop, 66, 18, addOpen ? "Tutup" : "+ Tambah",
                    addOpen, () -> {
                        keepTyped();
                        addOpen = !addOpen;
                        rebuildWidgets();
                    });
            if (addOpen) {
                ui.button("addok", font, contentX + contentW - 66, contentTop + 22, 66, 18, "Simpan", false,
                        this::addPath);
            }
        }
    }

    private void renderSidebar(GuiGraphicsExtractor g, Font font, int accent, float a) {
        int sx = winX + 6;
        int sy = winY + 6;
        int sw = sideW - 6;
        int sh = winH - 12;
        Glass.roundRect(g, sx, sy, sw, sh, 10, Glass.argb(0x000000, 0.22f * a));
        Glass.border(g, sx, sy, sw, sh, 10, Glass.argb(0xFFFFFF, 0.18f * a), Glass.argb(0xFFFFFF, 0.05f * a));
        if (a > 0.55f) {
            GlassPanelRenderer.text(g, font, "X FIVE LIVE", sx + 8, sy + 8, accent);
        }
        int itemH = 20;
        int itemX = sx + 5;
        int itemW = sw - 10;
        int first = sy + 24;
        // penanda aktif meluncur halus ke item terpilih
        float idx = ui.ease("nav#y", page, 14f);
        Glass.control(g, itemX, first + Math.round(idx * (itemH + 3)), itemW, itemH, 8, a, 0.4f, accent, 0.5f);
        for (int i = 0; i < PAGE_NAMES.length; i++) {
            final int p = i;
            navItem("nav" + i, font, itemX, first + i * (itemH + 3), itemW, itemH, PAGE_NAMES[i], a,
                    () -> goPage(p));
        }
        navItem("navedit", font, itemX, first + 3 * (itemH + 3), itemW, itemH, "Edit HUD", a, () -> {
            keepTyped();
            edit = true;
            rebuildWidgets();
        });
        navItem("navclose", font, itemX, sy + sh - itemH - 5, itemW, itemH, "Tutup", a, this::onClose);

        if (a > 0.55f) {
            MusicManager m = XFiveLiveClient.MUSIC;
            int ty = sy + sh - itemH - 5 - 24;
            GlassPanelRenderer.text(g, font, fit(font, m.playlist().size() + " lagu", itemW), itemX + 2, ty,
                    GlassPanelRenderer.MUTED);
            GlassPanelRenderer.text(g, font, fit(font, m.status(), itemW), itemX + 2, ty + 11,
                    GlassPanelRenderer.MUTED);
        }
    }

    private void navItem(String id, Font font, int x, int y, int w, int h, String label, float a, Runnable action) {
        float[] st = ui.row(id, x, y, w, h, action);
        int in = Math.round(st[1] * 2f);
        if (st[0] > 0.02f) {
            Glass.control(g(), x + in, y + in, w - 2 * in, h - 2 * in, 8, a * st[0] * 0.8f, st[0], 0xFFFFFF, 0f);
        }
        if (a > 0.55f) {
            GlassPanelRenderer.text(g(), font, label, x + 9 + Math.round(st[0] * 2f), y + (h - 8) / 2 + Math.round(st[1]),
                    GlassPanelRenderer.WHITE);
        }
    }

    private GuiGraphicsExtractor gRef;

    private GuiGraphicsExtractor g() {
        return gRef;
    }

    // ---- Bar pemutar ----

    private void renderBar(GuiGraphicsExtractor g, Font font, int accent, float a) {
        gRef = g;
        MusicManager m = XFiveLiveClient.MUSIC;
        XFiveConfig c = XFiveLiveClient.CONFIG;
        int bx = mainX;
        int bw = mainW;
        Glass.panel(g, bx, barY, bw, barH, 14, a * 0.95f, accent);
        PlaylistManager.Track t = m.currentTrack();
        boolean playing = m.isPlaying();

        // sampul kecil dan judul
        int cover = 26;
        int cx0 = bx + 9;
        int cy0 = barY + 6;
        int color = t == null ? accent : coverColor(t);
        Glass.roundRect(g, cx0, cy0, cover, cover, 6, Glass.argb(color, 0.95f * a), Glass.argb(color, 0.55f * a));
        Glass.border(g, cx0, cy0, cover, cover, 6, Glass.argb(0xFFFFFF, 0.5f * a), Glass.argb(0xFFFFFF, 0.1f * a));
        boolean wide = bw >= 380;
        int textW = Math.max(46, Math.min(130, bw / 4));
        int tx = cx0 + cover + 6;
        if (a > 0.55f) {
            String title = t == null ? "Belum ada lagu" : t.title();
            String sub = t == null ? m.status() : (m.isLoading() ? "Memuat..." : t.artist());
            GlassPanelRenderer.text(g, font, fit(font, title, textW), tx, barY + 7, GlassPanelRenderer.WHITE);
            GlassPanelRenderer.text(g, font, fit(font, sub, textW), tx, barY + 18, GlassPanelRenderer.MUTED);
        }

        // kelompok kanan: acak, ulang, volume
        int rightW = wide ? 120 : 62;
        int rx = bx + bw - rightW - 8;
        if (wide) {
            ui.button("shuf", font, rx, barY + 5, 32, 16, "Acak", m.shuffle(), XFiveLiveClient::toggleShuffle);
            String rl = m.repeatMode() == MusicManager.REPEAT_ONE ? "Ulang 1" : "Ulang";
            ui.button("rep", font, rx + 36, barY + 5, 44, 16, rl, m.repeatMode() != MusicManager.REPEAT_OFF,
                    XFiveLiveClient::cycleRepeat);
            ui.seek("vol", rx + 86, barY + 5, 34, 16, m.volume(), v -> {
                m.setVolume((float) v);
                c.defaultVolume = (float) v;
            });
        } else {
            ui.seek("vol", rx, barY + 5, rightW, 16, m.volume(), v -> {
                m.setVolume((float) v);
                c.defaultVolume = (float) v;
            });
        }

        // tombol tengah
        int leftEnd = tx + textW + 10;
        int rightStart = rx - 8;
        int mid = (leftEnd + rightStart) / 2;
        int cy = barY + 14;
        transportButton("prev", mid - 32, cy, 12, a, () -> m.previous(), 0, false);
        playDisc(g, mid, cy, accent, a, playing);
        transportButton("next", mid + 32, cy, 12, a, () -> m.next(), 1, false);

        // progres dengan waktu
        long dur = m.durationMs();
        long pos = m.positionMs();
        int px1 = leftEnd + 26;
        int px2 = rightStart - 26;
        if (px2 - px1 > 30) {
            ui.seek("prog", px1, barY + barH - 15, px2 - px1, 10, dur > 0 ? Math.min(1f, (float) pos / dur) : 0f,
                    v -> m.seekTo((float) v));
            if (a > 0.55f) {
                GlassPanelRenderer.text(g, font, time(pos), px1 - 26, barY + barH - 14, GlassPanelRenderer.MUTED);
                GlassPanelRenderer.text(g, font, time(dur), px2 + 4, barY + barH - 14, GlassPanelRenderer.MUTED);
            }
        }
    }

    /** Tombol sebelumnya/berikutnya: ikon segitiga dengan garis, membesar halus saat disorot. */
    private void transportButton(String id, int cx, int cy, int r, float a, Runnable action, int dir, boolean unused) {
        GuiGraphicsExtractor g = g();
        float[] st = ui.row(id, cx - r, cy - r, 2 * r, 2 * r, action);
        if (st[0] > 0.02f) {
            Glass.disc(g, cx, cy, r, Glass.argb(0xFFFFFF, 0.16f * st[0] * a));
        }
        int half = Math.max(3, Math.round(5f * (1f + 0.15f * st[0] - 0.25f * st[1])));
        int white = Glass.argb(0xFFFFFF, a);
        if (dir == 0) {
            g.fill(cx - half, cy - half, cx - half + 2, cy + half, white);
            tri(g, cx - half + 2, cy, half, half * 2 - 2, false, white);
        } else {
            g.fill(cx + half - 2, cy - half, cx + half, cy + half, white);
            tri(g, cx - half, cy, half, half * 2 - 2, true, white);
        }
    }

    private void playDisc(GuiGraphicsExtractor g, int cx, int cy, int accent, float a, boolean playing) {
        MusicManager m = XFiveLiveClient.MUSIC;
        float[] st = ui.row("play", cx - 13, cy - 13, 26, 26, () -> {
            XFiveLiveClient.refreshMusic();
            m.togglePause();
        });
        int r = Math.round(11f + 1.5f * st[0] - 3f * st[1]);
        Glass.disc(g, cx, cy, r + 1, Glass.argb(0xFFFFFF, 0.35f * a));
        Glass.disc(g, cx, cy, r, Glass.argb(accent, 0.95f * a));
        Glass.roundRect(g, cx - r + 2, cy - r + 1, 2 * r - 4, r / 2, r / 4, Glass.argb(0xFFFFFF, 0.25f * a),
                Glass.argb(0xFFFFFF, 0.02f * a));
        int white = Glass.argb(0xFFFFFF, a);
        // ikon berubah halus: dua batang saat memutar, segitiga saat dijeda
        float p = ui.ease("playicon", playing ? 1f : 0f, 18f);
        if (p > 0.5f) {
            g.fill(cx - 4, cy - 5, cx - 1, cy + 5, white);
            g.fill(cx + 1, cy - 5, cx + 4, cy + 5, white);
        } else {
            tri(g, cx - 3, cy, 5, 9, true, white);
        }
    }

    private static void tri(GuiGraphicsExtractor g, int x, int cy, int half, int width, boolean right, int argb) {
        for (int i = 0; i < width; i++) {
            float frac = right ? 1f - (float) i / width : (float) i / width;
            int hh = Math.max(1, Math.round(half * frac));
            g.fill(x + i, cy - hh, x + i + 1, cy + hh, argb);
        }
    }

    // ---- Halaman Lagu ----

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

    private void renderSongs(GuiGraphicsExtractor g, Font font, int accent, float a, int slide, int mx, int my) {
        gRef = g;
        int x = contentX;
        int w = contentW;
        int top = listTop();
        int bottom = listBottom();
        viewRect = new int[] {x, top, w, bottom - top};
        List<Integer> shown = visibleIndexes();
        double max = Math.max(0, shown.size() * ROW - (bottom - top));
        listScroll = Math.max(0, Math.min(max, listScroll));
        listShown = Math.max(0, Math.min(max, listShown));

        int current = XFiveLiveClient.MUSIC.playlist().currentIndex();
        boolean playing = XFiveLiveClient.MUSIC.isPlaying();
        List<PlaylistManager.Track> tracks = XFiveLiveClient.MUSIC.playlist().tracks();
        double time = System.nanoTime() / 1_000_000_000.0;

        if (shown.isEmpty() && a > 0.55f) {
            String msg = tracks.isEmpty() ? "Belum ada lagu. Tekan + Tambah untuk memasukkan folder." : "Tidak ada yang cocok.";
            GlassPanelRenderer.text(g, font, msg, x + 4, top + 8, GlassPanelRenderer.MUTED);
        }
        for (int r = 0; r < shown.size(); r++) {
            int y = top + (int) Math.round(r * ROW - listShown);
            if (y < top || y + ROW > bottom) {
                continue;
            }
            int idx = shown.get(r);
            PlaylistManager.Track t = tracks.get(idx);
            boolean isCurrent = idx == current;
            final int index = idx;
            int rx = x + slide;
            float[] st = ui.row("row" + idx, rx, y, w - 6, ROW - 2, () -> play(index));
            int in = Math.round(st[1] * 2f);
            float curAmt = ui.ease("cur" + idx, isCurrent ? 1f : 0f, 12f);
            if (curAmt > 0.02f || st[0] > 0.02f) {
                Glass.control(g, rx + in, y + in, w - 6 - 2 * in, ROW - 2 - 2 * in, 9, a * Math.max(curAmt, st[0] * 0.8f),
                        Math.max(curAmt * 0.5f, st[0]), accent, curAmt * 0.3f);
            }
            int cy = y + (ROW - 2) / 2;
            int nx = rx + 6;
            if (isCurrent && playing) {
                for (int i = 0; i < 3; i++) {
                    double wave = 0.5 + 0.5 * Math.sin(time * 6 + i * 1.9);
                    int bh = 3 + (int) Math.round(wave * 8);
                    int bxx = nx + i * 4;
                    g.fill(bxx, cy + 5 - bh, bxx + 2, cy + 5, Glass.argb(accent, a));
                }
            } else if (a > 0.55f) {
                GlassPanelRenderer.text(g, font, String.valueOf(idx + 1), nx, cy - 4,
                        isCurrent ? accent : GlassPanelRenderer.MUTED);
            }
            int cover = ROW - 8;
            int cxx = rx + 24;
            int cyy = y + 3;
            int color = coverColor(t);
            Glass.roundRect(g, cxx, cyy, cover, cover, 5, Glass.argb(color, 0.95f * a), Glass.argb(color, 0.55f * a));
            if (a > 0.55f) {
                String ini = t.title().isEmpty() ? "?" : t.title().substring(0, 1).toUpperCase();
                GlassPanelRenderer.text(g, font, ini, cxx + (cover - font.width(ini)) / 2, cyy + (cover - 9) / 2,
                        GlassPanelRenderer.WHITE);
                int tx = cxx + cover + 8;
                int maxW = rx + w - 12 - tx;
                GlassPanelRenderer.text(g, font, fit(font, t.title(), maxW), tx + Math.round(st[0] * 2f), y + 4,
                        isCurrent ? accent : GlassPanelRenderer.WHITE);
                GlassPanelRenderer.text(g, font, fit(font, t.artist(), maxW), tx + Math.round(st[0] * 2f), y + 15,
                        GlassPanelRenderer.MUTED);
            }
        }
        if (max > 0) {
            int trackH = bottom - top;
            int thumbH = Math.max(14, (int) (trackH * (trackH / (trackH + max))));
            int thumbY = top + (int) ((trackH - thumbH) * (listShown / max));
            Glass.roundRect(g, x + w - 3, thumbY, 2, thumbH, 1, Glass.argb(0xFFFFFF, 0.4f * a));
        }
    }

    // ---- Halaman Tampilan dan Audio ----

    private List<CellSpec> cells() {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        MusicManager m = XFiveLiveClient.MUSIC;
        Font f = GlassPanelRenderer.font();
        List<CellSpec> out = new ArrayList<>();
        if (page == PAGE_DISPLAY) {
            out.add(toggleCell(f, "t1", "Mod aktif", () -> c.enableMod, v -> c.enableMod = v));
            out.add(toggleCell(f, "t2", "HUD tampil", () -> c.showHud, v -> c.showHud = v));
            out.add(toggleCell(f, "t3", "Dynamic Island", () -> c.dynamicIsland, v -> c.dynamicIsland = v));
            out.add(toggleCell(f, "t4", "Musik", () -> c.enableMusic, v -> c.enableMusic = v));
            out.add(toggleCell(f, "t5", "Lirik", () -> c.enableLyrics, v -> c.enableLyrics = v));
            out.add(toggleCell(f, "t6", "Chat", () -> c.enableChat, v -> c.enableChat = v));
            out.add(toggleCell(f, "t7", "Auto sembunyi", () -> c.islandAutoHide, v -> c.islandAutoHide = v));
            out.add(toggleCell(f, "t8", "Tampil saat jeda", () -> c.islandShowPaused, v -> c.islandShowPaused = v));
            out.add(buttonCell(f, "b1", () -> "Bentuk: " + (c.islandCard ? "Kartu" : "Pil"),
                    () -> c.islandCard = !c.islandCard));
            out.add(buttonCell(f, "b2", () -> "Tema: " + ("cyan".equals(c.theme) ? "Cyan" : "Ungu"),
                    () -> c.theme = "cyan".equals(c.theme) ? "purple" : "cyan"));
            out.add(buttonCell(f, "b3", () -> "Warna: " + DynamicIslandHud.COLOR_NAMES[c.islandColor],
                    () -> c.islandColor = (c.islandColor + 1) % DynamicIslandHud.COLOR_NAMES.length));
            out.add(buttonCell(f, "b4", () -> "Lirik: " + LYRICS_POS[c.lyricsPosition], () -> {
                c.lyricsPosition = (c.lyricsPosition + 1) % LYRICS_POS.length;
                int[] pos = c.hudPositions.get("lyricsHud");
                pos[0] = -1;
                pos[1] = -1;
            }));
            out.add(sliderCell(f, "s1", "Transparansi", () -> Math.round(c.customTransparency * 100) + "%",
                    () -> (c.customTransparency - 0.1f) / 0.9f, v -> c.customTransparency = 0.1f + (float) v * 0.9f));
            out.add(sliderCell(f, "s2", "Lebar island", () -> String.valueOf(c.islandW),
                    () -> (c.islandW - 140) / 260f, v -> c.islandW = 140 + (int) Math.round(v * 260)));
            out.add(sliderCell(f, "s3", "Tinggi island", () -> String.valueOf(c.islandH),
                    () -> (c.islandH - 60) / 100f, v -> c.islandH = 60 + (int) Math.round(v * 100)));
            out.add(sliderCell(f, "s4", "Lama tampil", () -> c.islandShowSec + " dtk",
                    () -> (c.islandShowSec - 2) / 28f, v -> c.islandShowSec = 2 + (int) Math.round(v * 28)));
            out.add(sliderCell(f, "s5", "Ukuran lirik", () -> String.valueOf(c.lyricsFontSize),
                    () -> (c.lyricsFontSize - 6) / 14f, v -> c.lyricsFontSize = 6 + (int) Math.round(v * 14)));
            out.add(buttonCell(f, "r1", () -> "Reset ukuran island", XFiveLiveClient::resetIslandSize));
            out.add(buttonCell(f, "r2", () -> "Reset posisi HUD", c::resetHudPositions));
        } else {
            out.add(sliderCell(f, "a1", "Volume", () -> Math.round(m.volume() * 100) + "%", m::volume, v -> {
                m.setVolume((float) v);
                c.defaultVolume = (float) v;
            }));
            out.add(buttonCell(f, "a2", () -> "Ulang: " + REPEAT_NAMES[m.repeatMode()], XFiveLiveClient::cycleRepeat));
            out.add(toggleCell(f, "a3", "Acak", m::shuffle, v -> XFiveLiveClient.toggleShuffle()));
            out.add(buttonCell(f, "a4", () -> "Berhenti", m::stop));
            out.add(toggleCell(f, "a5", "Putar otomatis", () -> c.autoResume, v -> c.autoResume = v));
            out.add(buttonCell(f, "a6", () -> "Segarkan lagu", XFiveLiveClient::refreshMusic));
            Path folder = XFiveLiveClient.musicFolder;
            out.add(infoCell(f, "Folder: " + (folder == null ? "-" : tail(folder.toString(), 46))));
            out.add(infoCell(f, "Jumlah lagu: " + m.playlist().size()));
            out.add(infoCell(f, "Lirik: " + (XFiveLiveClient.LYRICS.isEmpty() ? "tidak ditemukan untuk lagu ini"
                    : XFiveLiveClient.LYRICS.lines().size() + " baris")));
            out.add(infoCell(f, "Status: " + GlassPanelRenderer.trim(m.status(), 48)));
        }
        return out;
    }

    private CellSpec toggleCell(Font f, String id, String label, java.util.function.BooleanSupplier get,
                                java.util.function.Consumer<Boolean> set) {
        return new CellSpec(false, CELL_H, (x, y, w) -> ui.toggle(id, f, x, y, w, CELL_H, label, get.getAsBoolean(),
                () -> set.accept(!get.getAsBoolean())));
    }

    private CellSpec buttonCell(Font f, String id, java.util.function.Supplier<String> label, Runnable action) {
        return new CellSpec(false, CELL_H, (x, y, w) -> ui.button(id, f, x, y, w, CELL_H, label.get(), false, action));
    }

    private CellSpec sliderCell(Font f, String id, String label, java.util.function.Supplier<String> value,
                                java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set) {
        return new CellSpec(false, CELL_H, (x, y, w) -> ui.slider(id, f, x, y, w, CELL_H, label, value.get(),
                (float) get.getAsDouble(), set));
    }

    private CellSpec infoCell(Font f, String text) {
        return new CellSpec(true, 12, (x, y, w) -> GlassPanelRenderer.text(g(), f, fit(f, text, w), x + 2, y,
                GlassPanelRenderer.MUTED));
    }

    private void renderCells(GuiGraphicsExtractor g, Font font, int accent, float a, int slide) {
        gRef = g;
        int top = contentTop;
        int bottom = listBottom();
        viewRect = new int[] {contentX, top, contentW, bottom - top};
        boolean twoCols = contentW >= 300;
        int colW = twoCols ? (contentW - 6 - 6) / 2 : contentW - 6;
        List<CellSpec> cells = cells();

        // Susun: sel biasa mengisi dua kolom, sel info memakai satu baris penuh.
        int[] ys = new int[cells.size()];
        int[] xs = new int[cells.size()];
        int cursorY = 0;
        int col = 0;
        for (int i = 0; i < cells.size(); i++) {
            CellSpec cs = cells.get(i);
            if (cs.full()) {
                if (col != 0) {
                    cursorY += CELL_H + CELL_GAP;
                    col = 0;
                }
                xs[i] = 0;
                ys[i] = cursorY;
                cursorY += cs.h() + 2;
            } else {
                xs[i] = col == 0 ? 0 : colW + 6;
                ys[i] = cursorY;
                if (twoCols && col == 0) {
                    col = 1;
                } else {
                    col = 0;
                    cursorY += CELL_H + CELL_GAP;
                }
            }
        }
        int total = cursorY + (col != 0 ? CELL_H : 0);
        double max = Math.max(0, total - (bottom - top));
        setScroll = Math.max(0, Math.min(max, setScroll));
        setShown = Math.max(0, Math.min(max, setShown));
        for (int i = 0; i < cells.size(); i++) {
            CellSpec cs = cells.get(i);
            int y = top + ys[i] - (int) Math.round(setShown);
            if (y < top || y + cs.h() > bottom) {
                continue;
            }
            cs.cell().draw(contentX + xs[i] + slide, y, cs.full() ? contentW - 6 : colW);
        }
        if (max > 0) {
            int trackH = bottom - top;
            int thumbH = Math.max(14, (int) (trackH * (trackH / (trackH + max))));
            int thumbY = top + (int) ((trackH - thumbH) * (setShown / max));
            Glass.roundRect(g, contentX + contentW - 3, thumbY, 2, thumbH, 1, Glass.argb(0xFFFFFF, 0.4f * a));
        }
    }

    // ---- Mode edit HUD ----

    private List<Item> hudItems() {
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

    private void renderEdit(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int accent) {
        gRef = g;
        XFiveConfig c = XFiveLiveClient.CONFIG;
        ui.setAlpha(1f);
        if (c.enableMusic) {
            if (c.dynamicIsland) {
                int[] b = DynamicIslandHud.box();
                PlaylistManager.Track t = XFiveLiveClient.MUSIC.currentTrack();
                String title = t == null ? "Judul lagu" : t.title();
                String artist = t == null ? "Artis" : t.artist();
                float alpha = c.hudOpacity * Math.max(0.35f, c.customTransparency);
                if (c.islandCard) {
                    DynamicIslandHud.drawCard(g, font, b[0], b[1], b[2], b[3], title, artist, 0.4f, 72_000L, 180_000L,
                            true, accent, alpha, 1f);
                } else {
                    DynamicIslandHud.drawPill(g, font, b[0], b[1], b[2], b[3], title, artist, 0.4f, 1f, accent, alpha);
                }
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
        for (Item it : hudItems()) {
            int[] b = it.box();
            boolean active = it.key().equals(dragKey) || inside(b, mouseX, mouseY);
            float glow = ui.ease("edit#" + it.key(), active ? 1f : 0f, 14f);
            outline(g, b, Glass.argb(accent, 0.4f + 0.6f * glow));
            int labelY = b[1] >= 12 ? b[1] - 11 : b[1] + b[3] + 3;
            GlassPanelRenderer.text(g, font, it.label(), b[0], labelY,
                    active ? GlassPanelRenderer.WHITE : GlassPanelRenderer.MUTED);
            if (it.key().equals("dynamicIsland")) {
                int[] hd = handle(b);
                int sz = HANDLE + Math.round(glow * 2f);
                g.fill(hd[0], hd[1], hd[0] + sz, hd[1] + sz, Glass.argb(accent, 1f));
            }
        }
        int bw = Math.min(width - 16, 330);
        int bx = (width - bw) / 2;
        int by = height - 36;
        Glass.panel(g, bx, by, bw, 28, 14, 1f, accent);
        int bwItem = (bw - 16) / 3;
        ui.button("e1", font, bx + 6, by + 4, bwItem, 20, "Selesai", true, () -> {
            edit = false;
            XFiveLiveClient.CONFIG.save();
            rebuildWidgets();
        });
        ui.button("e2", font, bx + 8 + bwItem, by + 4, bwItem, 20, "Reset posisi", false, c::resetHudPositions);
        ui.button("e3", font, bx + 10 + 2 * bwItem, by + 4, bwItem, 20, "Reset ukuran", false,
                XFiveLiveClient::resetIslandSize);
        GlassPanelRenderer.text(g, font, "Geser kotak HUD. Tarik titik di pojok Island untuk ukuran.",
                (width - font.width("Geser kotak HUD. Tarik titik di pojok Island untuk ukuran.")) / 2, by - 12,
                GlassPanelRenderer.WHITE);
    }

    // ---- Masukan ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) {
            return true;
        }
        if (!edit && super.mouseClicked(event, doubleClick)) {
            return true; // kotak teks
        }
        double mx = event.x();
        double my = event.y();
        if (ui.down(mx, my)) {
            scrollArea = edit ? 0 : areaAt(mx, my);
            scrolling = false;
            pressY = my;
            lastY = my;
            return true;
        }
        if (edit) {
            List<Item> items = hudItems();
            for (Item it : items) {
                if (it.key().equals("dynamicIsland")) {
                    int[] b = it.box();
                    int[] hd = handle(b);
                    if (mx >= hd[0] - 4 && mx < hd[0] + HANDLE + 4 && my >= hd[1] - 4 && my < hd[1] + HANDLE + 4) {
                        int[] pos = XFiveLiveClient.CONFIG.hudPositions.get("dynamicIsland");
                        pos[0] = b[0];
                        pos[1] = b[1];
                        resizing = true;
                        return true;
                    }
                }
            }
            for (int i = items.size() - 1; i >= 0; i--) {
                Item it = items.get(i);
                int[] b = it.box();
                if (inside(b, mx, my)) {
                    dragKey = it.key();
                    dragOffX = (int) mx - b[0];
                    dragOffY = (int) my - b[1];
                    dragW = b[2];
                    dragH = b[3];
                    return true;
                }
            }
            return false;
        }
        scrollArea = areaAt(mx, my);
        scrolling = false;
        pressY = my;
        lastY = my;
        return scrollArea != 0;
    }

    private int areaAt(double mx, double my) {
        if (viewRect[2] > 0 && mx >= viewRect[0] && mx < viewRect[0] + viewRect[2]
                && my >= viewRect[1] && my < viewRect[1] + viewRect[3]) {
            return page == PAGE_SONGS ? 1 : 2;
        }
        return 0;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double my = event.y();
        if (edit) {
            if (ui.drag(event.x(), my)) {
                return true;
            }
            XFiveConfig c = XFiveLiveClient.CONFIG;
            if (resizing) {
                int[] pos = c.hudPositions.get("dynamicIsland");
                c.islandW = Math.max(140, Math.min(400, (int) event.x() - pos[0]));
                c.islandH = Math.max(60, Math.min(160, (int) my - pos[1]));
                return true;
            }
            if (dragKey != null) {
                int nx = Math.max(0, Math.min(width - dragW, (int) event.x() - dragOffX));
                int ny = Math.max(0, Math.min(height - dragH, (int) my - dragOffY));
                int[] pos = c.hudPositions.get(dragKey);
                pos[0] = nx;
                pos[1] = ny;
                return true;
            }
            return false;
        }
        if (ui.drag(event.x(), my)) {
            return true;
        }
        if (scrollArea != 0 && (scrolling || Math.abs(my - pressY) > 5)) {
            if (!scrolling) {
                scrolling = true;
                ui.cancelPress();
            }
            if (scrollArea == 1) {
                listScroll += lastY - my;
            } else {
                setScroll += lastY - my;
            }
            lastY = my;
            return true;
        }
        lastY = my;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = ui.up(event.x(), event.y());
        boolean wasDrag = dragKey != null || resizing;
        dragKey = null;
        resizing = false;
        scrolling = false;
        scrollArea = 0;
        if (handled || wasDrag) {
            XFiveLiveClient.CONFIG.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (edit) {
            return false;
        }
        if (page == PAGE_SONGS) {
            listScroll -= scrollY * ROW;
        } else {
            setScroll -= scrollY * (CELL_H + CELL_GAP) * 2;
        }
        return true;
    }

    // ---- Penutupan ----

    @Override
    public void onClose() {
        if (edit) {
            edit = false;
            XFiveLiveClient.CONFIG.save();
            rebuildWidgets();
            return;
        }
        keepTyped();
        closing = true; // animasi menutup dulu, lalu finishClose()
    }

    private void finishClose() {
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

    // ---- Aksi dan bantuan ----

    private void addPath() {
        String raw = pathInput == null ? pendingPath : pathInput.getValue();
        String msg = XFiveLiveClient.addSource(raw);
        pendingPath = msg.startsWith("Ditambahkan") ? "" : raw;
        if (search != null) {
            pendingSearch = search.getValue();
        }
        XFiveLiveClient.MUSIC.setStatus(msg);
        rebuildWidgets();
    }

    private static void play(int index) {
        XFiveLiveClient.MUSIC.playlist().select(index);
        XFiveLiveClient.MUSIC.playCurrent();
    }

    private static int coverColor(PlaylistManager.Track t) {
        return COVER_COLORS[Math.floorMod(t.title().hashCode(), COVER_COLORS.length)];
    }

    private static String time(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    private static String tail(String s, int max) {
        return s.length() <= max ? s : "…" + s.substring(s.length() - max + 1);
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

    private static int[] handle(int[] b) {
        return new int[] {b[0] + b[2] - HANDLE, b[1] + b[3] - HANDLE};
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
}
