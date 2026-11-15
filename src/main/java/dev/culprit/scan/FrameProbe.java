package dev.culprit.scan;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
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
    /** Parts handed in to be drawn, counted while a frame scan runs (mixin/SubmitNodeFrameMixin); and the count at this frame's start. */
    public static long parts;
    private static long frameParts;

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
        frameParts = parts;
    }

    /** At a frame's end: the sorting and the drawing of the handed-in parts shared out to the things, by what each handed in. */
    static void shareOut() {
        table.shareByParts(FramePhase.BATCHING, FramePhase.FEATURES, parts - frameParts);
    }

    /** Time making or drawing an entity, charged to it; its slot, or -1. */
    public static int entity(Entity entity, long ns) {
        CostTable t = table;
        int slot = t.slotOf(entity);
        if (slot < 0) {
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            slot = t.newSlot(Kind.ENTITY, t.levelIndex(entity.level()), entity.blockPosition().asLong(), 0L, t.typeId(type), entity);
        }
        if (slot >= 0) {
            t.add(slot, ns);
        }
        return slot;
    }

    /** Time making or drawing a block entity, charged to it; its slot, or -1. */
    public static int blockEntity(BlockEntity blockEntity, long ns) {
        CostTable t = table;
        int slot = t.slotOf(blockEntity);
        if (slot < 0) {
            Level level = blockEntity.getLevel();
            if (level == null) {
                return -1;
            }
            String type = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
            // A place's block entities are one thing, as in a tick scan (a piston's moving block is new at every push).
            slot = t.blockEntitySlot(t.levelIndex(level), blockEntity, blockEntity.getBlockPos().asLong(), t.typeId(type));
        }
        if (slot >= 0) {
            t.add(slot, ns);
        }
        return slot;
    }

    /** Time moving or making ready a particle, charged to its kind in its chunk. */
    public static void particle(Level level, ParticleInfo particle, long ns) {
        CostTable t = table;
        Object type = particle.culprit$type();
        int typeId = t.typeIdOf(type != null ? type : particle.getClass());
        int li = t.levelIndex(level);
        int cx = Mth.floor(particle.culprit$x()) >> 4, cz = Mth.floor(particle.culprit$z()) >> 4;
        long key = (((long) cx & 0x1FFFFFL) << 43) | (((long) cz & 0x1FFFFFL) << 22) | (typeId & 0x3FFFFFL);
        int slot = t.slotAt(li, Kind.PARTICLE, key);
        if (slot < 0) {
            slot = t.newSlot(Kind.PARTICLE, li, BlockPos.asLong((cx << 4) + 8, level.getSeaLevel(), (cz << 4) + 8), key, typeId, null);
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

    /**
     * Time handing in a render state's parts to be drawn, and how many, charged to the thing it was made from (if it was
     * made this frame): its parts are its share of the sorting and drawing, at the frame's end.
     */
    public static void drawn(Object state, long ns, long handedIn) {
        Object thing = STATES.get(state);
        int slot;
        if (((Object) thing) instanceof Entity entity) {
            slot = entity(entity, ns);
        } else if (((Object) thing) instanceof BlockEntity blockEntity) {
            slot = blockEntity(blockEntity, ns);
        } else {
            phase(FramePhase.SUBMIT, ns);
            return;
        }
        if (slot >= 0 && handedIn > 0) {
            table.addParts(slot, handedIn);
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
