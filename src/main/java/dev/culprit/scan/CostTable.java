package dev.culprit.scan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
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

    private final IntArrayList touched = new IntArrayList();
    /** The tick being measured, counted from 1 (a slot's lastTick 0 means never). */
    private int tick = 1;

    CostTable() {
        byRef.defaultReturnValue(-1);
        typeIds.defaultReturnValue(-1);
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
    }

    /** Folds the tick that just ended into each thing's total and worst tick. */
    void endTick() {
        int[] slots = touched.elements();
        for (int i = 0, n = touched.size(); i < n; i++) {
            int s = slots[i];
            long ns = tickNs[s];
            totalNs[s] += ns;
            if (ns > maxNs[s]) {
                maxNs[s] = ns;
            }
            tickNs[s] = 0;
        }
        touched.clear();
        tick++;
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
