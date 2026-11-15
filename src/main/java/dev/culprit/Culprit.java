package dev.culprit;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.culprit.command.CulpritCommand;
import dev.culprit.net.CulpritNetwork;
import dev.culprit.scan.TickScan;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Culprit: finds what is lagging the game. It times the server's ticks thing by thing, the client's frames thing by
 * thing, and shows what keeps each chunk loaded, all on the world itself (DESIGN.md §1, §4). It adds no items and no
 * blocks. The client side is {@link CulpritClient}.
 */
@Mod(Culprit.MODID)
public class Culprit {
    public static final String MODID = "culprit";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Culprit() {
        IEventBus modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        ModContainer modContainer = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();
        dev.culprit.legacy.Lifecycle.install(modBus, modContainer);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, dev.culprit.config.CulpritServerConfig.SPEC);
        TickScan.register();
        CulpritCommand.register();
        CulpritNetwork.register(modBus);
        dev.culprit.net.LoadSender.register();             // the HUD line's numbers, for players who ask
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> new CulpritClient(modBus, modContainer));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
