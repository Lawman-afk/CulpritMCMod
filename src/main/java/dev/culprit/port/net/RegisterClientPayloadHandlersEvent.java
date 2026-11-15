package dev.culprit.port.net;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.culprit.legacy.net.CustomPacketPayload;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import dev.culprit.legacy.net.IPayloadHandler;

/**
 * NeoForge 26.x's event for the client's payload handlers, on 1.21.1 (this branch's own): 1.21.1 takes a client-bound
 * payload's handler as it is registered (on both sides), so it is registered with a handler that hands each payload to
 * the one the client gives here at its setup; a server never has one.
 */
public final class RegisterClientPayloadHandlersEvent extends Event implements IModBusEvent {
    private static final Map<CustomPacketPayload.Type<?>, IPayloadHandler<?>> HANDLERS = new ConcurrentHashMap<>();

    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, IPayloadHandler<T> handler) {
        HANDLERS.put(type, handler);
    }

    /** The handler a client-bound payload is registered with: the one the client gave, when it comes. */
    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> IPayloadHandler<T> handler(CustomPacketPayload.Type<T> type) {
        return (payload, context) -> {
            IPayloadHandler<T> h = (IPayloadHandler<T>) HANDLERS.get(type);
            if (h != null) {
                h.handle(payload, context);
            }
        };
    }

    /** Posts this event on the mod bus at client setup. */
    public static void post(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent e) -> modBus.post(new RegisterClientPayloadHandlersEvent()));
    }
}
