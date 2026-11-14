package dev.culprit.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import dev.culprit.scan.ScanReport;
import dev.culprit.scan.TickScan;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /culprit scan [seconds]}, {@code /culprit stop}, {@code /culprit top [things|types|mods|chunks] [count]}:
 * the tick scan from chat or a server console (DESIGN §4.4). Operators only.
 */
public final class CulpritCommand {
    private static final int DEFAULT_SECONDS = 10;
    private static final int DEFAULT_LINES = 10;

    private CulpritCommand() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> commands(e.getDispatcher()));
    }

    private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> top = Commands.literal("top").executes(c -> top(c, "things", DEFAULT_LINES));
        for (String what : new String[] {"things", "types", "mods", "chunks"}) {
            top.then(Commands.literal(what).executes(c -> top(c, what, DEFAULT_LINES))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                            .executes(c -> top(c, what, IntegerArgumentType.getInteger(c, "count")))));
        }
        dispatcher.register(Commands.literal("culprit")
                .requires(source -> dev.culprit.config.CulpritServerConfig.mayScan(source.permissions()))
                .then(Commands.literal("scan").executes(c -> scan(c, DEFAULT_SECONDS))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                                .executes(c -> scan(c, IntegerArgumentType.getInteger(c, "seconds")))))
                .then(Commands.literal("stop").executes(CulpritCommand::stop))
                .then(top));
    }

    private static int scan(CommandContext<CommandSourceStack> c, int asked) {
        CommandSourceStack source = c.getSource();
        int seconds = Math.min(asked, dev.culprit.config.CulpritServerConfig.maxSeconds());
        int left = TickScan.ticksLeft();
        if (left >= 0) {
            source.sendFailure(ReportText.tr("scan.busy", "A scan is already running (%s s left).", (left + 19) / 20));
            return 0;
        }
        net.minecraft.server.level.ServerPlayer player = source.getPlayer();
        boolean started = TickScan.start(seconds * 20, report -> {
            // A player with the mod gets the report (their client says it in chat and draws it); anyone else the text.
            boolean sent = player != null && !player.hasDisconnected() && dev.culprit.net.CulpritNetwork.sendReport(player, report);
            if (!sent) {
                source.sendSuccess(() -> ReportText.summary(report), false);
            }
        });
        if (!started) {
            source.sendFailure(ReportText.tr("scan.busy", "A scan is already running (%s s left).", seconds));
            return 0;
        }
        source.sendSuccess(() -> ReportText.tr("scan.started", "Scanning the server for %s seconds...", seconds), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> c) {
        if (!TickScan.stopNow(true)) {
            c.getSource().sendFailure(ReportText.tr("scan.not_running", "No scan is running."));
            return 0;
        }
        c.getSource().sendSuccess(() -> ReportText.tr("scan.stopped", "Scan stopped early."), true);
        return 1;
    }

    private static int top(CommandContext<CommandSourceStack> c, String what, int lines) {
        CommandSourceStack source = c.getSource();
        ScanReport r = TickScan.last();
        if (r == null) {
            source.sendFailure(ReportText.tr("scan.none", "No scan yet: run /culprit scan first."));
            return 0;
        }
        boolean teleport = source.isPlayer();
        switch (what) {
            case "types" -> {
                int n = ReportText.count(r.types, lines);
                source.sendSuccess(() -> ReportText.header(what, n, r), false);
                for (int i = 0; i < n; i++) {
                    int rank = i + 1;
                    ScanReport.Group g = r.types.get(i);
                    source.sendSuccess(() -> ReportText.group(rank, g, r, teleport), false);
                }
            }
            case "mods" -> {
                int n = ReportText.count(r.mods, lines);
                source.sendSuccess(() -> ReportText.header(what, n, r), false);
                for (int i = 0; i < n; i++) {
                    int rank = i + 1;
                    ScanReport.Group g = r.mods.get(i);
                    source.sendSuccess(() -> ReportText.group(rank, g, r, teleport), false);
                }
            }
            case "chunks" -> {
                int n = ReportText.count(r.chunks, lines);
                source.sendSuccess(() -> ReportText.header(what, n, r), false);
                for (int i = 0; i < n; i++) {
                    int rank = i + 1;
                    ScanReport.ChunkGroup g = r.chunks.get(i);
                    source.sendSuccess(() -> ReportText.chunk(rank, g, r, teleport), false);
                }
            }
            default -> {
                int n = ReportText.count(r.things, lines);
                source.sendSuccess(() -> ReportText.header(what, n, r), false);
                for (int i = 0; i < n; i++) {
                    int rank = i + 1;
                    ScanReport.Thing th = r.things.get(i);
                    source.sendSuccess(() -> ReportText.thing(rank, th, r, teleport), false);
                }
            }
        }
        if (r.things.isEmpty()) {
            source.sendSuccess(() -> ReportText.tr("report.empty", "Nothing was measured.").withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }
}
