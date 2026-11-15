package dev.culprit.legacy;

import dev.culprit.legacy.net.Network;
import dev.culprit.legacy.net.RegisterPayloadHandlersEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Starts this branch's stand-ins for NeoForge's systems as the mod is made (the port calls {@link #install} first thing
 * in the mod's constructor): once every part of the mod has added its listeners, NeoForge's registration events posted on
 * the mod bus at common setup (the payloads, then the mod's one Forge channel opened; the chunk ticket controllers), and
 * the client's half on a client.
 */
public final class Lifecycle {
    private Lifecycle() {}

    private static boolean installed;

    public static void install(IEventBus modBus, ModContainer container) {
        if (installed) {
            return;
        }
        installed = true;
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            modBus.post(new RegisterPayloadHandlersEvent());
            Network.open(Network.version());
            modBus.post(new RegisterTicketControllersEvent());
            RegisterTicketControllersEvent.done();
        }));
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.culprit.legacy.client.ClientLifecycle.install(modBus));
    }
}
