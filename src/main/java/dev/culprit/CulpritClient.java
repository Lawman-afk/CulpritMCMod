package dev.culprit;

import dev.culprit.client.ClientNetwork;
import dev.culprit.client.CulpritKeys;
import dev.culprit.client.DevRuns;
import dev.culprit.client.Overlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Client-only entry point; never loaded on a dedicated server. */
@Mod(value = Culprit.MODID, dist = Dist.CLIENT)
public class CulpritClient {
    public CulpritClient(IEventBus modBus, ModContainer container) {
        DevRuns.register();   // the dev test runs leave the user's mouse and windows alone
        ClientNetwork.register(modBus);
        CulpritKeys.register(modBus);
        Overlay.register();
    }
}
