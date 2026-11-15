package dev.culprit.port;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Minecraft 26.x's saved data type: an id, how to make one, and the codec it is saved with, over 1.21.1's data storage
 * (a factory and a file name; saved as the codec's value under "data"). This branch's own. Main's
 * {@code level.getDataStorage().computeIfAbsent(TYPE)} becomes {@code TYPE.get(level)}, its {@code get(TYPE)}
 * {@code TYPE.find(level)}, and a saved data class saves itself with {@code TYPE.save}.
 */
public final class SavedDataType<T extends SavedData> {
    private final ResourceLocation id;
    private final Supplier<T> constructor;
    private final Codec<T> codec;

    public SavedDataType(ResourceLocation id, Supplier<T> constructor, Codec<T> codec) {
        this.id = id;
        this.constructor = constructor;
        this.codec = codec;
    }

    public ResourceLocation id() {
        return this.id;
    }

    /** Its file's name in the level's data folder. */
    public String name() {
        return this.id.getNamespace() + "_" + this.id.getPath().replace('/', '_');
    }

    private SavedData.Factory<T> factory() {
        return new SavedData.Factory<>(this.constructor, (tag, registries) -> {
            var data = tag.get("data");
            if (data == null) {
                return this.constructor.get();
            }
            return this.codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), data).result().orElseGet(this.constructor);
        }, null);
    }

    /** The level's, made if it has none yet. */
    public T get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), name());
    }

    /** The level's, if it has one. */
    public @Nullable T find(ServerLevel level) {
        return level.getDataStorage().get(factory(), name());
    }

    /** Saves {@code data} into {@code tag} with its codec. */
    public CompoundTag save(T data, CompoundTag tag, HolderLookup.Provider registries) {
        this.codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), data).result().ifPresent(t -> tag.put("data", t));
        return tag;
    }
}
