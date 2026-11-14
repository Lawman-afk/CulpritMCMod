package dev.culprit;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.culprit.command.CulpritCommand;
import dev.culprit.net.CulpritNetwork;
import dev.culprit.scan.TickScan;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Culprit: finds what is lagging the game. It times the server's ticks thing by thing, the client's frames thing by
 * thing, and shows what keeps each chunk loaded, all on the world itself (DESIGN.md §1, §4). It adds no items and no
 * blocks. The client side is {@link CulpritClient}.
 */
@Mod(Culprit.MODID)
public class Culprit {
    public static final String MODID = "culprit";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Culprit(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SYNCED, dev.culprit.config.CulpritServerConfig.SPEC);
        TickScan.register();
        CulpritCommand.register();
        CulpritNetwork.register(modBus);
        dev.culprit.net.LoadSender.register();             // the HUD line's numbers, for players who ask
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
