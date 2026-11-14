package dev.culprit.client;

import org.jspecify.annotations.Nullable;

import dev.culprit.net.CulpritNetwork;
import dev.culprit.net.ReportData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** What this client knows of the server's scans: the last report it was sent, and how the scan it asked for stands. */
public final class ClientReport {
    private static @Nullable ReportData data;
    private static long receivedMillis;
    private static byte state = -1;
    private static int stateTicks;
    private static long stateMillis;

    private ClientReport() {}

    public static @Nullable ReportData data() {
        return data;
    }

    public static long receivedMillis() {
        return receivedMillis;
    }

    /** The last status the server sent (a {@link CulpritNetwork.Status} state), or -1. */
    public static byte state() {
        return state;
    }

    static void receive(ReportData report) {
        data = report;
        receivedMillis = System.currentTimeMillis();
        state = -1;
        Overlay.newReport(report);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !(mc.gui.screen() instanceof CulpritScreen)) {
            mc.player.sendSystemMessage(Component.translatableWithFallback("culprit.client.ready",
                    "Culprit: the scan's results are shown on the world. Press %s for the list.", CulpritKeys.OPEN.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    static void status(byte s, int ticks) {
        state = s;
        stateTicks = ticks;
        stateMillis = System.currentTimeMillis();
    }

    /** Seconds left in the scan this client started, or -1 if none is known to run. */
    public static int secondsLeft() {
        if (state != CulpritNetwork.Status.STARTED && state != CulpritNetwork.Status.BUSY) {
            return -1;
        }
        long left = stateTicks * 50L - (System.currentTimeMillis() - stateMillis);
        return (int) Math.max(0, (left + 999) / 1000);
    }

    /** Forgets everything (on leaving a world). */
    static void clear() {
        data = null;
        state = -1;
        Overlay.newReport(null);
    }
}
