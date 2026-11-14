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

    /** One chunk: its total, how many things in it, and the type costing most there. */
    public record ChunkGroup(ResourceKey<Level> level, int x, int z, long totalNs, int things, String topType, long topTypeNs) {
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
    public final List<Thing> things;
    public final List<Group> types;
    public final List<Group> mods;
    public final List<ChunkGroup> chunks;

    private ScanReport(int ticks, long tickNsTotal, long tickNsMax, long startedAtMillis, boolean incomplete,
                       List<Thing> things, List<Group> types, List<Group> mods, List<ChunkGroup> chunks) {
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

    public long measuredNs() {
        long sum = 0;
        for (long ns : kindNs) {
            sum += ns;
        }
        return sum;
    }

    public static String modOf(String type) {
        int colon = type.indexOf(':');
        return colon < 0 ? "minecraft" : type.substring(0, colon);
    }

    static ScanReport of(CostTable t, int ticks, long tickNsTotal, long tickNsMax, long startedAtMillis) {
        List<Thing> things = new ArrayList<>(t.size);
        for (int s = 0; s < t.size; s++) {
            long pos = t.pos[s];
            int entityId = -1;
            if (t.ref[s] instanceof Entity entity && !entity.isRemoved()) {
                pos = entity.blockPosition().asLong();   // where it is now, not where it was first measured
                entityId = entity.getId();
            }
            things.add(new Thing(t.kind[s], t.levels.get(t.level[s]), pos, t.typeNames.get(t.type[s]), t.totalNs[s], t.maxNs[s], t.calls[s], entityId));
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
            if (th.kind() != Kind.CHUNK) {
                add(modSums, modWorst, th.mod(), th);
            }
            BlockPos p = th.blockPos();
            int cx = p.getX() >> 4;
            int cz = p.getZ() >> 4;
            String chunkKey = th.level().identifier() + "|" + cx + "|" + cz;
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
        return new ScanReport(ticks, tickNsTotal, tickNsMax, startedAtMillis, t.full, Collections.unmodifiableList(things),
                Collections.unmodifiableList(types), Collections.unmodifiableList(mods), Collections.unmodifiableList(chunks));
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
            byType.computeIfAbsent(th.type(), k -> new long[1])[0] += th.totalNs();
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
            return new ChunkGroup(level, x, z, total, count, top, Math.max(0, topNs));
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
