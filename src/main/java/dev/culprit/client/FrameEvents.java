package dev.culprit.client;

import dev.culprit.scan.FrameProbe;
import dev.culprit.scan.FramePhase;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Mods' frame events, timed during a frame scan: a listener that runs before every other one starts the clock, and one
 * that runs after every other one stops it, so what the mods' listeners took between them is the frame's "mods' frame
 * events" part. (Listeners, not a hook in the frame loop: they cannot fail to find their place.)
 */
public final class FrameEvents {
    private static long start = -1;

    private FrameEvents() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, net.minecraftforge.event.TickEvent.RenderTickEvent.class, e -> begin());
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, net.minecraftforge.event.TickEvent.RenderTickEvent.class, e -> end());
    }

    private static void begin() {
        start = FrameProbe.measuring() ? FrameProbe.begin() : -1;
    }

    private static void end() {
        if (start >= 0 && FrameProbe.measuring()) {
            FrameProbe.phase(FramePhase.MOD_EVENTS, FrameProbe.end(start));
        }
        start = -1;
    }
}
