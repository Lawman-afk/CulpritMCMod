package dev.culprit.client;

import org.jspecify.annotations.Nullable;

import dev.culprit.command.ReportText;
import dev.culprit.net.CulpritNetwork;
import dev.culprit.net.ChunkData;
import dev.culprit.net.ReportData;
import dev.culprit.scan.FrameScan;
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
    /** Whether the results show the lag spikes (or stutters) rather than the average of every tick (or frame). */
    private static boolean spikesShown;
    /** The last frame scan's report (made on this client), when it came, and whether the results show it. */
    private static @Nullable ReportData frames;
    private static long framesReceivedMillis;
    private static boolean framesShown;
    /** The last look at the loaded chunks (the chunk inspector), when it came, and whether the results show it. */
    private static @Nullable ChunkData chunks;
    private static long chunksReceivedMillis;
    private static boolean chunksShown;

    private ClientReport() {}

    public static @Nullable ReportData data() {
        return data;
    }

    /** The scan shown: the server's ticks, or this game's frames (none while the loaded chunks are shown). */
    public static @Nullable ReportData base() {
        return chunksShown ? null : framesShown ? frames : data;
    }

    public static @Nullable ChunkData chunksData() {
        return chunks;
    }

    public static long chunksReceivedMillis() {
        return chunksReceivedMillis;
    }

    public static boolean chunksShown() {
        return chunksShown;
    }

    /** Shows a section of the results: 0 the server's ticks, 1 this game's frames, 2 the loaded chunks. */
    public static void showSection(int section) {
        framesShown = section == 1;
        chunksShown = section == 2;
        Overlay.newReport(view());
        Overlay.markLoader(null);
    }

    /** Asks the server for a look at its loaded chunks (operators, as scans). */
    public static void requestChunks() {
        if (ScanScreen.serverHasCulprit()) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new CulpritNetwork.ChunksRequest());
        }
    }

    static void receiveChunks(ChunkData report) {
        chunks = report;
        chunksReceivedMillis = System.currentTimeMillis();
        chunksShown = true;
        framesShown = false;
        Overlay.newReport(view());
        Overlay.markLoader(null);
    }

    /** The report shown: the shown scan's average, or its lag spikes or stutters (null when there were none). */
    public static @Nullable ReportData view() {
        ReportData base = base();
        return base == null ? null : spikesShown ? base.spikes : base;
    }

    /** The last frame scan's report, whichever scan is shown. */
    public static @Nullable ReportData framesData() {
        return frames;
    }

    public static boolean framesShown() {
        return framesShown;
    }

    /** Shows the frames' results, or the ticks' again, in the results and on the world. */
    public static void showFrames(boolean on) {
        framesShown = on;
        chunksShown = false;
        Overlay.newReport(view());
    }

    /** When the shown scan's report came. */
    public static long baseReceivedMillis() {
        return framesShown ? framesReceivedMillis : receivedMillis;
    }

    /** Starts a frame scan of this game (it needs nothing from the server); the chat says when it starts and ends. */
    public static void startFrameScan(int seconds) {
        boolean started = FrameScan.start(seconds, report -> {
            ReportData d = ReportData.of(report);
            d.frames = true;
            if (d.spikes != null) {
                d.spikes.frames = true;
            }
            receiveFrames(d);
        });
        say(tag().append((started ? Component.translatableWithFallback("culprit.chat.frames_started", "Scanning frames for %s seconds.", seconds)
                : Component.translatableWithFallback("culprit.chat.frames_busy", "A frame scan is already running.")).withStyle(ChatFormatting.GRAY)));
    }

    private static void receiveFrames(ReportData report) {
        if (Minecraft.getInstance().level == null) {
            return;                                             // the world was left while it ran
        }
        frames = report;
        framesReceivedMillis = System.currentTimeMillis();
        Names.clear();
        framesShown = true;                                     // the results open on what was just scanned
        chunksShown = false;
        Overlay.newReport(view());
        say(finished(report));
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
        framesShown = false;                                    // the results open on what was just scanned
        chunksShown = false;
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
        long mean = d.perTick(d.tickNsTotal);
        Component meanText = Component.literal(ReportText.time(mean)).withColor(Heat.rgb(Heat.of(d.frames ? mean / 4 : mean)));
        MutableComponent line = tag().append((d.frames
                ? Component.translatableWithFallback("culprit.chat.frames_done", "Done. A frame took %s on average (%s FPS) and %s at worst.",
                        meanText, mean <= 0 ? "-" : String.valueOf(Math.round(1e9 / mean)), ReportText.time(d.tickNsMax))
                : Component.translatableWithFallback("culprit.chat.done", "Done. The server tick took %s on average and %s at worst (it has 50 ms).",
                        meanText, ReportText.time(d.tickNsMax))).withStyle(ChatFormatting.WHITE));
        line.append(" ").append((d.frames
                ? d.spikes == null ? Component.translatableWithFallback("culprit.chat.no_stutters", "No stutters.")
                        : Component.translatableWithFallback("culprit.chat.stutters", "Stutters: %s.", d.spikes.ticks)
                : d.spikes == null ? Component.translatableWithFallback("culprit.chat.no_spikes", "No lag spikes.")
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
        frames = null;
        framesShown = false;
        chunks = null;
        chunksShown = false;
        FrameScan.stop();
        state = -1;
        spikesShown = false;
        Overlay.newReport(null);
        Overlay.setShown(false);                               // hidden again in the next world, until asked for
    }
}
