package dev.culprit.net;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import dev.culprit.legacy.net.PacketDistributor;

/**
 * The HUD line's numbers for players on a server (DESIGN §4.5): the server's average time a tick and ticks a second,
 * sent once a second to each player who asked and may scan there (an operator, by default). Nothing is sent while no
 * one has asked. On the server thread.
 */
public final class LoadSender {
    private static final Set<UUID> ASKED = new HashSet<>();

    private LoadSender() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent e) -> { if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return; tick(e.getServer()); });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ASKED.remove(e.getEntity().getUUID()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ASKED.clear());
    }

    static void asked(ServerPlayer player, boolean on) {
        if (on && CulpritNetwork.mayScan(player)) {
            ASKED.add(player.getUUID());
        } else {
            ASKED.remove(player.getUUID());
        }
    }

    private static void tick(MinecraftServer server) {
        if (ASKED.isEmpty() || server.getTickCount() % 20 != 0) {
            return;
        }
        long ns = (long) (server.getAverageTickTime() * 1_000_000L);
        float tps = (float) Math.min(20f, ns > 0 ? 1e9 / ns : 20);
        for (Iterator<UUID> it = ASKED.iterator(); it.hasNext(); ) {
            ServerPlayer player = server.getPlayerList().getPlayer(it.next());
            if (player == null || !CulpritNetwork.mayScan(player)) {
                it.remove();                                    // (gone, or no longer allowed)
                continue;
            }
            PacketDistributor.sendToPlayer(player, new CulpritNetwork.Load(ns, tps));
        }
    }
}
