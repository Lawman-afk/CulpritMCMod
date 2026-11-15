package dev.culprit.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * What a finished tick scan found: every measured thing with its total time, its worst tick and its calls, and the
 * same folded by type, by mod and by chunk, each list costliest first; with the whole tick's mean and worst time.
 * Times are nanoseconds summed over the scan: per tick is {@link #perTick}.
 */
public final class ScanReport {
    /**
     * One measured thing; {@code pos} is a packed block position (a chunk's centre for chunk ticks); {@code entityId}
     * the entity's network id while it is in the world (so a client can follow it), else -1.
     */
    public record Thing(byte kind, ResourceKey<Level> level, long pos, String type, long totalNs, long maxNs, long calls, int entityId) {
        public String mod() {
            return modOf(type);
        }

        public BlockPos blockPos() {
            return BlockPos.of(pos);
        }
    }

    /** Things of one type (and kind) or one mod: their total, how many, and the costliest. */
    public record Group(String name, int kind, long totalNs, int things, Thing worst) {}

    /** One chunk: its total, how many things in it, and the type (of a kind) costing most there. */
    public record ChunkGroup(ResourceKey<Level> level, int x, int z, long totalNs, int things, String topType, int topKind, long topTypeNs) {
        public BlockPos centre() {
            return new BlockPos((x << 4) + 8, 64, (z << 4) + 8);
        }
    }

    public final int ticks;
    public final long tickNsTotal;
    public final long tickNsMax;
    public final long startedAtMillis;
    /** Whether the scan stopped adding things (more than {@link CostTable#MAX_SLOTS}). */
    public final boolean incomplete;
    /** Total measured time per {@link Kind}. */
    public final long[] kindNs = new long[Kind.COUNT];
    /** Total own time per {@link Phase} of the tick. */
    public final long[] phaseNs;
    /** Culprit's own timing, estimated: every measured call times what measuring one costs. */
    public final long selfNs;
    public final List<Thing> things;
    public final List<Group> types;
    public final List<Group> mods;
    public final List<ChunkGroup> chunks;
    /** The scan's usual tick (the median of its ticks), and the time over which a tick is a lag spike. */
    public final long medianTickNs, spikeThresholdNs;
    /** The lag spikes: the same lists over the spike ticks only; null when no tick was one (and in a spikes report). */
    public final @Nullable ScanReport spikes;

    /** A tick is a lag spike when it takes over twice the scan's usual tick, and at least this long. */
    public static final long SPIKE_FLOOR_NS = 5_000_000;

    private ScanReport(int ticks, long tickNsTotal, long tickNsMax, long startedAtMillis, boolean incomplete, long[] phaseNs, long selfNs,
                       List<Thing> things, List<Group> types, List<Group> mods, List<ChunkGroup> chunks, long medianTickNs, long spikeThresholdNs,
                       @Nullable ScanReport spikes) {
        this.medianTickNs = medianTickNs;
        this.spikeThresholdNs = spikeThresholdNs;
        this.spikes = spikes;
        this.phaseNs = phaseNs.clone();
        this.selfNs = selfNs;
        this.ticks = ticks;
        this.tickNsTotal = tickNsTotal;
        this.tickNsMax = tickNsMax;
        this.startedAtMillis = startedAtMillis;
        this.incomplete = incomplete;
        this.things = things;
        this.types = types;
        this.mods = mods;
        this.chunks = chunks;
        for (Thing t : things) {
            kindNs[t.kind()] += t.totalNs();
        }
    }

    /** A per-tick figure from a summed one. */
    public long perTick(long totalNs) {
        return ticks == 0 ? 0 : totalNs / ticks;
    }

    /** What the things took: every measured thing's own time. */
    public long measuredNs() {
        long sum = 0;
        for (long ns : kindNs) {
            sum += ns;
        }
        return sum;
    }

    /** What the tick's phases took, their own time. */
    public long phasesNs() {
        long sum = 0;
        for (long ns : phaseNs) {
            sum += ns;
        }
        return sum;
    }

    /** The part of the ticks nothing measured covers: tick events run outside the measured phases, and the like. */
    public long unaccountedNs() {
        return Math.max(0, tickNsTotal - measuredNs() - phasesNs() - selfNs);
    }

    public static String modOf(String type) {
        int colon = type.indexOf(':');
        return colon < 0 ? "minecraft" : type.substring(0, colon);
    }

    /**
     * A scan's report from its table: every tick's average, and beside it the lag spikes, the kept slowest ticks that
     * took over twice the usual tick (and at least {@link #SPIKE_FLOOR_NS}), with what each thing and part took in them.
     */
    static ScanReport of(CostTable t, int ticks, long tickNsTotal, long tickNsMax, long startedAtMillis, long perCallNs, long spikeFloorNs) {
        long[] times = t.tickTimes.toLongArray();
        java.util.Arrays.sort(times);
        long median = times.length == 0 ? 0 : times[times.length / 2];
        long threshold = Math.max(2 * median, spikeFloorNs);
        long[] total = new long[t.size], max = new long[t.size], calls = new long[t.size], phases = new long[t.phaseNs.length];
        int spikeTicks = 0;
        long spikeTotal = 0, spikeMax = 0, spikeCalls = 0;
        for (CostTable.TickRecord record : t.kept) {
            if (record.tickNs <= threshold) {
                continue;
            }
            spikeTicks++;
            spikeTotal += record.tickNs;
            spikeMax = Math.max(spikeMax, record.tickNs);
            spikeCalls += record.calls;
            for (int k = 0; k < record.size; k++) {
                int s = record.slots[k];
                total[s] += record.ns[k];
                max[s] = Math.max(max[s], record.ns[k]);
                calls[s]++;
            }
            for (int p = 0; p < phases.length; p++) {
                phases[p] += record.phases[p];
            }
        }
        ScanReport spikes = spikeTicks == 0 ? null : build(t, total, max, calls, true, spikeTicks, spikeTotal, spikeMax, startedAtMillis, phases,
                spikeCalls * perCallNs, median, threshold, null);
        return build(t, t.totalNs, t.maxNs, t.calls, false, ticks, tickNsTotal, tickNsMax, startedAtMillis, t.phaseNs, t.calls() * perCallNs,
                median, threshold, spikes);
    }

    /** A report over some of the scan's ticks: each thing's total, worst and calls in them, and the phases'. */
    private static ScanReport build(CostTable t, long[] totals, long[] maxes, long[] calls, boolean spikesOnly, int ticks, long tickNsTotal,
                                    long tickNsMax, long startedAtMillis, long[] phaseNs, long selfNs, long median, long threshold,
                                    @Nullable ScanReport spikes) {
        List<Thing> things = new ArrayList<>(t.size);
        for (int s = 0; s < t.size; s++) {
            if (spikesOnly && calls[s] == 0) {
                continue;                                   // not in any of the spike ticks
            }
            long pos = t.pos[s];
            int entityId = -1;
            if (t.ref[s] instanceof Entity entity && !entity.isRemoved()) {
                pos = entity.blockPosition().asLong();   // where it is now, not where it was first measured
                entityId = entity.getId();
            }
            things.add(new Thing(t.kind[s], t.levels.get(t.level[s]), pos, t.typeNames.get(t.type[s]), totals[s], maxes[s], calls[s], entityId));
        }
        Comparator<Thing> costliest = Comparator.comparingLong(Thing::totalNs).reversed();
        things.sort(costliest);

        Map<String, long[]> typeSums = new HashMap<>();
        Map<String, Thing> typeWorst = new HashMap<>();
        Map<String, long[]> modSums = new HashMap<>();
        Map<String, Thing> modWorst = new HashMap<>();
        Map<String, ChunkAcc> chunkSums = new HashMap<>();
        for (Thing th : things) {
            String typeKey = th.kind() + "|" + th.type();
            add(typeSums, typeWorst, typeKey, th);
            if (!Kind.isGames(th.kind())) {
                add(modSums, modWorst, th.mod(), th);       // random ticks go to their block's mod
            }
            BlockPos p = th.blockPos();
            int cx = p.getX() >> 4;
            int cz = p.getZ() >> 4;
            String chunkKey = th.level().location() + "|" + cx + "|" + cz;
            chunkSums.computeIfAbsent(chunkKey, k -> new ChunkAcc(th.level(), cx, cz)).add(th);
        }
        List<Group> types = new ArrayList<>();
        typeSums.forEach((key, sum) -> {
            Thing worst = typeWorst.get(key);
            types.add(new Group(worst.type(), worst.kind(), sum[0], (int) sum[1], worst));
        });
        List<Group> mods = new ArrayList<>();
        modSums.forEach((mod, sum) -> mods.add(new Group(mod, -1, sum[0], (int) sum[1], modWorst.get(mod))));
        List<ChunkGroup> chunks = new ArrayList<>();
        for (ChunkAcc acc : chunkSums.values()) {
            chunks.add(acc.group());
        }
        types.sort(Comparator.comparingLong(Group::totalNs).reversed());
        mods.sort(Comparator.comparingLong(Group::totalNs).reversed());
        chunks.sort(Comparator.comparingLong(ChunkGroup::totalNs).reversed());
        return new ScanReport(ticks, tickNsTotal, tickNsMax, startedAtMillis, t.full, phaseNs, selfNs, Collections.unmodifiableList(things),
                Collections.unmodifiableList(types), Collections.unmodifiableList(mods), Collections.unmodifiableList(chunks), median, threshold, spikes);
    }

    private static void add(Map<String, long[]> sums, Map<String, Thing> worst, String key, Thing th) {
        long[] sum = sums.computeIfAbsent(key, k -> new long[2]);
        sum[0] += th.totalNs();
        sum[1]++;
        worst.putIfAbsent(key, th);   // things come costliest first, so the first seen is the worst
    }

    private static final class ChunkAcc {
        final ResourceKey<Level> level;
        final int x;
        final int z;
        long total;
        int count;
        final Map<String, long[]> byType = new HashMap<>();

        ChunkAcc(ResourceKey<Level> level, int x, int z) {
            this.level = level;
            this.x = x;
            this.z = z;
        }

        void add(Thing th) {
            total += th.totalNs();
            count++;
            byType.computeIfAbsent(th.kind() + "|" + th.type(), k -> new long[1])[0] += th.totalNs();
        }

        ChunkGroup group() {
            String top = "";
            long topNs = -1;
            for (Map.Entry<String, long[]> e : byType.entrySet()) {
                if (e.getValue()[0] > topNs) {
                    top = e.getKey();
                    topNs = e.getValue()[0];
                }
            }
            int bar = top.indexOf('|');
            int topKind = bar < 0 ? -1 : Integer.parseInt(top.substring(0, bar));
            return new ChunkGroup(level, x, z, total, count, top.substring(bar + 1), topKind, Math.max(0, topNs));
        }
    }

    /** The things of a kind within a box, for tests and the overlay. */
    public List<Thing> thingsWithin(@Nullable ResourceKey<Level> level, int kind, BlockPos min, BlockPos max) {
        List<Thing> out = new ArrayList<>();
        for (Thing th : things) {
            if ((kind < 0 || th.kind() == kind) && (level == null || th.level().equals(level))) {
                BlockPos p = th.blockPos();
                if (p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                        && p.getZ() >= min.getZ() && p.getZ() <= max.getZ()) {
                    out.add(th);
                }
            }
        }
        return out;
    }
}
