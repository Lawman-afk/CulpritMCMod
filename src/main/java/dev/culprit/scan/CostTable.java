package dev.culprit.scan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jspecify.annotations.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Everything one tick scan measured, thing by thing: a slot per thing (a block entity's ticker, an entity, a
 * position's scheduled ticks or block events, a chunk) and its costs in primitive arrays. A thing finds its slot once,
 * by identity or by position, and the arrays grow by doubling, so measuring allocates nothing per call once every
 * thing has its slot. Server thread only.
 */
final class CostTable {
    /** A scan stops adding things past this many (its report says so); what it already has goes on being measured. */
    static final int MAX_SLOTS = 1 << 20;

    byte[] kind = new byte[1024];
    short[] level = new short[1024];
    long[] pos = new long[1024];
    int[] type = new int[1024];
    /** The ticker or entity itself (for its position at the end), or null for things known by position. */
    Object[] ref = new Object[1024];
    long[] totalNs = new long[1024];
    long[] tickNs = new long[1024];
    long[] maxNs = new long[1024];
    long[] calls = new long[1024];
    private int[] lastTick = new int[1024];
    int size;
    boolean full;

    private final Reference2IntOpenHashMap<Object> byRef = new Reference2IntOpenHashMap<>();
    /** Per level, per kind known by position: position (or chunk) to slot. */
    private final List<Long2IntOpenHashMap[]> byPos = new ArrayList<>();
    final List<ResourceKey<Level>> levels = new ArrayList<>();
    private final Map<ServerLevel, Integer> levelIndex = new IdentityHashMap<>();
    private ServerLevel lastLevel;
    private int lastLevelIndex;
    final List<String> typeNames = new ArrayList<>();
    private final Object2IntOpenHashMap<String> typeIds = new Object2IntOpenHashMap<>();
    /** Blocks and fluids to their type number, found once each (random ticks look theirs up on every call). */
    private final Reference2IntOpenHashMap<Object> typeOfObject = new Reference2IntOpenHashMap<>();

    /** The tick's phases: their own time, summed over the scan, and how many times they were timed. */
    final long[] phaseNs = new long[Phase.COUNT];
    long phaseCalls;
    /** This tick's phases' own time and its measured calls, folded into the totals (and a kept tick) as it ends. */
    final long[] phaseTickNs = new long[Phase.COUNT];
    long tickCalls;
    /** Every tick's time, in order (the scan's usual tick is their median). */
    final LongArrayList tickTimes = new LongArrayList();
    /** The slowest ticks so far, at most {@link #keep} of them, each kept whole (the lag spikes come from these). */
    final List<TickRecord> kept = new ArrayList<>();
    private final int keep;

    /** One tick kept whole: its time, what each thing (by slot) and each phase took in it, and its measured calls. */
    static final class TickRecord {
        long tickNs;
        int size;
        int[] slots = new int[64];
        long[] ns = new long[64];
        final long[] phases = new long[Phase.COUNT];
        long calls;
    }

    private final IntArrayList touched = new IntArrayList();
    /** The tick being measured, counted from 1 (a slot's lastTick 0 means never). */
    private int tick = 1;

    CostTable() {
        this(0);
    }

    /** A table keeping its {@code keep} slowest ticks whole (none for 0). */
    CostTable(int keep) {
        this.keep = keep;
        byRef.defaultReturnValue(-1);
        typeIds.defaultReturnValue(-1);
        typeOfObject.defaultReturnValue(-1);
    }

    /** The type number of a block or fluid, by its registry name the first time. */
    int typeIdOf(Object blockOrFluid) {
        int id = typeOfObject.getInt(blockOrFluid);
        if (id < 0) {
            String name = blockOrFluid instanceof net.minecraft.world.level.block.Block block
                    ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString()
                    : net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey((net.minecraft.world.level.material.Fluid) blockOrFluid).toString();
            id = typeId(name);
            typeOfObject.put(blockOrFluid, id);
        }
        return id;
    }

    /** Every measured call, things and phases: what Culprit's own timing cost is estimated from. */
    long calls() {
        long sum = phaseCalls;
        for (int s = 0; s < size; s++) {
            sum += calls[s];
        }
        return sum;
    }

    int levelIndex(ServerLevel level) {
        if (level == lastLevel) {
            return lastLevelIndex;
        }
        Integer index = levelIndex.get(level);
        if (index == null) {
            index = levels.size();
            levels.add(level.dimension());
            levelIndex.put(level, index);
            Long2IntOpenHashMap[] maps = new Long2IntOpenHashMap[Kind.COUNT];
            for (int k = 0; k < maps.length; k++) {
                maps[k] = new Long2IntOpenHashMap();
                maps[k].defaultReturnValue(-1);
            }
            byPos.add(maps);
        }
        lastLevel = level;
        lastLevelIndex = index;
        return index;
    }

    int typeId(String name) {
        int id = typeIds.getInt(name);
        if (id < 0) {
            id = typeNames.size();
            typeNames.add(name);
            typeIds.put(name, id);
        }
        return id;
    }

    /** The slot of a thing known by identity (a ticker, an entity), or -1. */
    int slotOf(Object thing) {
        return byRef.getInt(thing);
    }

    /** The slot of a thing known by position (scheduled ticks, block events, chunks), or -1. */
    int slotAt(int levelIndex, byte kind, long key) {
        return byPos.get(levelIndex)[kind].get(key);
    }

    /**
     * The slot of a block entity's ticker seen for the first time: its place's slot if the same type ticked there
     * before (a piston's moving block is a new block entity at every push; a block broken and placed again), so a place
     * is one thing; else a new slot. -1 once the table is full.
     */
    int blockEntitySlot(int levelIndex, Object ticker, long pos, int type) {
        Long2IntOpenHashMap places = byPos.get(levelIndex)[Kind.BLOCK_ENTITY];
        int slot = places.get(pos);
        if (slot >= 0 && this.type[slot] == type) {
            byRef.put(ticker, slot);
            ref[slot] = ticker;
            return slot;
        }
        slot = newSlot(Kind.BLOCK_ENTITY, levelIndex, pos, pos, type, ticker);
        if (slot >= 0) {
            places.put(pos, slot);
        }
        return slot;
    }

    /** A new slot for a thing; -1 once the table is full. {@code ref} null means the thing is known by position. */
    int newSlot(byte kind, int levelIndex, long pos, long key, int type, Object ref) {
        if (size >= MAX_SLOTS) {
            full = true;
            return -1;
        }
        if (size == this.kind.length) {
            grow();
        }
        int slot = size++;
        this.kind[slot] = kind;
        this.level[slot] = (short) levelIndex;
        this.pos[slot] = pos;
        this.type[slot] = type;
        this.ref[slot] = ref;
        if (ref != null) {
            byRef.put(ref, slot);
        } else {
            byPos.get(levelIndex)[kind].put(key, slot);
        }
        return slot;
    }

    void add(int slot, long ns) {
        if (lastTick[slot] != tick) {
            lastTick[slot] = tick;
            touched.add(slot);
        }
        tickNs[slot] += ns;
        calls[slot]++;
        tickCalls++;
    }

    /**
     * Folds the tick that just ended (it took {@code tickTime}) into each thing's total and worst tick, and the phases'
     * totals; keeps it whole when it is among the slowest so far.
     */
    void endTick(long tickTime) {
        tickTimes.add(tickTime);
        TickRecord record = keep > 0 ? recordFor(tickTime) : null;
        int[] slots = touched.elements();
        int n = touched.size();
        if (record != null) {
            record.tickNs = tickTime;
            record.size = n;
            record.calls = tickCalls;
            if (record.slots.length < n) {
                int grown = Math.max(n, record.slots.length * 2);
                record.slots = new int[grown];
                record.ns = new long[grown];
            }
        }
        for (int i = 0; i < n; i++) {
            int s = slots[i];
            long ns = tickNs[s];
            totalNs[s] += ns;
            if (ns > maxNs[s]) {
                maxNs[s] = ns;
            }
            if (record != null) {
                record.slots[i] = s;
                record.ns[i] = ns;
            }
            tickNs[s] = 0;
        }
        for (int p = 0; p < Phase.COUNT; p++) {
            phaseNs[p] += phaseTickNs[p];
            if (record != null) {
                record.phases[p] = phaseTickNs[p];
            }
            phaseTickNs[p] = 0;
        }
        tickCalls = 0;
        touched.clear();
        tick++;
    }

    /** Where to keep a tick that took {@code tickTime}: a new record while there is room, else the fastest kept one's if it was slower; null if not. */
    private @Nullable TickRecord recordFor(long tickTime) {
        if (kept.size() < keep) {
            TickRecord record = new TickRecord();
            kept.add(record);
            return record;
        }
        TickRecord fastest = kept.getFirst();
        for (TickRecord record : kept) {
            if (record.tickNs < fastest.tickNs) {
                fastest = record;
            }
        }
        return tickTime > fastest.tickNs ? fastest : null;
    }

    private void grow() {
        int n = kind.length * 2;
        kind = Arrays.copyOf(kind, n);
        level = Arrays.copyOf(level, n);
        pos = Arrays.copyOf(pos, n);
        type = Arrays.copyOf(type, n);
        ref = Arrays.copyOf(ref, n);
        totalNs = Arrays.copyOf(totalNs, n);
        tickNs = Arrays.copyOf(tickNs, n);
        maxNs = Arrays.copyOf(maxNs, n);
        calls = Arrays.copyOf(calls, n);
        lastTick = Arrays.copyOf(lastTick, n);
    }
}
