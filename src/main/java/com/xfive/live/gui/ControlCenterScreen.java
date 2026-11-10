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

/**
 * Unified XFiveLive Control Center. Sidebar navigation with Audio, HUD,
 * Custom Controls, Appearance and general settings pages.
 */
public final class ControlCenterScreen extends Screen {
    private enum Page {
        HOME("Home"), AUDIO("Audio"), HUD("HUD"),
        CONTROLS("Custom Controls"), APPEARANCE("Appearance"), SETTINGS("Settings");

        final String title;
        Page(String title) { this.title = title; }
    }

    private static final int SIDEBAR_W = 116;
    private final Page page;

    public ControlCenterScreen() {
        this(Page.HOME);
    }

    private ControlCenterScreen(Page page) {
        super(Component.literal("XFiveLive Control Center"));
        this.page = page;
    }

    @Override
    protected void init() {
        int sideX = Math.max(8, (width - 620) / 2);
        int top = Math.max(8, (height - 350) / 2);
        int panelW = Math.min(620, width - 16);
        int panelH = Math.min(350, height - 16);
        int contentX = sideX + SIDEBAR_W + 18;
        int contentW = Math.max(150, panelW - SIDEBAR_W - 34);
        int y = top + 42;

        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            Page target = pages[i];
            int by = top + 42 + i * 29;
            addRenderableWidget(button(sideX + 10, by, SIDEBAR_W - 20, 24,
                    (target == page ? "● " : "") + target.title,
                    () -> Minecraft.getInstance().setScreenAndShow(new ControlCenterScreen(target))));
        }

        switch (page) {
            case HOME -> {
                addRenderableWidget(button(contentX, y, contentW, 24, "Open Audio", () -> open(Page.AUDIO)));
                y += 34;
                addRenderableWidget(button(contentX, y, contentW, 24, "Open HUD settings", () -> open(Page.HUD)));
                y += 34;
                addRenderableWidget(button(contentX, y, contentW, 24, "Open Custom Controls", () -> open(Page.CONTROLS)));
            }
            case AUDIO -> {
                addRenderableWidget(button(contentX, y, contentW, 24, "Play / Pause",
                        () -> XFiveLiveClient.MUSIC.togglePause()));
                y += 30;
                addRenderableWidget(button(contentX, y, contentW, 24, "Previous track",
                        () -> XFiveLiveClient.MUSIC.previous()));
                y += 30;
                addRenderableWidget(button(contentX, y, contentW, 24, "Next track",
                        () -> XFiveLiveClient.MUSIC.next()));
                y += 30;
                addRenderableWidget(button(contentX, y, contentW, 24, "Refresh playlist",
                        XFiveLiveClient::refreshMusic));
                y += 30;
                addRenderableWidget(button(contentX, y, contentW, 24, "Open song list",
                        () -> Minecraft.getInstance().setScreenAndShow(new SongListScreen(this))));
                y += 30;
                addRenderableWidget(new VolumeSlider(contentX, y, contentW, 20));
            }
            case HUD -> {
                XFiveConfig c = XFiveLiveClient.CONFIG;
                addToggle(contentX, y, contentW, "Master HUD", () -> c.showHud = !c.showHud); y += 29;
                addToggle(contentX, y, contentW, "Music HUD", () -> c.enableMusic = !c.enableMusic); y += 29;
                addToggle(contentX, y, contentW, "Dynamic Island", () -> c.dynamicIsland = !c.dynamicIsland); y += 29;
                addToggle(contentX, y, contentW, "Lyrics HUD", () -> c.enableLyrics = !c.enableLyrics); y += 29;
                addToggle(contentX, y, contentW, "Live Chat HUD", () -> c.enableChat = !c.enableChat); y += 29;
                addRenderableWidget(button(contentX, y, contentW, 24, "Reset HUD positions", () -> {
                    c.resetHudPositions();
                    c.save();
                }));
            }
            case CONTROLS -> {
                addRenderableWidget(button(contentX, y, contentW, 24, "Play / Pause", () -> XFiveLiveClient.MUSIC.togglePause()));
                y += 30;
                addRenderableWidget(button(contentX, y, contentW, 24, "Next track", () -> XFiveLiveClient.MUSIC.next()));
                y += 30;
                addRenderableWidget(button(contentX, y, contentW, 24, "Previous track", () -> XFiveLiveClient.MUSIC.previous()));
                y += 30;
                addRenderableWidget(button(contentX, y, contentW, 24, "Repeat mode: " + repeatLabel(),
                        XFiveLiveClient::cycleRepeat));
                y += 30;
                addRenderableWidget(button(contentX, y, contentW, 24, "Shuffle: " + (XFiveLiveClient.CONFIG.shuffle ? "ON" : "OFF"),
                        XFiveLiveClient::toggleShuffle));
                y += 34;
                addRenderableWidget(button(contentX, y, contentW, 24, "Open Minecraft keybind settings",
                        () -> Minecraft.getInstance().setScreenAndShow(new net.minecraft.client.gui.screens.options.ControlsScreen(this, Minecraft.getInstance().options))));
            }
            case APPEARANCE -> {
                XFiveConfig c = XFiveLiveClient.CONFIG;
                addRenderableWidget(button(contentX, y, contentW, 24, "Accent: " + c.theme.toUpperCase(),
                        () -> { c.theme = "purple".equals(c.theme) ? "cyan" : "purple"; c.save(); open(Page.APPEARANCE); }));
                y += 34;
                addRenderableWidget(new OpacitySlider(contentX, y, contentW, 20));
                y += 34;
                addToggle(contentX, y, contentW, "Animations", () -> c.animations = !c.animations);
            }
            case SETTINGS -> {
                XFiveConfig c = XFiveLiveClient.CONFIG;
                addToggle(contentX, y, contentW, "Enable XFiveLive", () -> c.enableMod = !c.enableMod); y += 30;
                addToggle(contentX, y, contentW, "Auto resume music", () -> c.autoResume = !c.autoResume); y += 30;
                addToggle(contentX, y, contentW, "Show album artwork", () -> c.showArtwork = !c.showArtwork); y += 30;
                addRenderableWidget(button(contentX, y, contentW, 24, "Save settings", c::save));
            }
        }

