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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Unified XFiveLive Control Center.
 * Audio playlist is embedded in this screen; HUD sizing controls from the legacy
 * settings screen are restored here. All persistent controls save to XFiveConfig.
 */
public final class ControlCenterScreen extends Screen {
    private enum Page {
        HOME("Home"), AUDIO("Audio"), HUD("HUD"),
        CONTROLS("Custom Controls"), APPEARANCE("Appearance"), SETTINGS("Settings");

        final String title;
        Page(String title) { this.title = title; }
    }

    private static final int SIDEBAR_W = 116;
    private static final int ROW_H = 22;
    private final Page page;
    private EditBox songSearch;
    private EditBox sourceInput;
    private double songScroll;

    public ControlCenterScreen() {
        this(Page.HOME);
    }

    private ControlCenterScreen(Page page) {
        super(Component.literal("XFiveLive Control Center"));
        this.page = page;
    }

    private int panelX() { return Math.max(8, (width - Math.min(720, width - 16)) / 2); }
    private int panelY() { return Math.max(8, (height - Math.min(430, height - 16)) / 2); }
    private int panelW() { return Math.min(720, width - 16); }
    private int panelH() { return Math.min(430, height - 16); }
    private int contentX() { return panelX() + SIDEBAR_W + 18; }
    private int contentW() { return Math.max(140, panelW() - SIDEBAR_W - 34); }
    private int contentTop() { return panelY() + 46; }

    @Override
    protected void init() {
        int sx = panelX();
        int sy = panelY();
        int pw = panelW();
        int ph = panelH();
        int cx = contentX();
        int cw = contentW();
        int y = contentTop();

        for (Page target : Page.values()) {
            int by = sy + 44 + target.ordinal() * 29;
            addRenderableWidget(button(sx + 10, by, SIDEBAR_W - 20, 24,
                    (target == page ? "● " : "") + target.title, () -> open(target)));
        }

        switch (page) {
            case HOME -> {
                addRenderableWidget(button(cx, y, cw, 24, "Audio & Playlist", () -> open(Page.AUDIO))); y += 34;
                addRenderableWidget(button(cx, y, cw, 24, "HUD Settings", () -> open(Page.HUD))); y += 34;
                addRenderableWidget(button(cx, y, cw, 24, "Custom Controls", () -> open(Page.CONTROLS))); y += 34;
                addRenderableWidget(button(cx, y, cw, 24, "Appearance", () -> open(Page.APPEARANCE)));
            }
            case AUDIO -> initAudio(cx, cw, y);
            case HUD -> initHud(cx, cw, y);
            case CONTROLS -> initControls(cx, cw, y);
            case APPEARANCE -> {
                XFiveConfig c = XFiveLiveClient.CONFIG;
                addRenderableWidget(button(cx, y, cw, 24, "Accent: " + c.theme.toUpperCase(Locale.ROOT),
                        () -> { c.theme = "purple".equals(c.theme) ? "cyan" : "purple"; saveAndReopen(); }));
                y += 34;
                addRenderableWidget(new OpacitySlider(cx, y, cw, 20)); y += 34;
                addToggle(cx, y, cw, "Animations", () -> c.animations = !c.animations);
            }
            case SETTINGS -> {
                XFiveConfig c = XFiveLiveClient.CONFIG;
                addToggle(cx, y, cw, "Enable XFiveLive", () -> c.enableMod = !c.enableMod); y += 29;
                addToggle(cx, y, cw, "Auto resume music", () -> c.autoResume = !c.autoResume); y += 29;
                addToggle(cx, y, cw, "Show album artwork", () -> c.showArtwork = !c.showArtwork); y += 29;
                addRenderableWidget(button(cx, y, cw, 24, "Save settings", c::save));
            }
        }

        addRenderableWidget(button(sx + 10, sy + ph - 34, SIDEBAR_W - 20, 24, "Close", this::onClose));
    }

