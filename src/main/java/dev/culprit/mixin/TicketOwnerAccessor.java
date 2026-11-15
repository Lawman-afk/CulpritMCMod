package dev.culprit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A NeoForge chunk loader's owner on 1.21.1 (its controller's id, and the block position or id that owns it), for the
 * chunk inspector: the class is not public there (this branch's own; 26.x saves them in a public record).
 */
@Mixin(value = net.minecraftforge.common.world.ForgeChunkManager.TicketOwner.class, remap = false)
public interface TicketOwnerAccessor {
    @Accessor("modId")
    String culprit$modId();

    @Accessor("owner")
    Comparable<?> culprit$owner();
}
