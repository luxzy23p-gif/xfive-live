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
import java.net.URI;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Online search and artist-enabled downloads through the Audius public API. */
public final class OnlineMusicScreen extends Screen {
    private static final String API = "https://discoveryprovider.audius.co/v1";
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "xfive-online-music");
        t.setDaemon(true);
        return t;
    });
    private static final long MAX_BYTES = 100L * 1024L * 1024L;

    private record Track(String id, String title, String artist, long duration) {}
    private EditBox queryBox;
    private volatile List<Track> results = List.of();
    private volatile String status = "Search music shared by artists on Audius.";
    private volatile boolean searching;
    private volatile boolean downloading;

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
            addRenderableWidget(Button.builder(Component.literal("Download"), b -> download(index))
                    .bounds(right() - 84, listTop() + i * 34 + 4, 78, 22).build());
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
        status = "Searching Audius...";
        results = List.of();
        NETWORK.submit(() -> {
            try {
                String url = API + "/tracks/search?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&limit=10&app_name=xfive_live";
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(25000);
                connection.setRequestProperty("User-Agent", "XFiveLive/1.0");
                if (connection.getResponseCode() != 200) {
                    int code = connection.getResponseCode();
                    connection.disconnect();
                    throw new IOException("Audius HTTP " + code);
                }
                String body;
                try (InputStream in = connection.getInputStream()) {
                    body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } finally { connection.disconnect(); }
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                List<Track> found = new ArrayList<>();
                JsonElement data = root.get("data");
                if (data != null && data.isJsonArray()) {
                    for (JsonElement element : data.getAsJsonArray()) {
                        if (!element.isJsonObject()) continue;
                        JsonObject item = element.getAsJsonObject();
                        if (!item.has("downloadable") || !item.get("downloadable").getAsBoolean()) continue;
                        String id = value(item, "id"), title = value(item, "title");
                        JsonObject user = item.has("user") && item.get("user").isJsonObject()
                                ? item.getAsJsonObject("user") : new JsonObject();
                        String artist = value(user, "name");
                        long duration = item.has("duration") && !item.get("duration").isJsonNull()
                                ? item.get("duration").getAsLong() : 0L;
                        if (!id.isBlank() && !title.isBlank())
                            found.add(new Track(id, title, artist.isBlank() ? "Unknown artist" : artist, duration));
                    }
                }
                results = List.copyOf(found);
                status = found.isEmpty() ? "No downloadable tracks found. Try another search."
                        : found.size() + " downloadable tracks found.";
            } catch (Exception e) { status = "Search failed: " + message(e); }
            finally { searching = false; }
        });
    }

    private void download(int index) {
        List<Track> snapshot = results;
        if (downloading || index < 0 || index >= snapshot.size()) return;
        Track track = snapshot.get(index);
        downloading = true;
        status = "Downloading: " + track.title();
        NETWORK.submit(() -> {
            Path temp = null;
            try {
                String url = API + "/tracks/" + track.id() + "/download?app_name=xfive_live";
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(45000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "XFiveLive/1.0");
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    connection.disconnect();
                    throw new IOException("Download HTTP " + responseCode);
                }
                Path folder = FabricLoader.getInstance().getGameDir().resolve("xfive-music");
                Files.createDirectories(folder);
                String safe = (track.artist() + " - " + track.title())
                        .replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").strip();
                if (safe.length() > 120) safe = safe.substring(0, 120).strip();
                Path target = folder.resolve(safe + ".mp3");
                int suffix = 2;
                while (Files.exists(target)) target = folder.resolve(safe + " (" + suffix++ + ").mp3");
                temp = Files.createTempFile(folder, "xfive-download-", ".part");
                long total = 0;
                try (InputStream in = connection.getInputStream(); var out = Files.newOutputStream(temp)) {
                    byte[] buffer = new byte[16384];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        total += n;
                        if (total > MAX_BYTES) throw new IOException("Track exceeds 100 MB.");
                        out.write(buffer, 0, n);
                    }
                }
                if (total < 16) throw new IOException("Downloaded file is empty.");
                connection.disconnect();
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                temp = null;
                Minecraft.getInstance().execute(() -> {
                    XFiveLiveClient.refreshMusic();
                    status = "Saved to xfive-music: " + track.title();
                });
            } catch (Exception e) { status = "Download failed: " + message(e); }
            finally {
                if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
                downloading = false;
            }
        });
    }

    private static String value(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static String message(Exception e) {
        String s = e.getMessage();
        if (s == null || s.isBlank()) return e.getClass().getSimpleName();
        return s.length() > 100 ? s.substring(0, 100) : s;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py(), w = pw(), h = ph();
        int accent = GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme);
        GlassPanelRenderer.panel(g, x, y, w, h, XFiveLiveClient.CONFIG.customTransparency, accent);
        Font font = GlassPanelRenderer.font();
        GlassPanelRenderer.text(g, font, "ONLINE MUSIC", x + 18, y + 16, accent);
        GlassPanelRenderer.text(g, font, "Audius • artist-enabled downloads", x + 18, y + 29, GlassPanelRenderer.MUTED);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        GlassPanelRenderer.text(g, font, status, left(), py() + 72, GlassPanelRenderer.WHITE);
        List<Track> snapshot = results;
        int rows = Math.min(7, Math.max(3, (h - 145) / 34));
        for (int i = 0; i < rows && i < snapshot.size(); i++) {
            Track t = snapshot.get(i);
            int ry = listTop() + i * 34;
            String title = t.title().length() > 48 ? t.title().substring(0, 47) + "…" : t.title();
            String artist = t.artist() + (t.duration() > 0
                    ? " • " + t.duration() / 60 + ":" + String.format(Locale.ROOT, "%02d", t.duration() % 60) : "");
            GlassPanelRenderer.text(g, font, title, left() + 7, ry + 3, GlassPanelRenderer.WHITE);
            GlassPanelRenderer.text(g, font, artist.length() > 64 ? artist.substring(0, 63) + "…" : artist,
                    left() + 7, ry + 16, GlassPanelRenderer.MUTED);
        }
        GlassPanelRenderer.text(g, font, "Downloaded songs are saved in the xfive-music folder.",
                left(), y + h - 52, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "Only tracks marked downloadable by their creators are shown.",
                left(), y + h - 40, GlassPanelRenderer.MUTED);
    }

    @Override public boolean isPauseScreen() { return false; }
}