    private void initAudio(int x, int w, int y) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        addRenderableWidget(button(x, y, w / 3 - 3, 24, "Previous", () -> { XFiveLiveClient.MUSIC.previous(); refreshPage(); }));
        addRenderableWidget(button(x + w / 3, y, w / 3 - 3, 24, XFiveLiveClient.MUSIC.isPlaying() ? "Pause" : "Play",
                () -> { XFiveLiveClient.MUSIC.togglePause(); refreshPage(); }));
        addRenderableWidget(button(x + 2 * w / 3, y, w - 2 * (w / 3) , 24, "Next",
                () -> { XFiveLiveClient.MUSIC.next(); refreshPage(); }));
        y += 30;
        addRenderableWidget(new VolumeSlider(x, y, w, 20)); y += 27;
        addRenderableWidget(button(x, y, w / 2 - 3, 22, "Repeat: " + repeatLabel(),
                () -> { XFiveLiveClient.cycleRepeat(); refreshPage(); }));
        addRenderableWidget(button(x + w / 2 + 3, y, w / 2 - 3, 22,
                "Shuffle: " + onOff(c.shuffle), () -> { XFiveLiveClient.toggleShuffle(); refreshPage(); }));
        y += 27;
        songSearch = new EditBox(GlassPanelRenderer.font(), x, y, w, 18, Component.literal("Search songs"));
        songSearch.setMaxLength(80);
        songSearch.setHint(Component.literal("Search title or artist"));
        songSearch.setResponder(s -> songScroll = 0);
        addRenderableWidget(songSearch);
        y += 23;
        int inputW = Math.max(70, w - 62);
        sourceInput = new EditBox(GlassPanelRenderer.font(), x, panelY() + panelH() - 53, inputW, 18,
                Component.literal("Add music path"));
        sourceInput.setMaxLength(300);
        sourceInput.setHint(Component.literal("Folder/file path"));
        addRenderableWidget(sourceInput);
        addRenderableWidget(button(x + inputW + 4, panelY() + panelH() - 53, w - inputW - 4, 18,
                "Add", () -> {
                    String result = XFiveLiveClient.addSource(sourceInput.getValue());
                    sourceInput.setValue("");
                    XFiveLiveClient.refreshMusic();
                    refreshPage();
                }));
        addRenderableWidget(button(x, panelY() + panelH() - 29, w, 18, "Refresh playlist",
                () -> { XFiveLiveClient.refreshMusic(); songScroll = 0; refreshPage(); }));
    }

    private void initHud(int x, int w, int y) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        addToggle(x, y, w, "Master HUD", () -> c.showHud = !c.showHud); y += 28;
        addToggle(x, y, w, "Music HUD", () -> c.enableMusic = !c.enableMusic); y += 28;
        addToggle(x, y, w, "Dynamic Island", () -> c.dynamicIsland = !c.dynamicIsland); y += 28;
        addToggle(x, y, w, "Lyrics HUD", () -> c.enableLyrics = !c.enableLyrics); y += 28;
        addToggle(x, y, w, "Live Chat HUD", () -> c.enableChat = !c.enableChat); y += 32;
        addRenderableWidget(new HudOpacitySlider(x, y, w, 20)); y += 27;
        addRenderableWidget(button(x, y, w, 22, "Reset HUD positions", () -> {
            c.resetHudPositions(); c.save(); refreshPage();
        }));
    }

    private void initControls(int x, int w, int y) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        addRenderableWidget(button(x, y, w, 22, "Dynamic Island: " + (c.islandCard ? "Card" : "Pill"),
                () -> { XFiveLiveClient.toggleIslandCard(); refreshPage(); })); y += 26;
        addRenderableWidget(new IslandWidthSlider(x, y, w, 20)); y += 25;
        addRenderableWidget(new IslandHeightSlider(x, y, w, 20)); y += 25;
        addRenderableWidget(button(x, y, w, 22, "Island color: " + c.islandColor,
                () -> { XFiveLiveClient.cycleIslandColor(); refreshPage(); })); y += 26;
        addRenderableWidget(button(x, y, w, 22, "Show while paused: " + onOff(c.islandShowPaused),
                () -> { XFiveLiveClient.toggleIslandShowPaused(); refreshPage(); })); y += 26;
        addRenderableWidget(button(x, y, w, 22, "Repeat: " + repeatLabel(),
                () -> { XFiveLiveClient.cycleRepeat(); refreshPage(); })); y += 26;
        addRenderableWidget(button(x, y, w, 22, "Shuffle: " + onOff(c.shuffle),
                () -> { XFiveLiveClient.toggleShuffle(); refreshPage(); })); y += 26;
        addRenderableWidget(button(x, y, w, 22, "Reset Island size", () -> {
            XFiveLiveClient.resetIslandSize(); refreshPage();
        }));
    }

    private void addToggle(int x, int y, int w, String label, Runnable action) {
        addRenderableWidget(button(x, y, w, 23, label + ": " + toggleState(label), () -> {
            action.run(); XFiveLiveClient.CONFIG.save(); refreshPage();
        }));
    }

    private String toggleState(String label) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        boolean value = switch (label) {
            case "Master HUD" -> c.showHud;
            case "Music HUD" -> c.enableMusic;
            case "Dynamic Island" -> c.dynamicIsland;
            case "Lyrics HUD" -> c.enableLyrics;
            case "Live Chat HUD" -> c.enableChat;
            case "Animations" -> c.animations;
            case "Enable XFiveLive" -> c.enableMod;
            case "Auto resume music" -> c.autoResume;
            case "Show album artwork" -> c.showArtwork;
            default -> false;
        };
        return onOff(value);
    }

    private void drawAudioList(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        if (page != Page.AUDIO || songSearch == null) return;
        int x = contentX();
        int w = contentW();
        int top = songSearch.getY() + 21;
        int bottom = panelY() + panelH() - 78;
        List<PlaylistManager.Track> tracks = XFiveLiveClient.MUSIC.playlist().tracks();
        String query = songSearch.getValue().strip().toLowerCase(Locale.ROOT);
        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            PlaylistManager.Track t = tracks.get(i);
            if (query.isEmpty() || t.title().toLowerCase(Locale.ROOT).contains(query)
                    || t.artist().toLowerCase(Locale.ROOT).contains(query)) shown.add(i);
        }
        int visibleRows = Math.max(1, (bottom - top) / ROW_H);
        songScroll = Math.max(0, Math.min(Math.max(0, shown.size() - visibleRows), songScroll));
        int start = (int) songScroll;
        for (int r = 0; r < visibleRows && start + r < shown.size(); r++) {
            int idx = shown.get(start + r);
            PlaylistManager.Track t = tracks.get(idx);
            int ry = top + r * ROW_H;
            boolean current = idx == XFiveLiveClient.MUSIC.playlist().currentIndex();
            boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY <= ry + ROW_H;
            if (current || hover) {
                g.fill(x, ry, x + w, ry + ROW_H - 1,
                        GlassPanelRenderer.argb(GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme), current ? 0.28f : 0.12f));
            }
            String title = GlassPanelRenderer.trim(t.title(), 38);
            String artist = GlassPanelRenderer.trim(t.artist(), 42);
            GlassPanelRenderer.text(g, font, (current ? "▶ " : (idx + 1) + "  ") + title, x + 5, ry + 2,
                    current ? GlassPanelRenderer.WHITE : GlassPanelRenderer.MUTED);
            GlassPanelRenderer.text(g, font, artist, x + 25, ry + 12, GlassPanelRenderer.MUTED);
        }
        String count = shown.size() + " / " + tracks.size() + " songs";
        GlassPanelRenderer.text(g, font, count, x + w - font.width(count), bottom + 2, GlassPanelRenderer.MUTED);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = panelX(), y = panelY(), w = panelW(), h = panelH();
        int accent = GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme);
        GlassPanelRenderer.panel(g, x, y, w, h, XFiveLiveClient.CONFIG.customTransparency, accent);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.fill(x + SIDEBAR_W, y + 12, x + SIDEBAR_W + 1, y + h - 12,
                GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.14f));
        Font font = GlassPanelRenderer.font();
        GlassPanelRenderer.text(g, font, "X FIVE LIVE", x + 12, y + 14, GlassPanelRenderer.WHITE);
        GlassPanelRenderer.text(g, font, "CONTROL CENTER", x + 12, y + 27, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, page.title, x + SIDEBAR_W + 18, y + 20, accent);
        drawAudioList(g, font, mouseX, mouseY);
        PlaylistManager.Track track = XFiveLiveClient.MUSIC.currentTrack();
        String now = track == null ? XFiveLiveClient.MUSIC.status() : track.title() + " - " + track.artist();
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(now, 46),
                x + SIDEBAR_W + 18, y + h - 18, GlassPanelRenderer.MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (page == Page.AUDIO && songSearch != null) {
            int x = contentX();
            int w = contentW();
            int top = songSearch.getY() + 21;
            int bottom = panelY() + panelH() - 78;
            if (event.x() >= x && event.x() <= x + w && event.y() >= top && event.y() < bottom) {
                int visibleRows = Math.max(1, (bottom - top) / ROW_H);
                int row = (int) ((event.y() - top) / ROW_H);
                List<Integer> shown = filteredSongIndexes();
                int index = (int) songScroll + row;
                if (row < visibleRows && index >= 0 && index < shown.size()) {
                    XFiveLiveClient.MUSIC.playlist().select(shown.get(index));
                    XFiveLiveClient.MUSIC.playCurrent();
                    return true;
                }
            }
        }
        return false;
    }

    private List<Integer> filteredSongIndexes() {
        List<PlaylistManager.Track> tracks = XFiveLiveClient.MUSIC.playlist().tracks();
        String query = songSearch == null ? "" : songSearch.getValue().strip().toLowerCase(Locale.ROOT);
        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            PlaylistManager.Track t = tracks.get(i);
            if (query.isEmpty() || t.title().toLowerCase(Locale.ROOT).contains(query)
                    || t.artist().toLowerCase(Locale.ROOT).contains(query)) shown.add(i);
        }
        return shown;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page == Page.AUDIO) {
            songScroll -= scrollY;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static String repeatLabel() {
        return switch (XFiveLiveClient.CONFIG.repeatMode) {
            case 0 -> "Off";
            case 2 -> "One";
            default -> "All";
        };
    }

    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
    private void open(Page target) { Minecraft.getInstance().setScreenAndShow(new ControlCenterScreen(target)); }
    private void refreshPage() { XFiveLiveClient.CONFIG.save(); Minecraft.getInstance().setScreenAndShow(new ControlCenterScreen(page)); }
    private void saveAndReopen() { XFiveLiveClient.CONFIG.save(); refreshPage(); }

    private static Button button(int x, int y, int w, int h, String label, Runnable action) {
        return Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, h).build();
    }

    @Override public void onClose() { XFiveLiveClient.CONFIG.save(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }

    private static final class VolumeSlider extends AbstractSliderButton {
        VolumeSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), XFiveLiveClient.CONFIG.defaultVolume); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Volume: " + Math.round(value * 100) + "%")); }
        @Override protected void applyValue() {
            XFiveLiveClient.CONFIG.defaultVolume = (float) value;
            XFiveLiveClient.MUSIC.setVolume((float) value);
            XFiveLiveClient.CONFIG.save();
        }
    }

    private static final class OpacitySlider extends AbstractSliderButton {
        OpacitySlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), (XFiveLiveClient.CONFIG.customTransparency - 0.1) / 0.9); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Glass opacity: " + Math.round((0.1 + value * 0.9) * 100) + "%")); }
        @Override protected void applyValue() { XFiveLiveClient.CONFIG.customTransparency = (float) (0.1 + value * 0.9); XFiveLiveClient.CONFIG.save(); }
    }

    private static final class HudOpacitySlider extends AbstractSliderButton {
        HudOpacitySlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), (XFiveLiveClient.CONFIG.hudOpacity - 0.1) / 0.9); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("HUD opacity: " + Math.round((0.1 + value * 0.9) * 100) + "%")); }
        @Override protected void applyValue() { XFiveLiveClient.CONFIG.hudOpacity = (float) (0.1 + value * 0.9); XFiveLiveClient.CONFIG.save(); }
    }

    private static final class IslandWidthSlider extends AbstractSliderButton {
        IslandWidthSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), (XFiveLiveClient.CONFIG.islandW - 140) / 260.0); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Bar width: " + XFiveLiveClient.CONFIG.islandW + " px")); }
        @Override protected void applyValue() { XFiveLiveClient.CONFIG.islandW = 140 + (int) Math.round(value * 260); XFiveLiveClient.CONFIG.save(); }
    }

    private static final class IslandHeightSlider extends AbstractSliderButton {
        IslandHeightSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), (XFiveLiveClient.CONFIG.islandH - 60) / 100.0); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Bar height: " + XFiveLiveClient.CONFIG.islandH + " px")); }
        @Override protected void applyValue() { XFiveLiveClient.CONFIG.islandH = 60 + (int) Math.round(value * 100); XFiveLiveClient.CONFIG.save(); }
    }
}
