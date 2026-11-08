package com.xfive.live.hud;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.chat.ChatManager;
import com.xfive.live.chat.ChatMessage;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Panel chat penonton. Daftar sementara dipakai ulang agar tidak mengalokasikan objek tiap frame. */
public final class LiveChatHud {
    private static final int WIDTH = 180;
    private static final int LINE_H = 11;
    private static final List<ChatMessage> SCRATCH = new ArrayList<>(64);

    private LiveChatHud() {
    }

    public static void render(GuiGraphicsExtractor g) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        if (!c.enableChat) {
            return;
        }
        ChatManager chat = XFiveLiveClient.CHAT;
        int n = chat.copyVisibleTo(SCRATCH);
        if (n == 0) {
            return;
        }
        int[] pos = c.hudPositions.get("chatHud");
        int x = pos[0];
        int y = pos[1];
        int height = n * LINE_H + 8;
        int accent = GlassPanelRenderer.accent(c.theme);
        GlassPanelRenderer.panel(g, x, y, WIDTH, height, c.chatOpacity * c.hudOpacity, accent);

        Font font = GlassPanelRenderer.font();
        for (int i = 0; i < n; i++) {
            ChatMessage m = SCRATCH.get(i);
            String line = GlassPanelRenderer.trim(m.username() + ": " + m.text(), 34);
            GlassPanelRenderer.text(g, font, line, x + 5, y + 4 + i * LINE_H, GlassPanelRenderer.WHITE);
        }
    }
}
