package dev.culprit.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;

/**
 * The chunk inspector's findings (DESIGN §4.3), as sent to a client: per dimension, each loaded chunk (where, how active,
 * why: its strongest loading ticket's reason and source, and what is in it) and the sources (the loading tickets:
 * where, how strong, why, who or what owns them). A chunk is loaded by every source within reach of it; its reason is
 * the strongest one's (a player's is taken last on a tie, as a player's own surroundings are expected to be loaded).
 */
public final class ChunkData {
    /** Why a chunk is loaded (its colour in the results and on the world). */
    public static final byte PLAYERS = 0, MODS = 1, FORCED = 2, PORTALS = 3, PEARLS = 4, SPAWN = 5, OTHER = 6, REQUESTS = 7;
    public static final int REASONS = 8;
    /** How active a loaded chunk is. */
    public static final byte BORDER = 0, BLOCK_TICKING = 1, ENTITY_TICKING = 2;
    /** The chunk level up to which a chunk is playable (loaded in full): a source of level L reaches 33 - L chunks out. */
    public static final int FULL_LEVEL = 33;
    /** At most this many chunks are sent, all dimensions together (the payload's size; the rest are counted). */
    public static final int MAX_CHUNKS = 40_000;

    /** One dimension's loaded chunks and their sources. */
    public static final class Dim {
        public String dim = "";
        /** Chunks loaded beyond the playable ones (being made, or kept for their neighbours). */
        public int edges;
        /** Playable chunks in all (more than are sent when there are very many). */
        public int total;
        public int chunks;
        public int[] x = new int[0], z = new int[0];
        public byte[] status = new byte[0], reason = new byte[0];
        /** The index of the chunk's strongest source, or -1. */
        public int[] source = new int[0];
        /** Every reason that reaches the chunk, a bit each. */
        public byte[] reasons = new byte[0];
        public int[] blockEntities = new int[0], entities = new int[0];
        public int sources;
        public int[] sx = new int[0], sz = new int[0], slevel = new int[0];
        public byte[] sreason = new byte[0];
        public String[] sowner = new String[0];

        /** How many chunks out a source reaches while keeping them playable. */
        public int reach(int s) {
            return Math.max(0, FULL_LEVEL - slevel[s]);
        }
    }

    public final List<Dim> dims = new ArrayList<>();
    public long takenAtMillis;

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(takenAtMillis);
        buf.writeVarInt(dims.size());
        for (Dim d : dims) {
            buf.writeUtf(d.dim, 256);
            buf.writeVarInt(d.edges);
            buf.writeVarInt(d.total);
            buf.writeVarInt(d.chunks);
            for (int i = 0; i < d.chunks; i++) {
                buf.writeVarInt(d.x[i]);
                buf.writeVarInt(d.z[i]);
                buf.writeByte(d.status[i]);
                buf.writeByte(d.reason[i]);
                buf.writeVarInt(d.source[i] + 1);
                buf.writeByte(d.reasons[i]);
                buf.writeVarInt(d.blockEntities[i]);
                buf.writeVarInt(d.entities[i]);
            }
            buf.writeVarInt(d.sources);
            for (int s = 0; s < d.sources; s++) {
                buf.writeVarInt(d.sx[s]);
                buf.writeVarInt(d.sz[s]);
                buf.writeVarInt(d.slevel[s]);
                buf.writeByte(d.sreason[s]);
                buf.writeUtf(d.sowner[s], 512);
            }
        }
    }

    public static ChunkData read(FriendlyByteBuf buf) {
        ChunkData data = new ChunkData();
        data.takenAtMillis = buf.readLong();
        int dims = Math.min(buf.readVarInt(), 64);
        int budget = MAX_CHUNKS;
        for (int k = 0; k < dims; k++) {
            Dim d = new Dim();
            d.dim = buf.readUtf(256);
            d.edges = buf.readVarInt();
            d.total = buf.readVarInt();
            d.chunks = dev.culprit.legacy.J21.clamp(buf.readVarInt(), 0, budget);
            budget -= d.chunks;
            d.x = new int[d.chunks];
            d.z = new int[d.chunks];
            d.status = new byte[d.chunks];
            d.reason = new byte[d.chunks];
            d.source = new int[d.chunks];
            d.reasons = new byte[d.chunks];
            d.blockEntities = new int[d.chunks];
            d.entities = new int[d.chunks];
            for (int i = 0; i < d.chunks; i++) {
                d.x[i] = buf.readVarInt();
                d.z[i] = buf.readVarInt();
                d.status[i] = buf.readByte();
                d.reason[i] = buf.readByte();
                d.source[i] = buf.readVarInt() - 1;
                d.reasons[i] = buf.readByte();
                d.blockEntities[i] = buf.readVarInt();
                d.entities[i] = buf.readVarInt();
            }
            d.sources = dev.culprit.legacy.J21.clamp(buf.readVarInt(), 0, 100_000);
            d.sx = new int[d.sources];
            d.sz = new int[d.sources];
            d.slevel = new int[d.sources];
            d.sreason = new byte[d.sources];
            d.sowner = new String[d.sources];
            for (int s = 0; s < d.sources; s++) {
                d.sx[s] = buf.readVarInt();
                d.sz[s] = buf.readVarInt();
                d.slevel[s] = buf.readVarInt();
                d.sreason[s] = buf.readByte();
                d.sowner[s] = buf.readUtf(512);
            }
            data.dims.add(d);
        }
        return data;
    }
}
