package dev.culprit.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.1's loading tickets by chunk, for the chunk inspector (this branch's own: 26.x keeps them in its ticket storage). */
@Mixin(DistanceManager.class)
public interface DistanceManagerAccessor {
    @Accessor("tickets")
    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> culprit$tickets();
}
