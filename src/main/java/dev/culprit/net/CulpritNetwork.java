package dev.culprit.net;

import dev.culprit.Culprit;
import dev.culprit.command.ReportText;
import dev.culprit.scan.ScanReport;
import dev.culprit.scan.TickScan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The payloads between Culprit's server side and a player's client (DESIGN §5.4). All are optional: a server without
 * the mod still lets a player with it in (the frame scan needs no server), and a vanilla client still joins a server
 * with it (and gets the commands' text). The client's handlers are registered in {@code CulpritClient}.
 */
public final class CulpritNetwork {
    public static final String VERSION = "1";

    /** Server to client: a finished scan's report. */
    public record Report(ReportData data) implements CustomPacketPayload {
        public static final Type<Report> TYPE = new Type<>(Culprit.id("report"));
        public static final StreamCodec<FriendlyByteBuf, Report> CODEC = StreamCodec.of(
                (FriendlyByteBuf buf, Report p) -> p.data.write(buf), buf -> new Report(ReportData.read(buf)));

        @Override
        public Type<Report> type() {
            return TYPE;
        }
    }

    /** Server to client: where the scan a player asked for stands. */
    public record Status(byte state, int ticks) implements CustomPacketPayload {
        public static final byte DENIED = 0;
        public static final byte STARTED = 1;
        public static final byte BUSY = 2;
        public static final byte STOPPED = 3;
        public static final byte NOT_RUNNING = 4;
        /** The answer to "may I scan?": yes (ticks: the longest scan allowed, in seconds). */
        public static final byte ALLOWED = 5;
        /** The answer to "may I scan?" while a scan runs: yes, and this many ticks are left of it. */
        public static final byte RUNNING = 6;
        public static final Type<Status> TYPE = new Type<>(Culprit.id("status"));
        public static final StreamCodec<FriendlyByteBuf, Status> CODEC = StreamCodec.of(
                (FriendlyByteBuf buf, Status p) -> {
                    buf.writeByte(p.state);
                    buf.writeVarInt(p.ticks);
                }, buf -> new Status(buf.readByte(), buf.readVarInt()));

        @Override
        public Type<Status> type() {
            return TYPE;
        }
    }

    /** Client to server: scan for {@code seconds} (5 to the server's longest), stop the running scan (0), or ask whether this player may (-1). */
    public record ScanRequest(int seconds) implements CustomPacketPayload {
        public static final Type<ScanRequest> TYPE = new Type<>(Culprit.id("scan_request"));
        public static final StreamCodec<FriendlyByteBuf, ScanRequest> CODEC = StreamCodec.of(
                (FriendlyByteBuf buf, ScanRequest p) -> buf.writeVarInt(p.seconds), buf -> new ScanRequest(buf.readVarInt()));

        @Override
        public Type<ScanRequest> type() {
            return TYPE;
        }
    }

    /** Client to server: look at the loaded chunks now (the chunk inspector). */
    public record ChunksRequest() implements CustomPacketPayload {
        public static final Type<ChunksRequest> TYPE = new Type<>(Culprit.id("chunks_request"));
        public static final StreamCodec<FriendlyByteBuf, ChunksRequest> CODEC = StreamCodec.of((FriendlyByteBuf buf, ChunksRequest p) -> {
        }, buf -> new ChunksRequest());

        @Override
        public Type<ChunksRequest> type() {
            return TYPE;
        }
    }

    /** Server to client: the loaded chunks, and why (the chunk inspector's findings). */
    public record ChunksReport(ChunkData data) implements CustomPacketPayload {
        public static final Type<ChunksReport> TYPE = new Type<>(Culprit.id("chunks_report"));
        public static final StreamCodec<FriendlyByteBuf, ChunksReport> CODEC = StreamCodec.of(
                (FriendlyByteBuf buf, ChunksReport p) -> p.data.write(buf), buf -> new ChunksReport(ChunkData.read(buf)));

        @Override
        public Type<ChunksReport> type() {
            return TYPE;
        }
    }

    /** Client to server: send the HUD line's numbers once a second (true), or stop (false). */
    public record LoadRequest(boolean on) implements CustomPacketPayload {
        public static final Type<LoadRequest> TYPE = new Type<>(Culprit.id("load_request"));
        public static final StreamCodec<FriendlyByteBuf, LoadRequest> CODEC = StreamCodec.of(
                (FriendlyByteBuf buf, LoadRequest p) -> buf.writeBoolean(p.on), buf -> new LoadRequest(buf.readBoolean()));

        @Override
        public Type<LoadRequest> type() {
            return TYPE;
        }
    }

    /** Server to client: the server's average time a tick and ticks a second, for the HUD line. */
    public record Load(long tickNs, float tps) implements CustomPacketPayload {
        public static final Type<Load> TYPE = new Type<>(Culprit.id("load"));
        public static final StreamCodec<FriendlyByteBuf, Load> CODEC = StreamCodec.of(
                (FriendlyByteBuf buf, Load p) -> {
                    buf.writeVarLong(p.tickNs);
                    buf.writeFloat(p.tps);
                }, buf -> new Load(buf.readVarLong(), buf.readFloat()));

        @Override
        public Type<Load> type() {
            return TYPE;
        }
    }

    private CulpritNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CulpritNetwork::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        registrar.playToClient(Report.TYPE, Report.CODEC);
        registrar.playToClient(Status.TYPE, Status.CODEC);
        registrar.playToServer(ScanRequest.TYPE, ScanRequest.CODEC, CulpritNetwork::scanRequest);
        registrar.playToServer(ChunksRequest.TYPE, ChunksRequest.CODEC, CulpritNetwork::chunksRequest);
        registrar.playToClient(ChunksReport.TYPE, ChunksReport.CODEC);
        registrar.playToServer(LoadRequest.TYPE, LoadRequest.CODEC, (request, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                LoadSender.asked(player, request.on());
            }
        });
        registrar.playToClient(Load.TYPE, Load.CODEC);
    }

    /** Whether a player may start and stop scans: the server's setting (operators by default), as the commands. */
    public static boolean mayScan(ServerPlayer player) {
        return dev.culprit.config.CulpritServerConfig.mayScan(player.permissions());
    }

    private static void scanRequest(ScanRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!mayScan(player)) {
            status(player, Status.DENIED, 0);
            return;
        }
        if (request.seconds() < 0) {
            int running = TickScan.ticksLeft();
            status(player, running >= 0 ? Status.RUNNING : Status.ALLOWED, running >= 0 ? running : dev.culprit.config.CulpritServerConfig.maxSeconds());
            return;
        }
        if (request.seconds() <= 0) {
            status(player, TickScan.stopNow(true) ? Status.STOPPED : Status.NOT_RUNNING, 0);
            return;
        }
        int seconds = Math.clamp(request.seconds(), 5, dev.culprit.config.CulpritServerConfig.maxSeconds());
        int left = TickScan.ticksLeft();
        if (left >= 0) {
            status(player, Status.BUSY, left);
            return;
        }
        if (TickScan.start(seconds * 20, report -> finished(player, report))) {
            status(player, Status.STARTED, seconds * 20);
        }
    }

    private static void chunksRequest(ChunksRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!mayScan(player)) {
            status(player, Status.DENIED, 0);
            return;
        }
        if (player.connection.hasChannel(ChunksReport.TYPE)) {
            PacketDistributor.sendToPlayer(player, new ChunksReport(dev.culprit.chunks.ChunkCensus.take(player.level().getServer())));
        }
    }

    private static void finished(ServerPlayer player, ScanReport report) {
        if (player.hasDisconnected()) {
            return;
        }
        if (!sendReport(player, report)) {
            player.sendSystemMessage(ReportText.summaryLine(report));
        }
    }

    /** Sends a report to a player whose client has Culprit; false if it has not (it gets the text instead). */
    public static boolean sendReport(ServerPlayer player, ScanReport report) {
        if (!player.connection.hasChannel(Report.TYPE)) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new Report(ReportData.of(report)));
        return true;
    }

    private static void status(ServerPlayer player, byte state, int ticks) {
        if (player.connection.hasChannel(Status.TYPE)) {
            PacketDistributor.sendToPlayer(player, new Status(state, ticks));
        }
    }
}
