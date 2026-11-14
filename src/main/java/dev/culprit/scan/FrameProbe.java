package dev.culprit.scan;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The frame hooks' side of a frame scan (the client mixins call it), as {@link Probe} is the tick hooks': while no
 * frame scan runs, a hook reads {@link #active} and does nothing else. While one runs, each measured call on the render
 * thread is timed, the measured calls nested in it taken out, and charged to the entity or block entity it drew, or to
 * a part of the frame. An entity's or block entity's state is made in one step and drawn in the next: the states made
 * this frame are remembered, so the drawing is charged to the thing too. Its own nesting, apart from the server
 * thread's, so a tick scan and a frame scan can run at once in a single-player game.
 */
public final class FrameProbe {
    /** Read by every frame hook; written only on the render thread, between frames. */
    public static boolean active;
    static Thread thread;
    static CostTable table;
    static long overheadNs;
    static long perCallNs;

    private static final int MAX_DEPTH = 256;
    private static final long[] childNs = new long[MAX_DEPTH];
    private static int depth;
    /** This frame's render states, to the entity or block entity each was made from. */
    private static final Map<Object, Object> STATES = new IdentityHashMap<>();

    private FrameProbe() {}

    public static boolean measuring() {
        return active && Thread.currentThread() == thread;
    }

    public static long begin() {
        if (depth < MAX_DEPTH) {
            childNs[depth] = 0;
        }
        depth++;
        return System.nanoTime();
    }

    public static long end(long start) {
        long elapsed = System.nanoTime() - start - overheadNs;
        if (elapsed < 0) {
            elapsed = 0;
        }
        int d = --depth;
        long own = d < MAX_DEPTH ? elapsed - childNs[d] : elapsed;
        if (d > 0 && d - 1 < MAX_DEPTH) {
            childNs[d - 1] += elapsed + perCallNs;
        }
        return own < 0 ? 0 : own;
    }

    /** A frame starts: nothing measured is open, and last frame's states are forgotten. */
    static void frameStarts() {
        depth = 0;
        STATES.clear();
    }

    /** Time making or drawing an entity, charged to it. */
    public static void entity(Entity entity, long ns) {
        CostTable t = table;
        int slot = t.slotOf(entity);
        if (slot < 0) {
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            slot = t.newSlot(Kind.ENTITY, t.levelIndex(entity.level()), entity.blockPosition().asLong(), 0L, t.typeId(type), entity);
        }
        if (slot >= 0) {
            t.add(slot, ns);
        }
    }

    /** Time making or drawing a block entity, charged to it. */
    public static void blockEntity(BlockEntity blockEntity, long ns) {
        CostTable t = table;
        int slot = t.slotOf(blockEntity);
        if (slot < 0) {
            Level level = blockEntity.getLevel();
            if (level == null) {
                return;
            }
            String type = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
            slot = t.newSlot(Kind.BLOCK_ENTITY, t.levelIndex(level), blockEntity.getBlockPos().asLong(), 0L, t.typeId(type), blockEntity);
        }
        if (slot >= 0) {
            t.add(slot, ns);
        }
    }

    /** Remembers which thing a render state was made from, for when it is drawn. */
    public static void stateOf(Object state, Object thing) {
        if (state != null) {
            STATES.put(state, thing);
        }
    }

    /** Time drawing a render state, charged to the thing it was made from (if it was made this frame). */
    public static void drawn(Object state, long ns) {
        Object thing = STATES.get(state);
        if (thing instanceof Entity entity) {
            entity(entity, ns);
        } else if (thing instanceof BlockEntity blockEntity) {
            blockEntity(blockEntity, ns);
        } else {
            phase(FramePhase.SUBMIT, ns);
        }
    }

    /** A part of the frame ({@link FramePhase}), its own time. */
    public static void phase(int phase, long ns) {
        CostTable t = table;
        t.phaseTickNs[phase] += ns;
        t.phaseCalls++;
        t.tickCalls++;
    }

    /** What measuring one call costs here, as {@link Probe#calibrate()} finds it for the server thread. */
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
