package dev.culprit.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.culprit.Culprit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

/** Culprit's keys: F7 opens the Culprit screen (rebindable in Controls). */
public final class CulpritKeys {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Culprit.id("culprit"));
    public static final KeyMapping OPEN = new KeyMapping("key.culprit.open", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F7, CATEGORY);

    private CulpritKeys() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(CATEGORY);
            e.register(OPEN);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (mc.gui.screen() == null && mc.player != null) {
                    mc.gui.setScreen(new CulpritScreen());
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientReport.clear());
    }
}
