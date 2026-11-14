package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.culprit.chunks.ForcedBy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.ForceLoadCommand;
import net.minecraft.server.level.ColumnPos;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Who runs /forceload add or remove, for the record of forced chunks (DESIGN §4.3): a player, a command block, the
 * server. Not needed to play, so a game where it cannot apply starts without it.
 */
@Mixin(ForceLoadCommand.class)
abstract class ForceLoadCommandMixin {
    @WrapMethod(method = "changeForceLoad", require = 0)
    private static int culprit$whoForces(CommandSourceStack source, ColumnPos from, ColumnPos to, boolean add, Operation<Integer> original) {
        ForcedBy.commandStarts(source);
        try {
            return original.call(source, from, to, add);
        } finally {
            ForcedBy.commandEnds();
        }
    }
}
