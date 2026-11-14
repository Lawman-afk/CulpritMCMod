package dev.culprit.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.culprit.scan.Kind;
import dev.culprit.scan.Phase;
import dev.culprit.scan.ScanReport;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A scan report as it travels to a player's client and as the client keeps it: the costliest things (up to
 * {@link #MAX_THINGS}), types, mods and chunks, in flat arrays with string tables, the summary beside them. Sums are
 * the server's, over everything the scan measured, not only what is sent. Common code: no client classes.
 */
public final class ReportData {
    public static final int MAX_THINGS = 4000;
    public static final int MAX_TYPES = 300;
    public static final int MAX_MODS = 100;
    public static final int MAX_CHUNKS = 1500;

    public int ticks;
    public long tickNsTotal;
    public long tickNsMax;
    public boolean incomplete;
    /** How many things the scan measured in all (only the costliest are sent). */
    public int thingCount;
    public final long[] kindNs = new long[Kind.COUNT];
    /** The tick's phases' own time ({@link Phase}), and Culprit's own timing, estimated. */
    public final long[] phaseNs = new long[Phase.COUNT];
    public long selfNs;
    /** The scan's usual tick (its median), and the time over which a tick counts as a lag spike. */
    public long medianTickNs, spikeThresholdNs;
    /** The lag spikes: the same lists over the spike ticks only; null when no tick was one (and in a spikes report). */
    public @org.jspecify.annotations.Nullable ReportData spikes;
    public String[] dims = new String[0];
    public String[] names = new String[0];

    public int things;
    public byte[] kind;
    public int[] dim;
    public long[] pos;
    public int[] type;
    public long[] total;
    public long[] max;
    public long[] calls;
    public int[] entity;

    public int types;
    public int[] typeName;
    public byte[] typeKind;
    public long[] typeTotal;
    public int[] typeCount;
    public int[] typeWorstDim;
    public long[] typeWorstPos;

    public int mods;
    public int[] modName;
    public long[] modTotal;
    public int[] modCount;
    public int[] modWorstDim;
    public long[] modWorstPos;
    public byte[] modWorstKind;

    public int chunks;
    public int[] chunkDim;
    public int[] chunkX;
    public int[] chunkZ;
    public long[] chunkTotal;
    public int[] chunkCount;
    public int[] chunkTop;
    public long[] chunkTopNs;
    public byte[] chunkTopKind;

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

    public long phasesNs() {
        long sum = 0;
        for (long ns : phaseNs) {
            sum += ns;
        }
        return sum;
    }

    /** The part of the ticks nothing measured covers. */
    public long unaccountedNs() {
        return Math.max(0, tickNsTotal - measuredNs() - phasesNs() - selfNs);
    }

    public static ReportData of(ScanReport r) {
        ReportData d = new ReportData();
        d.ticks = r.ticks;
        d.tickNsTotal = r.tickNsTotal;
        d.tickNsMax = r.tickNsMax;
        d.incomplete = r.incomplete;
        d.thingCount = r.things.size();
        System.arraycopy(r.kindNs, 0, d.kindNs, 0, Kind.COUNT);
        System.arraycopy(r.phaseNs, 0, d.phaseNs, 0, Phase.COUNT);
        d.selfNs = r.selfNs;
        Table dims = new Table();
        Table names = new Table();

        d.things = Math.min(MAX_THINGS, r.things.size());
        d.kind = new byte[d.things];
        d.dim = new int[d.things];
        d.pos = new long[d.things];
        d.type = new int[d.things];
        d.total = new long[d.things];
        d.max = new long[d.things];
        d.calls = new long[d.things];
        d.entity = new int[d.things];
        for (int i = 0; i < d.things; i++) {
            ScanReport.Thing t = r.things.get(i);
            d.kind[i] = t.kind();
            d.dim[i] = dims.id(t.level().identifier().toString());
            d.pos[i] = t.pos();
            d.type[i] = names.id(t.type());
            d.total[i] = t.totalNs();
            d.max[i] = t.maxNs();
            d.calls[i] = t.calls();
            d.entity[i] = t.entityId();
        }

        d.types = Math.min(MAX_TYPES, r.types.size());
        d.typeName = new int[d.types];
        d.typeKind = new byte[d.types];
        d.typeTotal = new long[d.types];
        d.typeCount = new int[d.types];
        d.typeWorstDim = new int[d.types];
        d.typeWorstPos = new long[d.types];
        for (int i = 0; i < d.types; i++) {
            ScanReport.Group g = r.types.get(i);
            d.typeName[i] = names.id(g.name());
            d.typeKind[i] = (byte) g.kind();
            d.typeTotal[i] = g.totalNs();
            d.typeCount[i] = g.things();
            d.typeWorstDim[i] = dims.id(g.worst().level().identifier().toString());
            d.typeWorstPos[i] = g.worst().pos();
        }

        d.mods = Math.min(MAX_MODS, r.mods.size());
        d.modName = new int[d.mods];
        d.modTotal = new long[d.mods];
        d.modCount = new int[d.mods];
        d.modWorstDim = new int[d.mods];
        d.modWorstPos = new long[d.mods];
        d.modWorstKind = new byte[d.mods];
        for (int i = 0; i < d.mods; i++) {
            ScanReport.Group g = r.mods.get(i);
            d.modName[i] = names.id(g.name());
            d.modTotal[i] = g.totalNs();
            d.modCount[i] = g.things();
            d.modWorstDim[i] = dims.id(g.worst().level().identifier().toString());
            d.modWorstPos[i] = g.worst().pos();
            d.modWorstKind[i] = g.worst().kind();
        }

        d.chunks = Math.min(MAX_CHUNKS, r.chunks.size());
        d.chunkDim = new int[d.chunks];
        d.chunkX = new int[d.chunks];
        d.chunkZ = new int[d.chunks];
        d.chunkTotal = new long[d.chunks];
        d.chunkCount = new int[d.chunks];
        d.chunkTop = new int[d.chunks];
        d.chunkTopNs = new long[d.chunks];
        d.chunkTopKind = new byte[d.chunks];
        for (int i = 0; i < d.chunks; i++) {
            ScanReport.ChunkGroup c = r.chunks.get(i);
            d.chunkDim[i] = dims.id(c.level().identifier().toString());
            d.chunkX[i] = c.x();
            d.chunkZ[i] = c.z();
            d.chunkTotal[i] = c.totalNs();
            d.chunkCount[i] = c.things();
            d.chunkTop[i] = names.id(c.topType());
            d.chunkTopNs[i] = c.topTypeNs();
            d.chunkTopKind[i] = (byte) c.topKind();
        }
        d.dims = dims.list.toArray(String[]::new);
        d.names = names.list.toArray(String[]::new);
        d.medianTickNs = r.medianTickNs;
        d.spikeThresholdNs = r.spikeThresholdNs;
        d.spikes = r.spikes == null ? null : of(r.spikes);
        return d;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(ticks);
        buf.writeVarLong(tickNsTotal);
        buf.writeVarLong(tickNsMax);
        buf.writeBoolean(incomplete);
        buf.writeVarInt(thingCount);
        for (long ns : kindNs) {
            buf.writeVarLong(ns);
        }
        for (long ns : phaseNs) {
            buf.writeVarLong(ns);
        }
        buf.writeVarLong(selfNs);
        strings(buf, dims);
        strings(buf, names);
        buf.writeVarInt(things);
        for (int i = 0; i < things; i++) {
            buf.writeByte(kind[i]);
            buf.writeVarInt(dim[i]);
            buf.writeLong(pos[i]);
            buf.writeVarInt(type[i]);
            buf.writeVarLong(total[i]);
            buf.writeVarLong(max[i]);
            buf.writeVarLong(calls[i]);
            buf.writeVarInt(entity[i] + 1);
        }
        buf.writeVarInt(types);
        for (int i = 0; i < types; i++) {
            buf.writeVarInt(typeName[i]);
            buf.writeByte(typeKind[i]);
            buf.writeVarLong(typeTotal[i]);
            buf.writeVarInt(typeCount[i]);
            buf.writeVarInt(typeWorstDim[i]);
            buf.writeLong(typeWorstPos[i]);
        }
        buf.writeVarInt(mods);
        for (int i = 0; i < mods; i++) {
            buf.writeVarInt(modName[i]);
            buf.writeVarLong(modTotal[i]);
            buf.writeVarInt(modCount[i]);
            buf.writeVarInt(modWorstDim[i]);
            buf.writeLong(modWorstPos[i]);
            buf.writeByte(modWorstKind[i]);
        }
        buf.writeVarInt(chunks);
        for (int i = 0; i < chunks; i++) {
            buf.writeVarInt(chunkDim[i]);
            buf.writeVarInt(chunkX[i]);
            buf.writeVarInt(chunkZ[i]);
            buf.writeVarLong(chunkTotal[i]);
            buf.writeVarInt(chunkCount[i]);
            buf.writeVarInt(chunkTop[i]);
            buf.writeVarLong(chunkTopNs[i]);
            buf.writeByte(chunkTopKind[i]);
        }
        buf.writeVarLong(medianTickNs);
        buf.writeVarLong(spikeThresholdNs);
        buf.writeBoolean(spikes != null);
        if (spikes != null) {
            spikes.write(buf);
        }
    }

    public static ReportData read(FriendlyByteBuf buf) {
        return read(buf, true);
    }

    /** Reads a report, with a spikes report inside it only when {@code outer} (never one inside that). */
    private static ReportData read(FriendlyByteBuf buf, boolean outer) {
        ReportData d = new ReportData();
        d.ticks = buf.readVarInt();
        d.tickNsTotal = buf.readVarLong();
        d.tickNsMax = buf.readVarLong();
        d.incomplete = buf.readBoolean();
        d.thingCount = buf.readVarInt();
        for (int k = 0; k < Kind.COUNT; k++) {
            d.kindNs[k] = buf.readVarLong();
        }
        for (int p = 0; p < Phase.COUNT; p++) {
            d.phaseNs[p] = buf.readVarLong();
        }
        d.selfNs = buf.readVarLong();
        d.dims = strings(buf);
        d.names = strings(buf);
        d.things = Math.min(buf.readVarInt(), MAX_THINGS);
        d.kind = new byte[d.things];
        d.dim = new int[d.things];
        d.pos = new long[d.things];
        d.type = new int[d.things];
        d.total = new long[d.things];
        d.max = new long[d.things];
        d.calls = new long[d.things];
        d.entity = new int[d.things];
        for (int i = 0; i < d.things; i++) {
            d.kind[i] = buf.readByte();
            d.dim[i] = buf.readVarInt();
            d.pos[i] = buf.readLong();
            d.type[i] = buf.readVarInt();
            d.total[i] = buf.readVarLong();
            d.max[i] = buf.readVarLong();
            d.calls[i] = buf.readVarLong();
            d.entity[i] = buf.readVarInt() - 1;
        }
        d.types = Math.min(buf.readVarInt(), MAX_TYPES);
        d.typeName = new int[d.types];
        d.typeKind = new byte[d.types];
        d.typeTotal = new long[d.types];
        d.typeCount = new int[d.types];
        d.typeWorstDim = new int[d.types];
        d.typeWorstPos = new long[d.types];
        for (int i = 0; i < d.types; i++) {
            d.typeName[i] = buf.readVarInt();
            d.typeKind[i] = buf.readByte();
            d.typeTotal[i] = buf.readVarLong();
            d.typeCount[i] = buf.readVarInt();
            d.typeWorstDim[i] = buf.readVarInt();
            d.typeWorstPos[i] = buf.readLong();
        }
        d.mods = Math.min(buf.readVarInt(), MAX_MODS);
        d.modName = new int[d.mods];
        d.modTotal = new long[d.mods];
        d.modCount = new int[d.mods];
        d.modWorstDim = new int[d.mods];
        d.modWorstPos = new long[d.mods];
        d.modWorstKind = new byte[d.mods];
        for (int i = 0; i < d.mods; i++) {
            d.modName[i] = buf.readVarInt();
            d.modTotal[i] = buf.readVarLong();
            d.modCount[i] = buf.readVarInt();
            d.modWorstDim[i] = buf.readVarInt();
            d.modWorstPos[i] = buf.readLong();
            d.modWorstKind[i] = buf.readByte();
        }
        d.chunks = Math.min(buf.readVarInt(), MAX_CHUNKS);
        d.chunkDim = new int[d.chunks];
        d.chunkX = new int[d.chunks];
        d.chunkZ = new int[d.chunks];
        d.chunkTotal = new long[d.chunks];
        d.chunkCount = new int[d.chunks];
        d.chunkTop = new int[d.chunks];
        d.chunkTopNs = new long[d.chunks];
        d.chunkTopKind = new byte[d.chunks];
        for (int i = 0; i < d.chunks; i++) {
            d.chunkDim[i] = buf.readVarInt();
            d.chunkX[i] = buf.readVarInt();
            d.chunkZ[i] = buf.readVarInt();
            d.chunkTotal[i] = buf.readVarLong();
            d.chunkCount[i] = buf.readVarInt();
            d.chunkTop[i] = buf.readVarInt();
            d.chunkTopNs[i] = buf.readVarLong();
            d.chunkTopKind[i] = buf.readByte();
        }
        d.medianTickNs = buf.readVarLong();
        d.spikeThresholdNs = buf.readVarLong();
        if (buf.readBoolean()) {
            if (!outer) {
                throw new io.netty.handler.codec.DecoderException("Culprit: a spikes report inside a spikes report");
            }
            d.spikes = read(buf, false);
        }
        return d;
    }

    private static void strings(FriendlyByteBuf buf, String[] list) {
        buf.writeVarInt(list.length);
        for (String s : list) {
            buf.writeUtf(s, 256);
        }
    }

    private static String[] strings(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 1 << 16);
        String[] out = new String[n];
        for (int i = 0; i < n; i++) {
            out[i] = buf.readUtf(256);
        }
        return out;
    }

    /** Strings to small numbers, each once. */
    private static final class Table {
        final List<String> list = new ArrayList<>();
        final Map<String, Integer> ids = new HashMap<>();

        int id(String s) {
            return ids.computeIfAbsent(s, k -> {
                list.add(k);
                return list.size() - 1;
            });
        }
    }
}
