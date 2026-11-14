package dev.culprit.scan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;

/**
 * The hooks' side of a tick scan (the mixins call it). While no scan runs, a hook reads {@link #active} and does
 * nothing else. While one runs, each measured call is timed on the server thread, the measured calls nested in it
 * (a vehicle's passengers, the entities inside a level's tick) taken out with what measuring them cost, so each thing
 * and each phase is charged only its own time; the time goes to the thing's slot in the scan's {@link CostTable}, or
 * to the phase's total.
 */
public final class Probe {
    /** Read by every hook; written only on the server thread, between ticks. */
    public static boolean active;
    /** The server thread: only its calls are measured (anything ticking levels on other threads is left alone). */
    static Thread thread;
    static CostTable table;
    /** What one {@code System.nanoTime()} call costs here, measured as the scan starts and taken off every timing. */
    static long overheadNs;
    /** What measuring one call costs in all (both timings, finding the slot, adding), measured as the scan starts. */
    static long perCallNs;

    private static final int MAX_DEPTH = 256;
    /** For each depth of nested measured calls, the time its nested calls took (with their measuring). */
    private static final long[] childNs = new long[MAX_DEPTH];
    private static int depth;

    private Probe() {}

    /** Whether this call should be measured: a scan runs and this is the server thread. */
    public static boolean measuring() {
        return active && Thread.currentThread() == thread;
    }

    /** Before a measured call; returns its start time. Always paired with {@link #end}, in a finally. */
    public static long begin() {
        if (depth < MAX_DEPTH) {
            childNs[depth] = 0;
        }
        depth++;
        return System.nanoTime();
    }

    /** After a measured call: its own time in nanoseconds, its nested measured calls' time taken out. */
    public static long end(long start) {
        long elapsed = System.nanoTime() - start - overheadNs;
        if (elapsed < 0) {
            elapsed = 0;
        }
        int d = --depth;
        long own = d < MAX_DEPTH ? elapsed - childNs[d] : elapsed;
        if (d > 0 && d - 1 < MAX_DEPTH) {
            childNs[d - 1] += elapsed + perCallNs;       // the parent is not charged for measuring this call either
        }
        return own < 0 ? 0 : own;
    }

    /** A tick starts: no measured call can be open (one that threw past its hook is forgotten here). */
    static void resetDepth() {
        depth = 0;
    }

    public static void blockEntity(ServerLevel level, TickingBlockEntity ticker, long ns) {
        CostTable t = table;
        int slot = t.slotOf(ticker);
        if (slot < 0) {
            slot = t.blockEntitySlot(t.levelIndex(level), ticker, ticker.getPos().asLong(), t.typeId(ticker.getType()));
        }
        if (slot >= 0) {
            t.add(slot, ns);
        }
    }

    public static void entity(ServerLevel level, Entity entity, long ns) {
        CostTable t = table;
        int slot = t.slotOf(entity);
        if (slot < 0) {
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            slot = t.newSlot(Kind.ENTITY, t.levelIndex(level), entity.blockPosition().asLong(), 0L, t.typeId(type), entity);
        }
        if (slot >= 0) {
            t.add(slot, ns);
        }
    }

    public static void blockTick(ServerLevel level, BlockPos pos, Block block, long ns) {
        positional(level, Kind.BLOCK_TICK, pos, block, ns);
    }

    public static void fluidTick(ServerLevel level, BlockPos pos, Fluid fluid, long ns) {
        positional(level, Kind.FLUID_TICK, pos, fluid, ns);
    }

    public static void blockEvent(ServerLevel level, BlockPos pos, Block block, long ns) {
        positional(level, Kind.BLOCK_EVENT, pos, block, ns);
    }

    /** A chunk's own upkeep (its random ticks are charged to their blocks apart). */
    public static void chunk(ServerLevel level, LevelChunk chunk, long ns) {
        perChunk(level, chunk, Kind.CHUNK, "minecraft:chunk_upkeep", ns);
    }

    /** A chunk's mob spawning. */
    public static void spawning(ServerLevel level, LevelChunk chunk, long ns) {
        perChunk(level, chunk, Kind.SPAWNING, "minecraft:mob_spawning", ns);
    }

    /** A random tick of a block (or fluid), charged to that kind of block in that chunk. */
    public static void randomTick(ServerLevel level, BlockPos pos, Object blockOrFluid, long ns) {
        CostTable t = table;
        int li = t.levelIndex(level);
        int type = t.typeIdOf(blockOrFluid);
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        long key = (((long) cx & 0x1FFFFFL) << 43) | (((long) cz & 0x1FFFFFL) << 22) | (type & 0x3FFFFFL);
        int slot = t.slotAt(li, Kind.RANDOM_TICK, key);
        if (slot < 0) {
            long centre = BlockPos.asLong((cx << 4) + 8, level.getSeaLevel(), (cz << 4) + 8);
            slot = t.newSlot(Kind.RANDOM_TICK, li, centre, key, type, null);
        }
        if (slot >= 0) {
            t.add(slot, ns);
        }
    }

    /** A phase of the tick ({@link Phase}), its own time. */
    public static void phase(int phase, long ns) {
        CostTable t = table;
        t.phaseNs[phase] += ns;
        t.phaseCalls++;
    }

    private static void perChunk(ServerLevel level, LevelChunk chunk, byte kind, String type, long ns) {
        CostTable t = table;
        int li = t.levelIndex(level);
        long key = chunk.getPos().pack();
        int slot = t.slotAt(li, kind, key);
        if (slot < 0) {
            long centre = BlockPos.asLong(chunk.getPos().getMinBlockX() + 8, level.getSeaLevel(), chunk.getPos().getMinBlockZ() + 8);
            slot = t.newSlot(kind, li, centre, key, t.typeId(type), null);
        }
        if (slot >= 0) {
            t.add(slot, ns);
        }
    }

    private static void positional(ServerLevel level, byte kind, BlockPos pos, Object type, long ns) {
        CostTable t = table;
        int li = t.levelIndex(level);
        long key = pos.asLong();
        int slot = t.slotAt(li, kind, key);
        if (slot < 0) {
            slot = t.newSlot(kind, li, key, key, t.typeIdOf(type), null);
        }
        if (slot >= 0) {
            t.add(slot, ns);
        }
    }

    /**
     * What measuring one call costs: many begin and end pairs with a slot looked up and added to, in a table of their
     * own (the scan's is untouched). Called as a scan starts.
     */
    static long calibrate() {
        CostTable scratch = new CostTable();
        Object thing = new Object();
        int slot = scratch.newSlot(Kind.ENTITY, 0, 0L, 0L, scratch.typeId("calibration"), thing);
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 10; round++) {
            long a = System.nanoTime();
            for (int i = 0; i < 2000; i++) {
                long s = begin();
                long own = end(s);
                int found = scratch.slotOf(thing);
                scratch.add(found >= 0 ? found : slot, own);
            }
            long b = System.nanoTime();
            best = Math.min(best, (b - a) / 2000);
        }
        depth = 0;
        return Math.max(0, best);
    }
}
