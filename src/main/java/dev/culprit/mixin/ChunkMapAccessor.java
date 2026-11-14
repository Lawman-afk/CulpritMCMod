package dev.culprit.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chunk map's loaded chunks, for the chunk inspector (read only, on the server thread). */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Accessor("visibleChunkMap")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> culprit$visibleChunkMap();
}
