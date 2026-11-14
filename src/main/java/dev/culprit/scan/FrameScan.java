package dev.culprit.scan;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

/**
 * A frame scan (DESIGN §4.2, §5.3): for some seconds of this client's frames, from the start of one frame to the end
 * of the last, every frame hook times its call on the render thread, thing by thing and part by part. One at a time;
 * it needs nothing from the server, so it works on any server. Its report is made as a tick scan's is, with the
 * stutters (its slowest frames, over twice the usual one and at least 20 ms) beside the averages. Render thread only.
 */
public final class FrameScan {
    /** A frame is a stutter when it takes over twice the scan's usual frame, and at least this long. */
    public static final long STUTTER_FLOOR_NS = 20_000_000;
    /** Read by the frame loop's hook: whether a scan waits for its first frame or runs. */
    public static boolean armed;
    private static @Nullable FrameScan pending;
    private static @Nullable FrameScan running;

    private final int seconds;
    private final Consumer<ScanReport> onDone;
    private @Nullable CostTable table;
    private int frames;
    private long startNs, frameStartNs, frameNsTotal, frameNsMax, startedAtMillis;
    private boolean stopAsked;

    private FrameScan(int seconds, Consumer<ScanReport> onDone) {
        this.seconds = seconds;
        this.onDone = onDone;
    }

    /** Starts a frame scan of {@code seconds} with the next frame; false if one is already waiting or running. */
    public static boolean start(int seconds, Consumer<ScanReport> onDone) {
        if (pending != null || running != null) {
            return false;
        }
        pending = new FrameScan(seconds, onDone);
        armed = true;
        return true;
    }

    /** Seconds left in the frame scan, or -1 if none runs. */
    public static int secondsLeft() {
        FrameScan scan = running != null ? running : pending;
        if (scan == null) {
            return -1;
        }
        if (scan == pending) {
            return scan.seconds;
        }
        long left = scan.seconds * 1_000_000_000L - (System.nanoTime() - scan.startNs);
        return (int) Math.max(0, (left + 999_999_999) / 1_000_000_000L);
    }

    /** How long the running (or waiting) frame scan was asked to last, in seconds; 0 when none. */
    public static int seconds() {
        FrameScan scan = running != null ? running : pending;
        return scan == null ? 0 : scan.seconds;
    }

    /** Ends the frame scan with the frame running now; its report is made from the frames it has. */
    public static void stop() {
        if (pending != null) {
            pending = null;
            armed = running != null;
        }
        if (running != null) {
            running.stopAsked = true;
        }
    }

    /** A frame begins (the client's loop goes round): a waiting scan starts with it. */
    public static void frameStarts() {
        if (pending != null && running == null) {
            running = pending;
            pending = null;
            running.begin();
        }
        FrameScan scan = running;
        if (scan != null) {
            FrameProbe.frameStarts();
            scan.frameStartNs = System.nanoTime();
        }
    }

    /** A frame ends: its time is folded in, and the scan ends after its last. */
    public static void frameEnds() {
        FrameScan scan = running;
        if (scan == null || scan.table == null) {
            return;
        }
        long now = System.nanoTime();
        long frameNs = now - scan.frameStartNs;
        scan.frameNsTotal += frameNs;
        scan.frameNsMax = Math.max(scan.frameNsMax, frameNs);
        scan.table.endTick(frameNs);
        scan.frames++;
        if (scan.stopAsked || now - scan.startNs >= scan.seconds * 1_000_000_000L) {
            scan.finish();
        }
    }

    private void begin() {
        table = new CostTable(Math.clamp(seconds * 3, 5, 60), FramePhase.COUNT);   // the slowest frames kept whole, for the stutters
        FrameProbe.table = table;
        FrameProbe.thread = Thread.currentThread();
        FrameProbe.overheadNs = TickScan.timerCost();
        FrameProbe.perCallNs = FrameProbe.calibrate();
        startedAtMillis = System.currentTimeMillis();
        startNs = System.nanoTime();
        FrameProbe.active = true;
    }

    private void finish() {
        FrameProbe.active = false;
        FrameProbe.table = null;
        running = null;
        armed = pending != null;
        CostTable t = table;
        table = null;
        if (t != null && frames > 0) {
            onDone.accept(ScanReport.of(t, frames, frameNsTotal, frameNsMax, startedAtMillis, FrameProbe.perCallNs, STUTTER_FLOOR_NS));
        }
    }
}
