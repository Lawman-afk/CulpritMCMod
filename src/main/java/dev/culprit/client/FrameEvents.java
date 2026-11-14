package dev.culprit.client;

import dev.culprit.scan.FrameProbe;
import dev.culprit.scan.FramePhase;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Mods' frame events, timed during a frame scan: a listener that runs before every other one starts the clock, and one
 * that runs after every other one stops it, so what the mods' listeners took between them is the frame's "mods' frame
 * events" part. (Listeners, not a hook in the frame loop: they cannot fail to find their place.)
 */
public final class FrameEvents {
    private static long start = -1;

    private FrameEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderFrameEvent.Pre.class, e -> begin());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderFrameEvent.Pre.class, e -> end());
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderFrameEvent.Post.class, e -> begin());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderFrameEvent.Post.class, e -> end());
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
