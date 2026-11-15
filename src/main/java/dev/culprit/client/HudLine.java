package dev.culprit.client;

import java.util.Locale;

import dev.culprit.config.CulpritClientConfig;
import dev.culprit.net.CulpritNetwork;
import dev.culprit.legacy.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import dev.culprit.port.GuiGraphicsExtractor;
import net.minecraft.client.server.IntegratedServer;
import dev.culprit.legacy.net.PacketDistributor;

/**
 * The HUD line (DESIGN §4.5): the server's ticks a second and time a tick, and the client's frames a second and time a
 * frame, in a corner of the screen while the player has it on. A server of one's own (single player, or opened to LAN)
 * is read directly; another server sends its numbers once a second to a player who may scan there and has asked. The
 * words are made four times a second, not every frame.
 */
public final class HudLine {
    private static final int GOOD = 0xFF55FF55, FAIR = 0xFFFFFF55, POOR = 0xFFFF5555, NAME = 0xFFE0E0E0, BACK = 0x90202020;
    private static final int MOST = 8;
    /** The server's last numbers, and when they came (a server stops sending when the player may no longer scan). */
    private static long serverTickNs = -1;
    private static float serverTps;
    private static long serverAtMillis;
    private static boolean asked;
    /** The line as last made: its pieces, their colours and places, its width. */
    private static final String[] PIECES = new String[MOST];
    private static final int[] COLOURS = new int[MOST], OFFSETS = new int[MOST];
    private static int pieces, width;
    private static long madeAtMillis;

    private HudLine() {}

    /** The server's numbers (its payload, once a second). */
    public static void received(long tickNs, float tps) {
        serverTickNs = tickNs;
        serverTps = tps;
        serverAtMillis = System.currentTimeMillis();
    }

    /** Asks the server for its numbers, or tells it to stop: as the line is turned on or off, and on joining a server. */
    public static void sync() {
        Minecraft mc = Minecraft.getInstance();
        var connection = mc.getConnection();
        boolean want = CulpritClientConfig.hudLine() && mc.getSingleplayerServer() == null;
        if (connection == null) {
            asked = false;
            return;
        }
        if (want != asked && dev.culprit.legacy.client.ClientNet.serverHas(connection)) {
            PacketDistributor.sendToServer(new CulpritNetwork.LoadRequest(want));
            asked = want;
        }
    }

    /** Forgets the last server's numbers (on leaving it). */
    public static void left() {
        asked = false;
        serverTickNs = -1;
    }

    public static void toggle() {
        CulpritClientConfig.setHudLine(!CulpritClientConfig.hudLine());
        madeAtMillis = 0;
        sync();
    }

    public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!CulpritClientConfig.hudLine() || mc.level == null || mc.options.hideGui || mc.options.renderDebug) {
            return;
        }
        Font font = mc.font;
        long now = System.currentTimeMillis();
        if (now - madeAtMillis >= 250) {
            madeAtMillis = now;
            make(mc, font, now);
        }
        boolean right = CulpritClientConfig.hudCorner() == CulpritClientConfig.Corner.TOP_RIGHT;
        int x = right ? g.guiWidth() - width - 3 : 3, y = 3;
        g.fill(x - 2, y - 2, x + width + 2, y + font.lineHeight, BACK);
        for (int i = 0; i < pieces; i++) {
            g.text(font, PIECES[i], x + OFFSETS[i], y, COLOURS[i]);
        }
    }

    private static void make(Minecraft mc, Font font, long now) {
        long tickNs = -1;
        float tps = 0;
        IntegratedServer own = mc.getSingleplayerServer();
        if (own != null) {
            tickNs = (long) (own.getAverageTickTime() * 1_000_000L);
            tps = (float) Math.min(20f, tickNs > 0 ? 1e9 / tickNs : 20);
        } else if (serverTickNs >= 0 && now - serverAtMillis < 3000) {
            tickNs = serverTickNs;
            tps = serverTps;
        }
        pieces = 0;
        width = 0;
        if (tickNs >= 0) {
            float ms = tickNs / 1e6f;
            piece(font, "TPS ", NAME);
            piece(font, String.format(Locale.ROOT, "%.1f", tps), tps >= 19.5f ? GOOD : tps >= 15 ? FAIR : POOR);
            piece(font, "   MSPT ", NAME);
            piece(font, String.format(Locale.ROOT, "%.1f", ms), ms < 30 ? GOOD : ms < 50 ? FAIR : POOR);
            piece(font, "   ", NAME);
        }
        int fps = mc.getFps();
        piece(font, "FPS ", NAME);
        piece(font, String.valueOf(fps), fps >= 60 ? GOOD : fps >= 30 ? FAIR : POOR);
        if (fps > 0) {
            piece(font, String.format(Locale.ROOT, " (%.1f ms)", 1000f / fps), NAME);
        }
    }

    private static void piece(Font font, String s, int colour) {
        PIECES[pieces] = s;
        COLOURS[pieces] = colour;
        OFFSETS[pieces] = width;
        width += font.width(s);
        pieces++;
    }
}
