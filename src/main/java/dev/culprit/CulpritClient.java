package dev.culprit;

import dev.culprit.client.ClientNetwork;
import dev.culprit.client.CulpritKeys;
import dev.culprit.client.DevRuns;
import dev.culprit.client.FrameEvents;
import dev.culprit.client.Overlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Client-only entry point; never loaded on a dedicated server. */
@Mod(value = Culprit.MODID, dist = Dist.CLIENT)
public class CulpritClient {
    public CulpritClient(IEventBus modBus, ModContainer container) {
        DevRuns.register();   // the dev test runs leave the user's mouse and windows alone
        ClientNetwork.register(modBus);
        CulpritKeys.register(modBus);
        Overlay.register();
        FrameEvents.register();                               // mods' frame events, timed during a frame scan
        // What the crosshair rests on, read out under it while the results are shown on the world.
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAbove(VanillaGuiLayers.CROSSHAIR, Culprit.id("readout"), Overlay::drawReadout));
    }
}