        addRenderableWidget(button(sideX + 10, top + panelH - 34, SIDEBAR_W - 20, 24, "Close", this::onClose));
    }

    private void addToggle(int x, int y, int w, String label, Runnable action) {
        addRenderableWidget(button(x, y, w, 24, label + ": " + toggleState(label), () -> {
            action.run();
            XFiveLiveClient.CONFIG.save();
            Minecraft.getInstance().setScreenAndShow(new ControlCenterScreen(page));
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
        return value ? "ON" : "OFF";
    }

    private static String repeatLabel() {
        return switch (XFiveLiveClient.CONFIG.repeatMode) {
            case 0 -> "Off";
            case 2 -> "One";
            default -> "All";
        };
    }

    private void open(Page target) {
        Minecraft.getInstance().setScreenAndShow(new ControlCenterScreen(target));
    }

    private static Button button(int x, int y, int w, int h, String label, Runnable action) {
        return Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, h).build();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int panelW = Math.min(620, width - 16);
        int panelH = Math.min(350, height - 16);
        int x = Math.max(8, (width - 620) / 2);
        int y = Math.max(8, (height - 350) / 2);
        int accent = GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme);
        GlassPanelRenderer.panel(g, x, y, panelW, panelH, 0.72f, accent);
        g.fill(x + SIDEBAR_W, y + 12, x + SIDEBAR_W + 1, y + panelH - 12,
                GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.14f));
        Font font = GlassPanelRenderer.font();
        GlassPanelRenderer.text(g, font, "X FIVE LIVE", x + 12, y + 14, GlassPanelRenderer.WHITE);
        GlassPanelRenderer.text(g, font, "CONTROL CENTER", x + 12, y + 27, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, page.title, x + SIDEBAR_W + 18, y + 20, accent);
        PlaylistManager.Track track = XFiveLiveClient.MUSIC.currentTrack();
        String now = track == null ? XFiveLiveClient.MUSIC.status() : track.title() + " - " + track.artist();
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(now, 46),
                x + SIDEBAR_W + 18, y + panelH - 20, GlassPanelRenderer.MUTED);
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

    private static final class VolumeSlider extends AbstractSliderButton {
        VolumeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), XFiveLiveClient.CONFIG.defaultVolume);
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(Component.literal("Volume: " + Math.round(value * 100) + "%"));
        }
        @Override protected void applyValue() {
            XFiveLiveClient.CONFIG.defaultVolume = (float) value;
            XFiveLiveClient.MUSIC.setVolume((float) value);
            XFiveLiveClient.CONFIG.save();
        }
    }

    private static final class OpacitySlider extends AbstractSliderButton {
        OpacitySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(),
                    (XFiveLiveClient.CONFIG.customTransparency - 0.1) / 0.9);
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(Component.literal("Glass opacity: " + Math.round((0.1 + value * 0.9) * 100) + "%"));
        }
        @Override protected void applyValue() {
            XFiveLiveClient.CONFIG.customTransparency = (float) (0.1 + value * 0.9);
            XFiveLiveClient.CONFIG.save();
        }
    }
}
