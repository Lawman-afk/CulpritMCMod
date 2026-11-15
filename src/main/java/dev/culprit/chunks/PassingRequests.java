package dev.culprit.chunks;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import dev.culprit.Culprit;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Who asked for a chunk that only the game's passing requests keep loaded (DESIGN §4.3). Code that reads a block in an
 * unloaded chunk makes the game load it there and then, on the server thread, and hold it for a tick; a mod doing so
 * every tick keeps the chunk loaded, and each first load is a stall. When such a request has to load a chunk in full
 * ({@code PassingRequestMixin}), the code that asked is noted ({@link Callers}) for as long as the chunk stays loaded,
 * in memory only. Requests for chunks already loaded are not walked.
 */
public final class PassingRequests {
    /** What any code reads a chunk through: passed to reach the code that asked. */
    private static final Set<String> WAY_THROUGH = Set.of("net.minecraft.world.level.Level", "net.minecraft.world.level.LevelReader",
            "net.minecraft.world.level.BlockGetter", "net.minecraft.world.level.LevelAccessor", "net.minecraft.world.level.CollisionGetter",
            "net.minecraft.world.level.SignalGetter", "net.minecraft.world.level.ServerLevelAccessor",
            "net.minecraft.world.level.CommonLevelAccessor", "net.minecraft.world.level.LevelHeightAccessor",
            "net.minecraft.world.level.BlockAndTintGetter", "net.minecraft.world.level.EntityGetter",
            "net.minecraft.world.level.LevelSimulatedReader", "net.minecraft.world.level.LevelWriter",
            "net.minecraft.world.level.chunk.ChunkSource", "net.minecraft.server.MinecraftServer", "net.minecraft.server.TickTask");
    private static final int MOST = 4096;
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<String>> ASKED = new WeakHashMap<>();

    private PassingRequests() {}

    /** A request for a chunk in full is loading it now (it was not loaded so): who asked. On the server thread. */
    public static void loading(ServerLevel level, long chunk) {
        try {
            String who = Callers.of(PassingRequests::wayThrough);
            if (who == null) {
                return;
            }
            Long2ObjectOpenHashMap<String> asked = ASKED.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
            if (asked.size() >= MOST) {
                forgetUnloaded(level, asked);
            }
            if (asked.size() < MOST) {
                asked.put(chunk, who);
            }
        } catch (RuntimeException e) {
            Culprit.LOGGER.debug("Culprit: could not note who asked for a chunk", e);
        }
    }

    private static boolean wayThrough(String name) {
        return name.startsWith("net.minecraft.server.level.") || name.startsWith("net.minecraft.util.thread.")
                || name.startsWith("net.minecraftforge.common.extensions.") || WAY_THROUGH.contains(name);
    }

    /** Who asked for a chunk that is loaded now, or null when no one was noted. */
    static @Nullable String of(ServerLevel level, long chunk) {
        Long2ObjectOpenHashMap<String> asked = ASKED.get(level);
        return asked == null ? null : asked.get(chunk);
    }

    /** The notes for chunks no longer loaded in full go (the census does it as it looks, and a full table). */
    static void forgetUnloaded(ServerLevel level, @Nullable Long2ObjectOpenHashMap<String> asked) {
        if (asked == null && (asked = ASKED.get(level)) == null) {
            return;
        }
        var chunkMap = level.getChunkSource().chunkMap;
        asked.keySet().removeIf(chunk -> {
            ChunkHolder holder = ((dev.culprit.mixin.ChunkMapAccessor) chunkMap).culprit$visibleChunkMap().get(chunk);
            return holder == null || holder.getTicketLevel() > dev.culprit.net.ChunkData.FULL_LEVEL;
        });
    }

    /** A passing request's owner in words: "Mekanism: asked for it", or the game's "passing requests". */
    static String describe(@Nullable String by) {
        return by == null ? ChunkCensus.PASSING : Callers.describe(by) + ": asked for it";
    }
}
