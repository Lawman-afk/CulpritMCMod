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
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, dev.culprit.config.CulpritClientConfig.SPEC);
        // The settings in the mod list (Config): this player's own, and the server's when it is their own world.
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                net.neoforged.neoforge.client.gui.ConfigurationScreen::new);
        DevRuns.register();   // the dev test runs leave the user's mouse and windows alone
        ClientNetwork.register(modBus);
        CulpritKeys.register(modBus);
        Overlay.register();
        FrameEvents.register();                               // mods' frame events, timed during a frame scan
        // What the crosshair rests on, read out under it while the results are shown on the world.
        modBus.addListener((RegisterGuiLayersEvent event) -> {
            event.registerAbove(VanillaGuiLayers.CROSSHAIR, Culprit.id("readout"), Overlay::drawReadout);
            event.registerAbove(VanillaGuiLayers.CROSSHAIR, Culprit.id("hud_line"), dev.culprit.client.HudLine::draw);
        });
        // The HUD line asks a server for its numbers on joining, and again when its setting changes.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) -> dev.culprit.client.HudLine.sync());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> dev.culprit.client.HudLine.left());
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading e) -> {
            if (e.getConfig().getSpec() == dev.culprit.config.CulpritClientConfig.SPEC) {
                net.minecraft.client.Minecraft.getInstance().execute(dev.culprit.client.HudLine::sync);
            }
        });
    }
}
