package dev.culprit.client;

import org.jspecify.annotations.Nullable;

import dev.culprit.command.ReportText;
import dev.culprit.net.CulpritNetwork;
import dev.culprit.net.ReportData;
import dev.culprit.scan.Kind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * What this client knows of the server's scans: the last report it was sent, how the scan it asked for stands, and
 * whether it may scan. It tells the player in chat when a scan starts and when it ends, with the quick results.
 */
public final class ClientReport {
    private static @Nullable ReportData data;
    private static long receivedMillis;
    private static byte state = -1;
    private static int stateTicks;
    private static long stateMillis;
    private static int scanSeconds;
    /** Whether the results show the lag spikes rather than the average of every tick. */
    private static boolean spikesShown;

    private ClientReport() {}

    public static @Nullable ReportData data() {
        return data;
    }

    /** The report shown: the average of every tick, or the lag spikes (null when there were none). */
    public static @Nullable ReportData view() {
        return data == null ? null : spikesShown ? data.spikes : data;
    }

    public static boolean spikesShown() {
        return spikesShown;
    }

    /** Shows the lag spikes, or the average again, in the results and on the world. */
    public static void showSpikes(boolean on) {
        spikesShown = on;
        Overlay.newReport(view());
    }

    public static long receivedMillis() {
        return receivedMillis;
    }

    /** The last status the server sent (a {@link CulpritNetwork.Status} state), or -1. */
    public static byte state() {
        return state;
    }

    /** How long the running scan was asked to last, in seconds. */
    public static int scanSeconds() {
        return scanSeconds;
    }

    static void receive(ReportData report) {
        data = report;
        receivedMillis = System.currentTimeMillis();
        Names.clear();
        if (state == CulpritNetwork.Status.STARTED || state == CulpritNetwork.Status.BUSY) {
            state = -1;
        }
        Overlay.newReport(view());
        say(finished(report));
    }

    static void status(byte s, int ticks) {
        if (s == CulpritNetwork.Status.STARTED || s == CulpritNetwork.Status.BUSY || s == CulpritNetwork.Status.RUNNING) {
            stateTicks = ticks;
            stateMillis = System.currentTimeMillis();
        }
        if (s == CulpritNetwork.Status.RUNNING) {
            scanSeconds = Math.max(scanSeconds, (ticks + 19) / 20);
            state = CulpritNetwork.Status.BUSY;        // a scan runs (someone's); no chat for an answer to a question
            return;
        }
        switch (s) {
            case CulpritNetwork.Status.STARTED -> {
                scanSeconds = (ticks + 19) / 20;
                say(tag().append(Component.translatableWithFallback("culprit.chat.started", "Scanning the server for %s seconds.", scanSeconds)
                        .withStyle(ChatFormatting.GRAY)));
            }
            case CulpritNetwork.Status.BUSY -> {
                scanSeconds = Math.max(scanSeconds, (ticks + 19) / 20);
                say(tag().append(Component.translatableWithFallback("culprit.chat.busy", "A scan is already running (%s s left).",
                        (ticks + 19) / 20).withStyle(ChatFormatting.GRAY)));
            }
            case CulpritNetwork.Status.DENIED -> {
                if (Minecraft.getInstance().gui.screen() == null) {
                    say(tag().append(Component.translatableWithFallback("culprit.chat.denied", "Only operators can scan this server.")
                            .withStyle(ChatFormatting.RED)));
                }
            }
            case CulpritNetwork.Status.STOPPED -> say(tag().append(Component.translatableWithFallback("culprit.chat.stopped",
                    "Scan stopped early.").withStyle(ChatFormatting.GRAY)));
            default -> {}
        }
        // The answer to "may I scan?" is kept as it is, without overwriting a scan's running state.
        if (s != CulpritNetwork.Status.ALLOWED || state == -1 || state == CulpritNetwork.Status.DENIED) {
            state = s;
        }
    }

    /** "[Culprit] Done. The server tick took ... Costliest: ..." */
    private static Component finished(ReportData d) {
        MutableComponent line = tag().append(Component.translatableWithFallback("culprit.chat.done",
                "Done. The server tick took %s on average and %s at worst (it has 50 ms).",
                Component.literal(ReportText.time(d.perTick(d.tickNsTotal))).withColor(Heat.rgb(Heat.of(d.perTick(d.tickNsTotal)))),
                ReportText.time(d.tickNsMax)).withStyle(ChatFormatting.WHITE));
        line.append(" ").append((d.spikes == null ? Component.translatableWithFallback("culprit.chat.no_spikes", "No lag spikes.")
                : Component.translatableWithFallback("culprit.chat.spikes", "Lag spikes: %s.", d.spikes.ticks)).withStyle(ChatFormatting.WHITE));
        if (d.types > 0) {
            MutableComponent top = Component.empty();
            for (int i = 0; i < Math.min(3, d.types); i++) {
                if (i > 0) {
                    top.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
                long perTick = d.perTick(d.typeTotal[i]);
                top.append(Component.literal(typeName(d, i) + " ")).append(Component.literal(ReportText.time(perTick)).withColor(Heat.rgb(Heat.of(perTick))));
            }
            line.append(" ").append(Component.translatableWithFallback("culprit.chat.costliest", "Costliest: %s.", top).withStyle(ChatFormatting.WHITE));
        }
        return line;
    }

    /** "Villager x24", "Hopper x70", "Piston (block events) x8", "Grass Block random ticks (40 chunks)". */
    private static String typeName(ReportData d, int i) {
        int kind = d.typeKind[i];
        String name = Names.of(d.names[d.typeName[i]], kind);
        if (Kind.isChunkWide(kind)) {
            String what = kind == Kind.RANDOM_TICK
                    ? name + " " + Component.translatableWithFallback("culprit.kind.random_tick", "random ticks").getString() : name;
            return Component.translatableWithFallback("culprit.chat.in_chunks", "%s (%s chunks)", what, d.typeCount[i]).getString();
        }
        String what = kind == Kind.BLOCK_ENTITY || kind == Kind.ENTITY ? name
                : name + " (" + Component.translatableWithFallback("culprit.kind.one." + Kind.key(kind), Kind.englishOne(kind)).getString() + ")";
        return d.typeCount[i] > 1 ? what + " x" + d.typeCount[i] : what;
    }

    /** "[Culprit] " in gold, as the first part of a line whose other parts keep their own colours. */
    private static MutableComponent tag() {
        return Component.empty().append(Component.literal("[Culprit] ").withStyle(ChatFormatting.GOLD));
    }

    private static void say(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(message);
        }
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
        spikesShown = false;
        Overlay.newReport(null);
        Overlay.setShown(false);                               // hidden again in the next world, until asked for
    }
}
