package com.xfive.live;

import com.mojang.blaze3d.platform.InputConstants;
import com.xfive.live.chat.ChatManager;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.gui.XFiveScreen;
import com.xfive.live.hud.LiveChatHud;
import com.xfive.live.hud.LyricsHud;
import com.xfive.live.hud.MusicHud;
import com.xfive.live.lyrics.LyricsManager;
import com.xfive.live.music.MusicManager;
import com.xfive.live.music.PlaylistManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Entrypoint client X FIVE LIVE.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class XFiveLiveClient implements ClientModInitializer {
    public static final String MOD_ID = "xfive-live";
    public static final XFiveConfig CONFIG = new XFiveConfig(FabricLoader.getInstance().getConfigDir());
    public static final MusicManager MUSIC = new MusicManager();
    public static final LyricsManager LYRICS = new LyricsManager();
    public static final ChatManager CHAT = new ChatManager();

    static final String SOURCE_FILE = "folder-musik.txt";
    private static final String SOURCE_TEMPLATE = String.join("\n",
            "# Tambahkan folder atau file lagu pribadi, satu per baris.",
            "# Contoh folder: /storage/emulated/0/Music",
            "# Contoh file:   /storage/emulated/0/Download/lagu.mp3",
            "# Baris yang diawali tanda # diabaikan.",
            "");

    private static KeyMapping hudKey;
    private static KeyMapping lyricsKey;
    private static KeyMapping chatKey;
    private static KeyMapping settingsKey;
    private static PlaylistManager.Track lastTrack;
    private static boolean autoResumeDone = false;

    @Override
    public void onInitializeClient() {
        CONFIG.load();
        reloadMusic();
        MUSIC.setVolume(CONFIG.defaultVolume);
        CHAT.setMaxMessages(CONFIG.maxMessages);
        CHAT.setDurationMs(CONFIG.messageDurationSec * 1000L);
        CHAT.setIntervalMs(CONFIG.messageSpeedMs);

        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        hudKey = register("key.xfive.hud", GLFW.GLFW_KEY_F8, category);
        lyricsKey = register("key.xfive.lyrics", GLFW.GLFW_KEY_F9, category);
        chatKey = register("key.xfive.chat", GLFW.GLFW_KEY_F10, category);
        settingsKey = register("key.xfive.settings", GLFW.GLFW_KEY_RIGHT_SHIFT, category);

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "overlay"),
                (graphics, delta) -> {
                    if (!CONFIG.enableMod || !CONFIG.showHud) {
                        return;
                    }
                    MusicHud.render(graphics);
                    LyricsHud.render(graphics);
                    LiveChatHud.render(graphics);
                });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> onTick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> MUSIC.shutdown());
    }

    /**
     * Memuat ulang daftar lagu dari folder default (xfive-music) dan folder-musik.txt.
     * Dipanggil saat startup dan dari tombol di menu pengaturan.
     */
    public static int reloadMusic() {
        // Folder default dibuat otomatis agar bisa langsung diisi lagu.
        Path folder = CONFIG.musicFolder.isBlank()
                ? FabricLoader.getInstance().getGameDir().resolve("xfive-music")
                : Path.of(CONFIG.musicFolder.strip());
        try {
            Files.createDirectories(folder);
        } catch (IOException ignored) {
            // folder tidak bisa dibuat, status akan menampilkan folder kosong
        }
        return MUSIC.setSources(musicSources(folder));
    }

    /**
     * Sumber musik: folder default ditambah setiap baris di folder-musik.txt.
     * File daftar dibuat otomatis berisi petunjuk jika belum ada.
     */
    static List<Path> musicSources(Path defaultDir) {
        List<Path> out = new ArrayList<>();
        out.add(defaultDir);
        Path list = defaultDir.resolve(SOURCE_FILE);
        try {
            if (!Files.exists(list)) {
                Files.writeString(list, SOURCE_TEMPLATE, StandardCharsets.UTF_8);
                return out;
            }
            for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
                String s = line.strip();
                if (s.isEmpty() || s.startsWith("#")) {
                    continue;
                }
                try {
                    out.add(Path.of(s));
                } catch (RuntimeException badPath) {
                    // path tidak valid, lewati baris ini
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // daftar tidak bisa dibaca, pakai folder default saja
        }
        return out;
    }

    private static KeyMapping register(String name, int key, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(name, InputConstants.Type.KEYSYM, key, category));
    }

    private static void onTick() {
        while (settingsKey.consumeClick()) {
            Minecraft.getInstance().setScreenAndShow(new XFiveScreen());
        }
        while (hudKey.consumeClick()) {
            CONFIG.showHud = !CONFIG.showHud;
            CONFIG.save();
        }
        while (lyricsKey.consumeClick()) {
            CONFIG.enableLyrics = !CONFIG.enableLyrics;
            CONFIG.save();
        }
        while (chatKey.consumeClick()) {
            CONFIG.enableChat = !CONFIG.enableChat;
            CONFIG.save();
        }

        CHAT.update(System.currentTimeMillis());

        if (CONFIG.enableMusic) {
            if (CONFIG.autoResume && !autoResumeDone) {
                autoResumeDone = true;
                if (MUSIC.playlist().size() > 0) {
                    MUSIC.playCurrent();
                }
            }
            MUSIC.tick();
        }
        syncLyrics();
    }

    /** Membaca file .lrc hanya saat lagu berganti, bukan tiap tick. */
    private static void syncLyrics() {
        PlaylistManager.Track track = MUSIC.currentTrack();
        if (track == lastTrack) {
            return;
        }
        lastTrack = track;
        if (track == null) {
            LYRICS.clear();
        } else {
            LYRICS.loadFile(lrcFor(track.path()));
        }
    }

    static Path lrcFor(Path audio) {
        String s = audio.toString();
        int dot = s.lastIndexOf('.');
        return Path.of((dot > 0 ? s.substring(0, dot) : s) + ".lrc");
    }
}
