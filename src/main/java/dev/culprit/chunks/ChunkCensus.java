package dev.culprit.chunks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.culprit.Culprit;
import dev.culprit.mixin.ChunkMapAccessor;
import dev.culprit.net.ChunkData;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import net.neoforged.neoforge.common.world.chunk.TicketSet;

/**
 * The chunk inspector (DESIGN §4.3): every loaded chunk of every dimension, and why. Each chunk is loaded by the
 * loading tickets within reach of it (a ticket of level L at a chunk makes the chunks d away level L + d; up to 33 they
 * are playable); its reason is the strongest one's. A ticket's owner is named: the players standing in its chunk, the
 * mod and block or entity behind a NeoForge chunk loader (from the world's own record of them, as it saves them),
 * /forceload, a portal, an ender pearl and who threw it, the world spawn. Taken on demand, on the server thread.
 */
public final class ChunkCensus {
    /** The owner named for the game's passing requests for a chunk (the "unknown" tickets, gone a tick later). */
    public static final String PASSING = "passing requests";

    private ChunkCensus() {}

    private record Source(long chunk, int level, byte reason, String owner) {}

    public static ChunkData take(MinecraftServer server) {
        ChunkData data = new ChunkData();
        data.takenAtMillis = System.currentTimeMillis();
        int budget = ChunkData.MAX_CHUNKS;
        for (ServerLevel level : server.getAllLevels()) {
            ChunkData.Dim dim = dimension(level, budget);
            budget -= dim.chunks;
            data.dims.add(dim);
        }
        return data;
    }

