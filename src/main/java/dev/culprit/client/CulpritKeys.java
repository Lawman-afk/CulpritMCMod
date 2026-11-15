package dev.culprit.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.culprit.Culprit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;

/**
 * Culprit's keys, under "Culprit" in Controls: F8 opens the scan menu, F9 the results. Minecraft binds neither (its
 * smooth camera, once F8, has no key since long ago); F7, the other free one near them, is the popular light-level
 * overlay mods' key. Both can be changed.
 */
public final class CulpritKeys {
    public static final String CATEGORY = dev.culprit.port.input.KeyEvent.category(Culprit.id("culprit"));
    public static final KeyMapping SCAN = new KeyMapping("key.culprit.scan", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            InputConstants.KEY_F8, CATEGORY);
    public static final KeyMapping RESULTS = new KeyMapping("key.culprit.results", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            InputConstants.KEY_F9, CATEGORY);
    /** The HUD line on or off: no key at first (the setting does it too). */
    public static final KeyMapping HUD = new KeyMapping("key.culprit.hud", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), CATEGORY);

    private CulpritKeys() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {

            e.register(SCAN);
            e.register(RESULTS);
            e.register(HUD);
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent e) -> {if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (SCAN.consumeClick()) {
                if (mc.screen == null && mc.player != null) {
                    mc.setScreen(new ScanScreen());
                }
            }
            while (HUD.consumeClick()) {
                HudLine.toggle();
            }
            while (RESULTS.consumeClick()) {
                if (mc.screen == null && mc.player != null) {
                    mc.setScreen(new CulpritScreen());
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientReport.clear());
    }
}
