package dev.culprit.client;

import dev.culprit.net.CulpritNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** The client's handlers for the server's payloads ({@link CulpritNetwork}), kept apart so no client class loads on a server. */
public final class ClientNetwork {
    private ClientNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> {
            event.register(CulpritNetwork.Report.TYPE, (payload, context) -> ClientReport.receive(payload.data()));
            event.register(CulpritNetwork.Status.TYPE, (payload, context) -> ClientReport.status(payload.state(), payload.ticks()));
            event.register(CulpritNetwork.ChunksReport.TYPE, (payload, context) -> ClientReport.receiveChunks(payload.data()));
        });
    }
}