    private static ChunkData.Dim dimension(ServerLevel level, int budget) {
        TicketStorage storage = level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
        Long2ObjectMap<List<String>> modOwners = modOwners(level, storage);
        var holders = ((ChunkMapAccessor) level.getChunkSource().chunkMap).culprit$visibleChunkMap();

        // The loading tickets, at the chunks they sit on (each is a loaded chunk itself).
        List<Source> sources = new ArrayList<>();
        for (Long2ObjectMap.Entry<ChunkHolder> entry : holders.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            for (Ticket ticket : storage.getTickets(key)) {
                TicketType type = ticket.getType();
                if (type.doesLoad()) {
                    byte reason = reasonOf(type);
                    sources.add(new Source(key, ticket.getTicketLevel(), reason, ownerOf(level, key, type, reason, modOwners)));
                }
            }
        }
        // What is in each chunk: its block entities, and the entities counted by chunk.
        Long2IntOpenHashMap entities = new Long2IntOpenHashMap();
        for (Entity entity : level.getAllEntities()) {
            entities.addTo(entity.chunkPosition().pack(), 1);
        }

        ChunkData.Dim d = new ChunkData.Dim();
        d.dim = level.dimension().identifier().toString();
        List<long[]> rows = new ArrayList<>();
        for (Long2ObjectMap.Entry<ChunkHolder> entry : holders.long2ObjectEntrySet()) {
            FullChunkStatus status = entry.getValue().getFullStatus();
            if (status == FullChunkStatus.INACCESSIBLE) {
                d.edges++;
                continue;
            }
            d.total++;
            if (rows.size() >= budget) {
                continue;
            }
            long key = entry.getLongKey();
            int x = ChunkPos.getX(key), z = ChunkPos.getZ(key);
            int best = -1, bestLevel = Integer.MAX_VALUE, reasons = 0;
            for (int s = 0; s < sources.size(); s++) {
                Source source = sources.get(s);
                int distance = Math.max(Math.abs(ChunkPos.getX(source.chunk) - x), Math.abs(ChunkPos.getZ(source.chunk) - z));
                int lvl = source.level + distance;
                if (lvl > ChunkData.FULL_LEVEL) {
                    continue;
                }
                reasons |= 1 << source.reason;
                if (lvl < bestLevel || (lvl == bestLevel && best >= 0 && priority(source.reason) > priority(sources.get(best).reason))) {
                    best = s;
                    bestLevel = lvl;
                }
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            byte st = status == FullChunkStatus.ENTITY_TICKING ? ChunkData.ENTITY_TICKING
                    : status == FullChunkStatus.BLOCK_TICKING ? ChunkData.BLOCK_TICKING : ChunkData.BORDER;
            rows.add(new long[]{x, z, st, best < 0 ? ChunkData.OTHER : sources.get(best).reason, best, reasons,
                    chunk == null ? 0 : chunk.getBlockEntities().size(), entities.get(key)});
        }
        d.chunks = rows.size();
        d.x = new int[d.chunks];
        d.z = new int[d.chunks];
        d.status = new byte[d.chunks];
        d.reason = new byte[d.chunks];
        d.source = new int[d.chunks];
        d.reasons = new byte[d.chunks];
        d.blockEntities = new int[d.chunks];
        d.entities = new int[d.chunks];
        for (int i = 0; i < d.chunks; i++) {
            long[] r = rows.get(i);
            d.x[i] = (int) r[0];
            d.z[i] = (int) r[1];
            d.status[i] = (byte) r[2];
            d.reason[i] = (byte) r[3];
            d.source[i] = (int) r[4];
            d.reasons[i] = (byte) r[5];
            d.blockEntities[i] = (int) r[6];
            d.entities[i] = (int) r[7];
        }
        d.sources = sources.size();
        d.sx = new int[d.sources];
        d.sz = new int[d.sources];
        d.slevel = new int[d.sources];
        d.sreason = new byte[d.sources];
        d.sowner = new String[d.sources];
        for (int s = 0; s < d.sources; s++) {
            Source source = sources.get(s);
            d.sx[s] = ChunkPos.getX(source.chunk);
            d.sz[s] = ChunkPos.getZ(source.chunk);
            d.slevel[s] = source.level;
            d.sreason[s] = source.reason;
            d.sowner[s] = source.owner;
        }
        return d;
    }

    /** On a tie, the reason named: a player's surroundings are expected to be loaded, a mod's or a forced chunk not. */
    private static int priority(byte reason) {
        return switch (reason) {
            case ChunkData.MODS -> 6;
            case ChunkData.FORCED -> 5;
            case ChunkData.PEARLS -> 4;
            case ChunkData.PORTALS -> 3;
            case ChunkData.OTHER -> 2;
            case ChunkData.SPAWN -> 1;
            default -> 0;
        };
    }

    static byte reasonOf(TicketType type) {
        if (type == TicketType.PLAYER_LOADING || type == TicketType.PLAYER_SIMULATION) {
            return ChunkData.PLAYERS;
        }
        if (type == TicketType.FORCED) {
            return ChunkData.FORCED;
        }
        if (type == TicketType.PORTAL) {
            return ChunkData.PORTALS;
        }
        if (type == TicketType.ENDER_PEARL) {
            return ChunkData.PEARLS;
        }
        if (type == TicketType.PLAYER_SPAWN || type == TicketType.SPAWN_SEARCH) {
            return ChunkData.SPAWN;
        }
        Identifier id = BuiltInRegistries.TICKET_TYPE.getKey(type);
        return id != null && !id.getNamespace().equals("minecraft") ? ChunkData.MODS : ChunkData.OTHER;
    }

    /** Who or what is behind a ticket, in a few words. */
    private static String ownerOf(ServerLevel level, long chunk, TicketType type, byte reason, Long2ObjectMap<List<String>> modOwners) {
        Identifier id = BuiltInRegistries.TICKET_TYPE.getKey(type);
        String typeName = id == null ? "?" : id.toString();
        int cx = ChunkPos.getX(chunk), cz = ChunkPos.getZ(chunk);
        return switch (reason) {
            case ChunkData.PLAYERS -> {
                // A player's area is held by tickets across it, not one at their feet: the ticket is the nearest player's.
                ServerPlayer nearest = null;
                int best = Integer.MAX_VALUE;
                for (ServerPlayer player : level.players()) {
                    int d = Math.max(Math.abs(player.chunkPosition().x() - cx), Math.abs(player.chunkPosition().z() - cz));
                    if (d < best) {
                        best = d;
                        nearest = player;
                    }
                }
                yield nearest == null ? "a player" : nearest.getName().getString();
            }
            case ChunkData.FORCED -> "/forceload";
            case ChunkData.PORTALS -> "a portal";
            case ChunkData.PEARLS -> {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof ThrownEnderpearl pearl && pearl.chunkPosition().x() == cx && pearl.chunkPosition().z() == cz
                            && pearl.getOwner() instanceof ServerPlayer thrower) {
                        yield "an ender pearl of " + thrower.getName().getString();
                    }
                }
                yield "an ender pearl";
            }
            case ChunkData.SPAWN -> "the world spawn";
            case ChunkData.MODS -> {
                List<String> owners = modOwners.get(chunk);
                yield owners != null && !owners.isEmpty() && typeName.startsWith("neoforge:") && !typeName.equals("neoforge:generate_forced")
                        ? String.join("; ", owners) : typeName.equals("neoforge:generate_forced") ? "chunks being made for a mod" : typeName;
            }
            // The game asked for the chunk just now (and lets go of it a tick later): no loader to act on.
            default -> typeName.equals("minecraft:dragon") ? "the dragon fight" : type == TicketType.UNKNOWN ? PASSING : typeName;
        };
    }

    /**
     * The NeoForge chunk loaders of a level, by chunk: their controller (a mod's id and name) and the block or entity
     * that owns each. Read the way the world saves them (the ticket storage's own codec), which is public.
     */
    private static Long2ObjectMap<List<String>> modOwners(ServerLevel level, TicketStorage storage) {
        Long2ObjectMap<List<String>> owners = new Long2ObjectOpenHashMap<>();
        try {
            Tag tag = TicketStorage.CODEC.encodeStart(NbtOps.INSTANCE, storage).result().orElse(null);
            if (!(tag instanceof CompoundTag compound) || !(compound.get("neo_ticket_data") instanceof ListTag list)) {
                return owners;
            }
            for (ForcedChunkManager.OwnedChunks owned : ForcedChunkManager.OwnedChunks.CODEC.listOf().parse(NbtOps.INSTANCE, list).result()
                    .orElse(List.of())) {
                String controller = owned.controller().toString();
                for (var entry : owned.blockChunks().entrySet()) {
                    BlockPos pos = entry.getKey();
                    String block = level.isLoaded(pos) ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString() : "a block";
                    add(owners, entry.getValue(), controller + " (" + block + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ")");
                }
                for (var entry : owned.entityChunks().entrySet()) {
                    UUID uuid = entry.getKey();
                    Entity entity = level.getEntity(uuid);
                    String who = entity == null ? "an entity not loaded" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + " at "
                            + entity.blockPosition().getX() + " " + entity.blockPosition().getY() + " " + entity.blockPosition().getZ();
                    add(owners, entry.getValue(), controller + " (" + who + ")");
                }
            }
        } catch (RuntimeException e) {
            Culprit.LOGGER.debug("Culprit: could not read the mods' chunk loaders", e);
        }
        return owners;
    }

    private static void add(Long2ObjectMap<List<String>> owners, TicketSet set, String owner) {
        for (LongSet chunks : List.of(set.normal(), set.naturalSpawning())) {
            for (long chunk : chunks) {
                List<String> list = owners.computeIfAbsent(chunk, k -> new ArrayList<>());
                if (!list.contains(owner)) {
                    list.add(owner);
                }
            }
        }
    }
}
