package com.xfive.live.tests;

import com.xfive.live.chat.ChatManager;
import com.xfive.live.chat.ChatMessage;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.lyrics.LrcParser;
import com.xfive.live.lyrics.LyricLine;
import com.xfive.live.lyrics.LyricsManager;
import com.xfive.live.music.PlaylistManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Uji unit untuk logika murni (tanpa Minecraft). Jalankan: java -ea ... XFiveTests <folder kerja> */
public final class XFiveTests {
    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        Path work = Path.of(args[0]);
        Files.createDirectories(work);
        testLrc();
        testLyricsIndex();
        testChat();
        testPlaylist(work.resolve("playlist"));
        testConfig(work.resolve("config"));
        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void testLrc() {
        String lrc = "[ti:Demo]\n[ar:Tester]\n[offset:500]\n[00:01.50]Hello\n"
                + "[00:03.250][00:05]World\n\n[00:02.5]Mid";
        List<LyricLine> lines = LrcParser.parse(lrc);
        check("lrc jumlah baris = 4 (World dua timestamp)", lines.size() == 4);
        check("lrc urut waktu", lines.size() == 4 && lines.get(0).timeMs() <= lines.get(1).timeMs()
                && lines.get(1).timeMs() <= lines.get(2).timeMs());
        check("lrc Hello di 2000ms (1500 + offset 500)", lines.get(0).timeMs() == 2000 && lines.get(0).text().equals("Hello"));
        check("lrc Mid di 3000ms (format .x + offset)", lines.get(1).timeMs() == 3000 && lines.get(1).text().equals("Mid"));
        check("lrc World 3750ms (.250 = 250ms)", lines.get(2).timeMs() == 3750 && lines.get(2).text().equals("World"));
        check("lrc World 5500ms (tanpa fraksi)", lines.get(3).timeMs() == 5500);
        check("lrc tag metadata diabaikan", LrcParser.parse("[ar:X]\n[ti:Y]").isEmpty());
        check("lrc kosong / null aman", LrcParser.parse("").isEmpty() && LrcParser.parse(null).isEmpty());
        check("lrc offset negatif tidak di bawah 0",
                LrcParser.parse("[offset:-5000]\n[00:01.00]X").get(0).timeMs() == 0L);
        check("lrc format .xx = centisecond", LrcParser.parse("[00:00.07]A").get(0).timeMs() == 70L);
    }

    static void testLyricsIndex() {
        LyricsManager lm = new LyricsManager();
        lm.load("[00:02.00]Hello\n[00:03.00]Mid\n[00:03.75]World\n[00:05.50]World2");
        check("indeks sebelum baris pertama = -1", lm.indexAt(0) == -1);
        check("indeks tepat di 2000ms = 0", lm.indexAt(2000) == 0);
        check("indeks 2999ms masih 0", lm.indexAt(2999) == 0);
        check("indeks 3000ms = 1", lm.indexAt(3000) == 1);
        check("indeks di akhir = 3", lm.indexAt(999_999) == 3);
        check("textAt(2) = World", lm.textAt(2).equals("World"));
        check("textAt di luar jangkauan = kosong", lm.textAt(99).isEmpty());
        lm.clear();
        check("clear membuat kosong", lm.isEmpty());
        check("loadFile file tidak ada = false", !lm.loadFile(Path.of("tidak-ada-file-xyz.lrc")));
    }

