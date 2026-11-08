package com.xfive.live.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Antrean chat: pesan masuk dikirim ke antrean, dilepas ke layar dengan jeda tertentu,
 * dibatasi jumlahnya, dan dihapus setelah durasi tampil habis.
 * Mode simulasi/manual memakai submit() yang sama.
 */
public final class ChatManager {
    private static final int MAX_PENDING = 100;
    private static final int MAX_TEXT_LENGTH = 200;

    private final Deque<ChatMessage> pending = new ArrayDeque<>();
    private final Deque<ChatMessage> visible = new ArrayDeque<>();
    private final CopyOnWriteArraySet<String> blockedWords = new CopyOnWriteArraySet<>();

    private int maxMessages = 50;
    private long durationMs = 15_000L;
    private long intervalMs = 400L;
    private long lastReleaseMs = Long.MIN_VALUE / 4;

    /** Menambahkan pesan. Mengembalikan false jika kosong atau kena filter. */
    public synchronized boolean submit(String username, String text, long nowMs) {
        String body = text == null ? "" : text.strip();
        if (body.isEmpty() || isBlocked(body)) {
            return false;
        }
        if (body.length() > MAX_TEXT_LENGTH) {
            body = body.substring(0, MAX_TEXT_LENGTH);
        }
        String user = username == null || username.isBlank() ? "viewer" : username.strip();
        if (pending.size() >= MAX_PENDING) {
            pending.pollFirst();
        }
        pending.addLast(new ChatMessage(user, body, nowMs));
        return true;
    }

    /** Dipanggil sekali per tick: melepas satu pesan jika jeda terpenuhi, lalu membuang yang kedaluwarsa. */
    public synchronized void update(long nowMs) {
        if (!pending.isEmpty() && nowMs - lastReleaseMs >= intervalMs) {
            ChatMessage m = pending.pollFirst();
            visible.addLast(new ChatMessage(m.username(), m.text(), nowMs));
            lastReleaseMs = nowMs;
        }
        while (!visible.isEmpty()
                && (nowMs - visible.peekFirst().timestampMs() > durationMs || visible.size() > maxMessages)) {
            visible.pollFirst();
        }
    }

    /** Menyalin pesan tampil (lama ke baru) ke list milik pemanggil, tanpa alokasi baru per frame. */
    public synchronized int copyVisibleTo(List<ChatMessage> out) {
        out.clear();
        out.addAll(visible);
        return out.size();
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    public synchronized void clear() {
        pending.clear();
        visible.clear();
    }

    public synchronized void setMaxMessages(int value) {
        maxMessages = Math.max(1, Math.min(200, value));
    }

    public synchronized void setDurationMs(long value) {
        durationMs = Math.max(1_000L, Math.min(600_000L, value));
    }

    public synchronized void setIntervalMs(long value) {
        intervalMs = Math.max(0L, Math.min(5_000L, value));
    }

    public void addBlockedWord(String word) {
        if (word != null && !word.isBlank()) {
            blockedWords.add(word.strip().toLowerCase(Locale.ROOT));
        }
    }

    public void removeBlockedWord(String word) {
        if (word != null) {
            blockedWords.remove(word.strip().toLowerCase(Locale.ROOT));
        }
    }

    public boolean isBlocked(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : blockedWords) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
