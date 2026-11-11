package com.xfive.live.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xfive.live.XFiveLiveClient;
import com.xfive.live.render.GlassPanelRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Online catalog search, temporary online playback, and creator-authorized downloads.
 * Audius current API uses is_streamable / is_downloadable fields.
 */
public final class OnlineMusicScreen extends Screen {
    private static final String API = "https://api.audius.co/v1";
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "xfive-online-music");
        t.setDaemon(true);
        return t;
    });
    private static final long MAX_BYTES = 100L * 1024L * 1024L;

    private record Track(String id, String title, String artist, long duration,
                         boolean streamable, boolean downloadable) {}
    private EditBox queryBox;
    private volatile List<Track> results = List.of();
    private volatile String status = "Search Audius tracks by title or artist.";
    private volatile boolean searching;
    private volatile boolean busy;
    private final List<Button> playButtons = new ArrayList<>();
    private final List<Button> downloadButtons = new ArrayList<>();

    public OnlineMusicScreen() { super(Component.literal("XFiveLive Online Music")); }

    private int px() { return Math.max(8, (width - Math.min(720, width - 16)) / 2); }
    private int py() { return Math.max(8, (height - Math.min(430, height - 16)) / 2); }
    private int pw() { return Math.min(720, width - 16); }
    private int ph() { return Math.min(430, height - 16); }
    private int left() { return px() + 18; }
    private int right() { return px() + pw() - 18; }
    private int listTop() { return py() + 92; }

    @Override protected void init() {
        int x = left(), y = py() + 43, w = right() - left();
        int searchW = Math.min(76, Math.max(58, w / 5));
        queryBox = new EditBox(GlassPanelRenderer.font(), x, y, w - searchW - 6, 20,
                Component.literal("Search Audius music"));
        queryBox.setMaxLength(100);
        queryBox.setHint(Component.literal("Song title or artist"));
        addRenderableWidget(queryBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), b -> search())
                .bounds(x + w - searchW, y, searchW, 20).build());

        int rows = Math.min(7, Math.max(3, (ph() - 145) / 34));
        for (int i = 0; i < rows; i++) {
            final int index = i;
            Button play = Button.builder(Component.literal("Play"), b -> playOnline(index))
                    .bounds(right() - 132, listTop() + i * 34 + 4, 60, 22).build();
            Button download = Button.builder(Component.literal("Save"), b -> download(index))
                    .bounds(right() - 66, listTop() + i * 34 + 4, 60, 22).build();
            play.active = false;
            download.active = false;
            playButtons.add(play);
            downloadButtons.add(download);
            addRenderableWidget(play);
            addRenderableWidget(download);
        }
        addRenderableWidget(Button.builder(Component.literal("Back"),
                b -> Minecraft.getInstance().setScreenAndShow(ControlCenterScreen.audio()))
                .bounds(px() + 18, py() + ph() - 34, 72, 22).build());
    }

    private void search() {
        String query = queryBox == null ? "" : queryBox.getValue().strip();
        if (query.isEmpty()) { status = "Enter a song title or artist first."; return; }
        if (searching) return;
        searching = true;
        results = List.of();
        status = "Searching online catalog...";
        NETWORK.submit(() -> {
            try {
                String url = API + "/tracks/search?query="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&limit=20&app_name=xfive_live";
                String body = requestText(url);
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                List<Track> found = new ArrayList<>();
                JsonElement data = root.get("data");
                if (data != null && data.isJsonArray()) {
                    for (JsonElement element : data.getAsJsonArray()) {
                        if (!element.isJsonObject()) continue;
                        JsonObject item = element.getAsJsonObject();
                        String id = value(item, "id");
                        String title = value(item, "title");
                        JsonObject user = item.has("user") && item.get("user").isJsonObject()
                                ? item.getAsJsonObject("user") : new JsonObject();
                        String artist = value(user, "name");
                        long duration = item.has("duration") && !item.get("duration").isJsonNull()
                                ? item.get("duration").getAsLong() : 0L;
                        boolean streamable = bool(item, "is_streamable");
                        boolean downloadable = bool(item, "is_downloadable");
                        if (!id.isBlank() && !title.isBlank() && (streamable || downloadable)) {
                            found.add(new Track(id, title,
                                    artist.isBlank() ? "Unknown artist" : artist,
                                    duration, streamable, downloadable));
                        }
                    }
                }
                results = List.copyOf(found);
                status = found.isEmpty()
                        ? "No playable tracks found. Try another title or artist."
                        : found.size() + " results. Play streams online or save allowed tracks.";
            } catch (Exception e) {
                status = "Search failed: " + message(e);
            } finally {
                searching = false;
            }
        });
    }

    private void playOnline(int index) {
        Track track = getTrack(index);
        if (track == null || !track.streamable() || busy) return;
        busy = true;
        status = "Preparing online playback: " + track.title();
        NETWORK.submit(() -> {
            Path temp = null;
            try {
                Path cache = FabricLoader.getInstance().getGameDir().resolve("xfive-online-cache");
                Files.createDirectories(cache);
                temp = Files.createTempFile(cache, "online-", ".mp3");
                transfer(API + "/tracks/" + track.id() + "/stream?app_name=xfive_live", temp);
                Path playable = temp;
                Minecraft.getInstance().execute(() -> {
                    boolean started = XFiveLiveClient.MUSIC.playOnlineFile(
                            playable, track.title(), track.artist());
                    status = started
                            ? "Online track prepared: " + track.title()
                            : "Could not start playback: " + XFiveLiveClient.MUSIC.status();
                    busy = false;
                });
                temp = null; // Keep cache file while the decoder/player may still need it.
            } catch (Exception e) {
                status = "Online playback failed: " + message(e);
                busy = false;
            } finally {
                if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
        });
    }

    private void download(int index) {
        Track track = getTrack(index);
        if (track == null || !track.downloadable() || busy) return;
        busy = true;
        status = "Saving: " + track.title();
        NETWORK.submit(() -> {
            Path temp = null;
            try {
                Path folder = FabricLoader.getInstance().getGameDir().resolve("xfive-music");
                Files.createDirectories(folder);
                String safe = (track.artist() + " - " + track.title())
                        .replaceAll("[\\\\/:*?\\\"<>|]", "_")
                        .replaceAll("\\s+", " ").strip();
                if (safe.length() > 120) safe = safe.substring(0, 120).strip();
                Path target = folder.resolve(safe + ".mp3");
                int suffix = 2;
                while (Files.exists(target)) target = folder.resolve(safe + " (" + suffix++ + ").mp3");
                temp = Files.createTempFile(folder, "xfive-download-", ".part");
                transfer(API + "/tracks/" + track.id() + "/download?app_name=xfive_live", temp);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                temp = null;
                Minecraft.getInstance().execute(() -> {
                    XFiveLiveClient.refreshMusic();
                    status = "Saved to xfive-music: " + track.title();
                    busy = false;
                });
            } catch (Exception e) {
                status = "Download failed: " + message(e);
                busy = false;
            } finally {
                if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
        });
    }

    private Track getTrack(int index) {
        List<Track> snapshot = results;
        return index >= 0 && index < snapshot.size() ? snapshot.get(index) : null;
    }

    private static String requestText(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static void transfer(String url, Path destination) throws IOException {
        HttpURLConnection connection = open(url);
        try {
            String type = connection.getContentType();
            if (type != null && (type.contains("application/json") || type.contains("text/html"))) {
                throw new IOException("The music server returned a web response, not audio.");
            }
            long total = 0;
            try (InputStream in = connection.getInputStream();
                 var out = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("Audio exceeds 100 MB.");
                    out.write(buffer, 0, n);
                }
            }
            if (total < 128) throw new IOException("Audio response was empty or incomplete.");
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(90000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "XFiveLive/1.2");
        connection.setRequestProperty("Accept", "audio/mpeg,application/json;q=0.8,*/*;q=0.5");
        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("Music service HTTP " + code);
        }
        return connection;
    }

    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }

    private static String value(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    private static String message(Exception e) {
        String s = e.getMessage();
        if (s == null || s.isBlank()) return e.getClass().getSimpleName();
        return s.length() > 110 ? s.substring(0, 110) : s;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py(), w = pw(), h = ph();
        List<Track> snapshot = results;
        for (int i = 0; i < playButtons.size(); i++) {
            Track t = i < snapshot.size() ? snapshot.get(i) : null;
            playButtons.get(i).active = !busy && t != null && t.streamable();
            downloadButtons.get(i).active = !busy && t != null && t.downloadable();
        }
        int accent = GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme);
        GlassPanelRenderer.panel(g, x, y, w, h, XFiveLiveClient.CONFIG.customTransparency, accent);
        Font font = GlassPanelRenderer.font();
        GlassPanelRenderer.text(g, font, "ONLINE MUSIC", x + 18, y + 16, accent);
        GlassPanelRenderer.text(g, font, "Search • stream • creator-enabled downloads",
                x + 18, y + 29, GlassPanelRenderer.MUTED);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        GlassPanelRenderer.text(g, font, status, left(), py() + 72, GlassPanelRenderer.WHITE);
        int rows = Math.min(7, Math.max(3, (h - 145) / 34));
        for (int i = 0; i < rows && i < snapshot.size(); i++) {
            Track t = snapshot.get(i);
            int ry = listTop() + i * 34;
            String title = t.title().length() > 35 ? t.title().substring(0, 34) + "…" : t.title();
            String artist = t.artist() + (t.duration() > 0
                    ? " • " + t.duration() / 60 + ":" + String.format(Locale.ROOT, "%02d", t.duration() % 60) : "");
            GlassPanelRenderer.text(g, font, title, left() + 7, ry + 3, GlassPanelRenderer.WHITE);
            GlassPanelRenderer.text(g, font, artist.length() > 36 ? artist.substring(0, 35) + "…" : artist,
                    left() + 7, ry + 16, GlassPanelRenderer.MUTED);
        }
        GlassPanelRenderer.text(g, font, "Play Online uses a temporary cache; Save stores allowed tracks in xfive-music.",
                left(), y + h - 52, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "Only tracks whose creators enable downloads can be saved.",
                left(), y + h - 40, GlassPanelRenderer.MUTED);
    }

    @Override public boolean isPauseScreen() { return false; }
}