    static void testChat() {
        ChatManager c = new ChatManager();
        c.setMaxMessages(2);
        c.setIntervalMs(100);
        c.setDurationMs(5000);
        c.addBlockedWord("BUY");

        check("filter menolak kata terlarang (case-insensitive)", !c.submit("u", "buy now!!", 0));
        check("pesan kosong ditolak", !c.submit("u", "   ", 0));
        check("pesan diterima", c.submit(null, "a", 0));
        c.submit("bob", "b", 0);
        c.submit("cat", "c", 0);
        check("pending = 3", c.pendingCount() == 3);

        List<ChatMessage> out = new ArrayList<>();
        c.update(50);           // rilis pertama langsung
        check("rilis pertama langsung", c.copyVisibleTo(out) == 1);
        c.update(100);          // jeda belum 100ms
        check("jeda kecepatan ditahan", c.copyVisibleTo(out) == 1);
        c.update(150);          // rilis b
        c.update(250);          // rilis c, batas 2 membuang a
        check("batas maksimum pesan = 2", c.copyVisibleTo(out) == 2);
        check("pesan lama dibuang, sisa b dan c",
                out.get(0).text().equals("b") && out.get(1).text().equals("c"));
        check("username kosong jadi viewer", out.get(0).username().equals("bob"));

        c.update(5200);         // b (rilis 150) sudah lewat 5000ms, c (rilis 250) belum
        check("pesan kedaluwarsa dibuang", c.copyVisibleTo(out) == 1 && out.get(0).text().equals("c"));

        ChatManager d = new ChatManager();
        d.submit(null, "halo", 0);
        d.update(1000);
        d.copyVisibleTo(out);
        check("username null menjadi viewer", out.size() == 1 && out.get(0).username().equals("viewer"));
    }

    static void testPlaylist(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Adele - Hello.mp3"), "x");
        Files.writeString(dir.resolve("plain.wav"), "x");
        Files.writeString(dir.resolve("notes.txt"), "x");
        Files.writeString(dir.resolve("Band - Song - Live.ogg"), "x");

        PlaylistManager pm = new PlaylistManager();
        check("scan hanya format didukung (3 file)", pm.scan(dir) == 3);
        check("parse artis dan judul", pm.tracks().get(0).artist().equals("Adele")
                && pm.tracks().get(0).title().equals("Hello"));
        check("judul boleh mengandung ' - '", pm.tracks().get(1).artist().equals("Band")
                && pm.tracks().get(1).title().equals("Song - Live"));
        check("file tanpa separator = Unknown Artist", pm.tracks().get(2).artist().equals("Unknown Artist")
                && pm.tracks().get(2).title().equals("plain"));
        check("indeks awal 0", pm.currentIndex() == 0);
        check("next 0->1", pm.next() != null && pm.currentIndex() == 1);
        check("next 1->2", pm.next() != null && pm.currentIndex() == 2);
        check("next wrap 2->0", pm.next() != null && pm.currentIndex() == 0);
        check("previous wrap 0->2", pm.previous() != null && pm.currentIndex() == 2);
        check("folder tidak ada = 0 lagu", new PlaylistManager().scan(dir.resolve("nope")) == 0);
    }

    static void testConfig(Path dir) throws Exception {
        Files.createDirectories(dir);
        XFiveConfig a = new XFiveConfig(dir);
        a.hudScale = 1.5f;
        a.lyricsFontSize = 14;
        a.theme = "cyan";
        a.musicFolder = "/music";
        a.hudPositions.get("chatHud")[1] = 77;
        a.save();

        XFiveConfig b = new XFiveConfig(dir);
        b.load();
        check("config hudScale tersimpan", b.hudScale == 1.5f);
        check("config fontSize tersimpan", b.lyricsFontSize == 14);
        check("config theme tersimpan", b.theme.equals("cyan"));
        check("config folder tersimpan", b.musicFolder.equals("/music"));
        check("config posisi HUD tersimpan", b.hudPositions.get("chatHud")[1] == 77);

        Files.writeString(dir.resolve(XFiveConfig.FILE_NAME), "general.hudScale=99\nlyrics.fontSize=-3\n");
        XFiveConfig c = new XFiveConfig(dir);
        c.load();
        check("config nilai di atas batas dikunci ke 2.0", c.hudScale == 2.0f);
        check("config nilai di bawah batas dikunci ke 6", c.lyricsFontSize == 6);

        Files.writeString(dir.resolve(XFiveConfig.FILE_NAME), "lyrics.fontSize=abc\n");
        XFiveConfig e = new XFiveConfig(dir);
        e.load();
        check("config nilai rusak memakai default", e.lyricsFontSize == 10);
    }
}
