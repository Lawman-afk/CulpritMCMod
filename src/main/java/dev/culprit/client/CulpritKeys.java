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

/**
 * Culprit's keys, under "Culprit" in Controls: F8 opens the scan menu, F9 the results. Minecraft binds neither (its
 * smooth camera, once F8, has no key since long ago); F7, the other free one near them, is the popular light-level
 * overlay mods' key. Both can be changed.
 */
public final class CulpritKeys {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Culprit.id("culprit"));
    public static final KeyMapping SCAN = new KeyMapping("key.culprit.scan", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F8, CATEGORY);
    public static final KeyMapping RESULTS = new KeyMapping("key.culprit.results", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F9, CATEGORY);

    private CulpritKeys() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(CATEGORY);
            e.register(SCAN);
            e.register(RESULTS);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            while (SCAN.consumeClick()) {
                if (mc.gui.screen() == null && mc.player != null) {
                    mc.gui.setScreen(new ScanScreen());
                }
            }
            while (RESULTS.consumeClick()) {
                if (mc.gui.screen() == null && mc.player != null) {
                    mc.gui.setScreen(new CulpritScreen());
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientReport.clear());
    }
}
