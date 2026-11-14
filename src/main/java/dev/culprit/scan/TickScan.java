package dev.culprit.scan;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * A tick scan: from the start of one server tick to the end of the last, every hooked call is timed (DESIGN §4.1,
 * §5.2). One scan at a time; the last finished scan's report is kept for {@code /culprit top} and the screens.
 */
public final class TickScan {
    private static @Nullable TickScan pending;
    private static @Nullable TickScan running;
    private static @Nullable ScanReport last;
    /** Between a tick's start and its end (where a stop waits for the end). */
    private static boolean inTick;

    private int ticksWanted;
    private final @Nullable Consumer<ScanReport> onDone;
    private CostTable table;
    private int ticksDone;
    private long tickStartNs;
    private long tickNsTotal;
    private long tickNsMax;
    private long startedAtMillis;

    private TickScan(int ticks, @Nullable Consumer<ScanReport> onDone) {
        this.ticksWanted = ticks;
        this.onDone = onDone;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TickScan::onTickPre);
        NeoForge.EVENT_BUS.addListener(TickScan::onTickPost);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> stopNow(false));
    }

    /** Starts a scan of {@code ticks} ticks from the next tick; false if one is already running or about to. */
    public static boolean start(int ticks, @Nullable Consumer<ScanReport> onDone) {
        if (pending != null || running != null) {
            return false;
        }
        pending = new TickScan(ticks, onDone);
        return true;
    }

    /** Ticks left in the running scan, or -1 if none runs. */
    public static int ticksLeft() {
        TickScan scan = running != null ? running : pending;
        return scan == null ? -1 : scan.ticksWanted - scan.ticksDone;
    }

    /**
     * Ends the running scan; its report is made from the ticks it has (false if none was running). Asked inside a tick,
     * with a report, it ends as that tick does, so the tick and what was measured in it count together.
     */
    public static boolean stopNow(boolean report) {
        if (pending != null) {
            pending = null;
            return true;
        }
        TickScan scan = running;
        if (scan == null) {
            return false;
        }
        if (report && inTick) {
            scan.ticksWanted = Math.min(scan.ticksWanted, scan.ticksDone + 1);
            return true;
        }
        scan.finish(report && scan.ticksDone > 0);
        return true;
    }

    public static @Nullable ScanReport last() {
        return last;
    }

    private static void onTickPre(ServerTickEvent.Pre event) {
        inTick = true;
        if (pending != null && running == null) {
            running = pending;
            pending = null;
            running.begin();
        }
        if (running != null) {
            Probe.resetDepth();
            running.tickStartNs = System.nanoTime();
        }
    }

    private static void onTickPost(ServerTickEvent.Post event) {
        inTick = false;
        TickScan scan = running;
        if (scan == null) {
            return;
        }
        long tickNs = System.nanoTime() - scan.tickStartNs;
        scan.tickNsTotal += tickNs;
        scan.tickNsMax = Math.max(scan.tickNsMax, tickNs);
        scan.table.endTick(tickNs);
        if (++scan.ticksDone >= scan.ticksWanted) {
            scan.finish(true);
        }
    }

    private void begin() {
        table = new CostTable(Math.clamp(ticksWanted / 20, 5, 60));   // the slowest 5 % of ticks kept whole, for the spikes
        Probe.table = table;
        Probe.thread = Thread.currentThread();
        Probe.overheadNs = timerCost();
        Probe.perCallNs = Probe.calibrate();
        startedAtMillis = System.currentTimeMillis();
        Probe.active = true;
    }

    private void finish(boolean report) {
        Probe.active = false;
        Probe.table = null;
        running = null;
        if (report) {
            ScanReport r = ScanReport.of(table, ticksDone, tickNsTotal, tickNsMax, startedAtMillis, Probe.perCallNs);
            last = r;
            if (onDone != null) {
                onDone.accept(r);
            }
        }
        table = null;
    }

    /** One {@code System.nanoTime()} call's cost: the least of many back-to-back pairs, so a timing's own cost comes off it. */
    private static long timerCost() {
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 20; round++) {
            long a = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                System.nanoTime();
            }
            long b = System.nanoTime();
            best = Math.min(best, (b - a) / 1001);
        }
        return Math.max(0, best);
    }
}
