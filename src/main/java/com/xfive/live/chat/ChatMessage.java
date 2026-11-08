package com.xfive.live.chat;

/** Satu pesan chat penonton. timestampMs adalah waktu pesan dirilis ke layar. */
public record ChatMessage(String username, String text, long timestampMs) {
}
