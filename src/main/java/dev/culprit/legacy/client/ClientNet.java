package dev.culprit.legacy.client;

import org.jspecify.annotations.Nullable;

import dev.culprit.legacy.net.Network;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** NeoForge's {@code hasChannel} on the client's connection: whether the server has Culprit (the mod's Forge channel). */
public final class ClientNet {
    private ClientNet() {}

    public static boolean serverHas(@Nullable ClientPacketListener connection) {
        return connection != null && Network.remotePresent(connection.getConnection());
    }
}
