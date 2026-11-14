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
 * nothing else. While one runs, each measured call is timed on the server thread, its nested measured calls (a
 * vehicle's passengers, say) taken out so each thing is charged only its own time, and the time goes to the thing's
 * slot in the scan's {@link CostTable}.
 */
public final class Probe {
    /** Read by every hook; written only on the server thread, between ticks. */
    public static boolean active;
    /** The server thread: only its calls are measured (anything ticking levels on other threads is left alone). */
    static Thread thread;
    static CostTable table;
    /** What one {@code System.nanoTime()} call costs here, measured as the scan starts and taken off every timing. */
    static long overheadNs;

    private static final int MAX_DEPTH = 256;
    /** For each depth of nested measured calls, the time its nested calls took. */
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
            childNs[d - 1] += elapsed + overheadNs;
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
            BlockPos pos = ticker.getPos();
            slot = t.newSlot(Kind.BLOCK_ENTITY, t.levelIndex(level), pos.asLong(), 0L, t.typeId(ticker.getType()), ticker);
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

    public static void chunk(ServerLevel level, LevelChunk chunk, long ns) {
        CostTable t = table;
        int li = t.levelIndex(level);
        long key = chunk.getPos().pack();
        int slot = t.slotAt(li, Kind.CHUNK, key);
        if (slot < 0) {
            long centre = BlockPos.asLong(chunk.getPos().getMinBlockX() + 8, level.getSeaLevel(), chunk.getPos().getMinBlockZ() + 8);
            slot = t.newSlot(Kind.CHUNK, li, centre, key, t.typeId("minecraft:chunk"), null);
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
            String name = type instanceof Block block ? BuiltInRegistries.BLOCK.getKey(block).toString()
                    : BuiltInRegistries.FLUID.getKey((Fluid) type).toString();
            slot = t.newSlot(kind, li, key, key, t.typeId(name), null);
        }
        if (slot >= 0) {
            t.add(slot, ns);
        }
    }
}
