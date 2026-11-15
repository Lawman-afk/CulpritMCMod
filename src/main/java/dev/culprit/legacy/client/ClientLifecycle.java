package dev.culprit.legacy.client;

import dev.culprit.legacy.net.Network;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * The client half of {@link dev.culprit.legacy.Lifecycle}: the client's own player for the payloads' handlers, and
 * NeoForge's HUD layer registration posted on the mod bus as Forge registers its overlays.
 */
public final class ClientLifecycle {
    private ClientLifecycle() {}

    public static void install(IEventBus modBus) {
        Network.clientPlayer = () -> Minecraft.getInstance().player;
        modBus.addListener((RegisterGuiOverlaysEvent event) -> modBus.post(new RegisterGuiLayersEvent(event)));
    }
}
