package dev.culprit.chunks;

import java.util.ArrayList;
import java.util.List;

import dev.culprit.Culprit;
import dev.culprit.mixin.ChunkMapAccessor;
import dev.culprit.net.ChunkData;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.ChunkPos;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.world.ForgeChunkManager;


/**
 * The chunk inspector (DESIGN §4.3): every loaded chunk of every dimension, and why. Each chunk is loaded by the
 * loading tickets within reach of it (a ticket of level L at a chunk makes the chunks d away level L + d; up to 33 they
 * are playable); its reason is the strongest one's. A ticket's owner is named: the nearest player, the mod behind a
 * NeoForge chunk loader and the block, entity, player or team that owns it (from the world's own record of them, as it
 * saves them), a mod's own kind of ticket, who forced a chunk (/forceload by whom, or a mod's code: {@link ForcedBy}),
 * a portal, an ender pearl and who threw it, the world spawn. Taken on demand, on the server thread.
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
        var tickets = ((dev.culprit.mixin.DistanceManagerAccessor) level.getChunkSource().chunkMap.getDistanceManager()).culprit$tickets();
        Long2ObjectMap<List<String>> modOwners = modOwners(level);
        var holders = ((ChunkMapAccessor) level.getChunkSource().chunkMap).culprit$visibleChunkMap();
        PassingRequests.forgetUnloaded(level, null);

        // The loading tickets, at the chunks they sit on (each is a loaded chunk itself).
        List<Source> sources = new ArrayList<>();
        for (Long2ObjectMap.Entry<ChunkHolder> entry : holders.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            var held = tickets.get(key);
            for (Ticket<?> ticket : held == null ? List.<Ticket<?>>of() : held) {
                TicketType<?> type = ticket.getType();
                if (type == TicketType.FORCED) {
                    // The ticket /forceload and the game's setChunkForced share: whose it is, from Culprit's record.
                    String by = ForcedBy.of(level, key);
                    sources.add(new Source(key, ticket.getTicketLevel(), ForcedBy.reason(by), ForcedBy.describe(by)));
                } else {
                    byte reason = reasonOf(type);
                    sources.add(new Source(key, ticket.getTicketLevel(), reason, ownerOf(level, key, type, reason, modOwners)));
                }
            }
        }
        // What is in each chunk: its block entities, and the entities counted by chunk.
        Long2IntOpenHashMap entities = new Long2IntOpenHashMap();
        for (Entity entity : level.getAllEntities()) {
            entities.addTo(entity.chunkPosition().toLong(), 1);
        }

        ChunkData.Dim d = new ChunkData.Dim();
        d.dim = level.dimension().location().toString();
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
                if (source.reason != ChunkData.REQUESTS) {
                    reasons |= 1 << source.reason;          // (a passing request counts only where it is all there is)
                }
                if (lvl < bestLevel || (lvl == bestLevel && best >= 0 && priority(source.reason) > priority(sources.get(best).reason))) {
                    best = s;
                    bestLevel = lvl;
                }
            }
            if (best >= 0 && sources.get(best).reason == ChunkData.REQUESTS) {
                reasons |= 1 << ChunkData.REQUESTS;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            byte st = status == FullChunkStatus.ENTITY_TICKING ? ChunkData.ENTITY_TICKING
                    : status == FullChunkStatus.BLOCK_TICKING ? ChunkData.BLOCK_TICKING : ChunkData.BORDER;
            rows.add(new long[]{x, z, st, best < 0 ? ChunkData.OTHER : sources.get(best).reason, best, reasons,
                    chunk == null ? 0 : chunk.getBlockEntities().size(), entities.get(key)});
        }
        // A passing request where something else keeps the chunk loaded is not why it is (a chunk of a player's area that
        // something read just now is the player's): only those that are all a chunk has are kept.
        boolean[] needed = new boolean[sources.size()];
        for (long[] r : rows) {
            if (r[4] >= 0) {
                needed[(int) r[4]] = true;
            }
        }
        int[] newIndex = new int[sources.size()];
        List<Source> kept = new ArrayList<>(sources.size());
        for (int s = 0; s < sources.size(); s++) {
            newIndex[s] = sources.get(s).reason != ChunkData.REQUESTS || needed[s] ? kept.size() : -1;
            if (newIndex[s] >= 0) {
                kept.add(sources.get(s));
            }
        }
        for (long[] r : rows) {
            r[4] = r[4] >= 0 ? newIndex[(int) r[4]] : -1;
        }
        sources = kept;

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
            case ChunkData.REQUESTS -> -1;                      // (asked for just now: whatever else holds it is why)
            case ChunkData.MODS -> 6;
            case ChunkData.FORCED -> 5;
            case ChunkData.PEARLS -> 4;
            case ChunkData.PORTALS -> 3;
            case ChunkData.OTHER -> 2;
            case ChunkData.SPAWN -> 1;
            default -> 0;
        };
    }

    static byte reasonOf(TicketType<?> type) {
        if (type == TicketType.PLAYER) {
            return ChunkData.PLAYERS;
        }
        if (type == TicketType.FORCED) {
            return ChunkData.FORCED;
        }
        if (type == TicketType.PORTAL) {
            return ChunkData.PORTALS;
        }
        if (type == TicketType.START) {
            return ChunkData.SPAWN;
        }
        if (type == TicketType.UNKNOWN) {
            return ChunkData.REQUESTS;
        }
        // 1.21.1's ticket types are named, not registered: the game's own by plain names, NeoForge's and mods' as they choose.
        return VANILLA.contains(type.toString()) ? ChunkData.OTHER : ChunkData.MODS;
    }

    private static final java.util.Set<String> VANILLA = java.util.Set.of("start", "dragon", "player", "forced", "light", "portal",
            "post_teleport", "unknown");

    /** Who or what is behind a ticket, in a few words. */
    private static String ownerOf(ServerLevel level, long chunk, TicketType<?> type, byte reason, Long2ObjectMap<List<String>> modOwners) {
        String typeName = type.toString();
        int cx = ChunkPos.getX(chunk), cz = ChunkPos.getZ(chunk);
        return switch (reason) {
            case ChunkData.PLAYERS -> {
                // A player's area is held by tickets across it, not one at their feet: the ticket is the nearest player's.
                ServerPlayer nearest = null;
                int best = Integer.MAX_VALUE;
                for (ServerPlayer player : level.players()) {
                    int d = Math.max(Math.abs(player.chunkPosition().x - cx), Math.abs(player.chunkPosition().z - cz));
                    if (d < best) {
                        best = d;
                        nearest = player;
                    }
                }
                yield nearest == null ? "a player" : nearest.getName().getString();
            }
            case ChunkData.PORTALS -> "a portal";
            // 1.21.1 keeps the spawn chunks loaded (26.x has none): named apart from their reason, "the world spawn".
            case ChunkData.SPAWN -> "the spawn chunks";
            // The game asked for the chunk just now (and lets go of it a tick later): who asked, if a request loaded it.
            case ChunkData.REQUESTS -> PassingRequests.describe(PassingRequests.of(level, chunk));
            case ChunkData.MODS -> {
                // A NeoForge chunk loader is the mod's and the block or entity that owns it; a mod's own kind of ticket its.
                List<String> owners = modOwners.get(chunk);
                if (typeName.startsWith("forge:")) {
                    yield owners != null && !owners.isEmpty() ? String.join("; ", owners) : "a mod's chunk loader";
                }
                yield typeName;
            }
            default -> typeName.equals("dragon") ? "the dragon fight" : typeName;
        };
    }

    /**
     * The NeoForge chunk loaders of a level, by chunk: their controller (a mod's id and name) and the block or entity
     * that owns each, as 1.21.1 keeps them in the level's forced-chunk data.
     */
    private static Long2ObjectMap<List<String>> modOwners(ServerLevel level) {
        Long2ObjectMap<List<String>> owners = new Long2ObjectOpenHashMap<>();
        try {
            var data = level.getDataStorage().get(net.minecraft.world.level.ForcedChunksSavedData::load, "chunks");
            if (data == null) {
                return owners;
            }
            // "FTB Chunks: team Steve's Party", "Mekanism: Digital Miner at 10 64 -5": the mod, then what owns the loader.
            java.util.function.BiFunction<ResourceLocation, Object, String> block = (id, owner) ->
                    OwnerNames.mod(id.getNamespace()) + ": " + OwnerNames.block(level, (net.minecraft.core.BlockPos) owner);
            java.util.function.BiFunction<ResourceLocation, Object, String> entity = (id, owner) ->
                    OwnerNames.mod(id.getNamespace()) + ": " + OwnerNames.uuid(level.getServer(), level, (java.util.UUID) owner);
            add(owners, data.getBlockForcedChunks().getChunks(), block);
            add(owners, data.getBlockForcedChunks().getTickingChunks(), block);
            add(owners, data.getEntityForcedChunks().getChunks(), entity);
            add(owners, data.getEntityForcedChunks().getTickingChunks(), entity);
        } catch (RuntimeException e) {
            Culprit.LOGGER.debug("Culprit: could not read the mods' chunk loaders", e);
        }
        return owners;
    }

    private static void add(Long2ObjectMap<List<String>> owners, java.util.Map<?, LongSet> byOwner,
                            java.util.function.BiFunction<ResourceLocation, Object, String> name) {
        for (var entry : byOwner.entrySet()) {
            var key = (dev.culprit.mixin.TicketOwnerAccessor) entry.getKey();
            String owner = name.apply(new ResourceLocation(key.culprit$modId(), "chunks"), key.culprit$owner());
            for (long chunk : entry.getValue()) {
                List<String> list = owners.computeIfAbsent(chunk, k -> new ArrayList<>());
                if (!list.contains(owner)) {
                    list.add(owner);
                }
            }
        }
    }
}
