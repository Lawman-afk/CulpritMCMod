package dev.culprit.chunks;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.culprit.Culprit;
import dev.culprit.net.ChunkData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.saveddata.SavedData;
import dev.culprit.port.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Who forced each chunk the game's own way (DESIGN §4.3). The /forceload command and a mod calling the game's
 * {@code setChunkForced} make the same ticket, which carries no owner; so each change is recorded as it happens
 * ({@code ForcedChunkMixin}), per level, and saved with the world so it holds across restarts: who ran /forceload
 * ({@code ForceLoadCommandMixin} says), or the mod whose code asked ({@link Callers}), or the game itself (its test
 * structures). A chunk forced before Culprit was installed has no record.
 */
public final class ForcedBy extends SavedData {
    private record Entry(int x, int z, String by) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Entry::x),
                Codec.INT.fieldOf("z").forGetter(Entry::z),
                Codec.STRING.fieldOf("by").forGetter(Entry::by)).apply(i, Entry::new));
    }

    public static final Codec<ForcedBy> CODEC = Entry.CODEC.listOf().xmap(ForcedBy::new, ForcedBy::entries);
    public static final SavedDataType<ForcedBy> TYPE = new SavedDataType<>(Culprit.id("forced_chunks"), ForcedBy::new, CODEC);

    private static final String COMMAND = "cmd:";
    /** Who is running /forceload now, while it runs (on the server thread), else null. */
    private static @Nullable String commandBy;

    private final Long2ObjectOpenHashMap<String> by = new Long2ObjectOpenHashMap<>();

    public ForcedBy() {}

    @Override
    public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag) {
        return TYPE.save(this, tag);
    }

    private ForcedBy(List<Entry> entries) {
        for (Entry entry : entries) {
            by.put(ChunkPos.asLong(entry.x, entry.z), entry.by);
        }
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>(by.size());
        for (Long2ObjectMap.Entry<String> entry : by.long2ObjectEntrySet()) {
            out.add(new Entry(ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()), entry.getValue()));
        }
        return out;
    }

    /** The record for one chunk, or null. */
    public @Nullable String get(long chunk) {
        return by.get(chunk);
    }

    /** The record for a chunk of a level, or null when there is none (never forced so, or before Culprit was there). */
    public static @Nullable String of(ServerLevel level, long chunk) {
        ForcedBy record = TYPE.find(level);
        return record == null ? null : record.by.get(chunk);
    }

    // ---- recording ----

    public static void commandStarts(CommandSourceStack source) {
        try {
            commandBy = who(source);
        } catch (RuntimeException e) {
            commandBy = "someone";
        }
    }

    public static void commandEnds() {
        commandBy = null;
    }

    /** A chunk's forced ticket was added or taken away (the game's own call said it changed). */
    public static void changed(ServerLevel level, ChunkPos pos, boolean forced) {
        try {
            long key = pos.toLong();
            if (forced) {
                // (past the forcing itself: the server level and its chunk source)
                String who = commandBy != null ? COMMAND + commandBy : Callers.of(name -> name.startsWith("net.minecraft.server.level."));
                if (who != null) {
                    ForcedBy record = TYPE.get(level);
                    record.by.put(key, who);
                    record.setDirty();
                }
            } else {
                ForcedBy record = TYPE.find(level);
                if (record != null && record.by.remove(key) != null) {
                    record.setDirty();
                }
            }
        } catch (RuntimeException e) {
            Culprit.LOGGER.debug("Culprit: could not record who forced a chunk", e);
        }
    }

    /** Who ran a command: a player or the entity it runs as, a command block, the server (its console or a function). */
    private static String who(CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (entity != null) {
            return entity.getName().getString();
        }
        BlockPos at = BlockPos.containing(source.getPosition());
        if (source.getLevel().getBlockState(at).getBlock() instanceof CommandBlock) {
            return "a command block at " + at.getX() + " " + at.getY() + " " + at.getZ();
        }
        return source.source instanceof MinecraftServer ? "the server" : source.getTextName();
    }

    // ---- reading ----

    /** The reason a forced chunk is loaded, by its record: a mod's code made it a mod's, the game's own "other". */
    static byte reason(@Nullable String by) {
        return by == null || by.startsWith(COMMAND) ? ChunkData.FORCED : by.startsWith(Callers.MOD) ? ChunkData.MODS : ChunkData.OTHER;
    }

    /** A forced chunk's owner in words: "/forceload by Steve", "Mekanism: forced chunk", "/forceload or a mod". */
    static String describe(@Nullable String by) {
        if (by == null) {
            return "/forceload or a mod";
        }
        if (by.startsWith(COMMAND)) {
            return "/forceload by " + by.substring(COMMAND.length());
        }
        if (by.equals(Callers.GAME + "StructureUtils")) {
            return "the game's test structures";
        }
        return Callers.describe(by) + ": forced chunk";
    }
}
