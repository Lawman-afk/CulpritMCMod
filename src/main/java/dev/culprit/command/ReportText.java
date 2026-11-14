package dev.culprit.command;

import java.util.List;
import java.util.Locale;

import dev.culprit.scan.Kind;
import dev.culprit.scan.ScanReport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A scan report as chat lines, for players and server consoles alike. Every phrase is translatable with an English
 * fallback, so a player without the mod (a vanilla client) still reads it.
 */
public final class ReportText {
    /** A server tick's budget at 20 ticks a second. */
    private static final double TICK_NS = 50_000_000.0;

    private ReportText() {}

    static MutableComponent tr(String key, String fallback, Object... args) {
        return Component.translatableWithFallback("culprit." + key, fallback, args);
    }

    /** A time per tick: "0.8 µs", "412 µs", "1.20 ms". */
    public static String time(long ns) {
        double us = ns / 1000.0;
        if (us < 10) {
            return String.format(Locale.ROOT, "%.1f µs", us);
        }
        if (us < 1000) {
            return String.format(Locale.ROOT, "%.0f µs", us);
        }
        return String.format(Locale.ROOT, "%.2f ms", us / 1000.0);
    }

    /** A per-tick time as a share of the tick's 50 ms. */
    public static String share(long nsPerTick) {
        return String.format(Locale.ROOT, "%.1f%%", nsPerTick / TICK_NS * 100.0);
    }

    public static Component summaryLine(ScanReport r) {
        return summary(r);
    }

    public static Component summary(ScanReport r) {
        MutableComponent text = tr("report.summary", "Culprit scanned %s ticks. The server tick took %s on average and %s at worst (a tick has 50 ms).",
                r.ticks, time(r.perTick(r.tickNsTotal)), time(r.tickNsMax));
        MutableComponent kinds = tr("report.measured", "Measured per tick: %s.", kindList(r));
        MutableComponent more = tr("report.more", "Details: /culprit top things, types, mods or chunks.").withStyle(ChatFormatting.GRAY);
        MutableComponent all = Component.empty().append(text).append("\n").append(kinds).append("\n").append(more);
        if (r.incomplete) {
            all.append("\n").append(tr("report.incomplete", "The scan met more than a million things and measured only the first million.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return all;
    }

    private static Component kindList(ScanReport r) {
        MutableComponent list = Component.empty();
        for (int k = 0; k < Kind.COUNT; k++) {
            if (k > 0) {
                list.append(", ");
            }
            list.append(tr("kind." + Kind.key(k), Kind.english(k))).append(" " + time(r.perTick(r.kindNs[k])));
        }
        return list;
    }

    static Component header(String what, int shown, ScanReport r) {
        return tr("report.top." + what, switch (what) {
            case "types" -> "The %s costliest types:";
            case "mods" -> "The %s costliest mods:";
            case "chunks" -> "The %s costliest chunks:";
            default -> "The %s costliest things:";
        }, shown).withStyle(ChatFormatting.GOLD);
    }

    static Component thing(int rank, ScanReport.Thing th, ScanReport r, boolean teleport) {
        long perTick = r.perTick(th.totalNs());
        MutableComponent line = Component.literal(rank + ". ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(th.type()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (").append(tr("kind.one." + Kind.key(th.kind()), Kind.englishOne(th.kind()))).append(")")
                        .withStyle(ChatFormatting.GRAY))
                .append(" ").append(place(th.level(), th.blockPos(), teleport))
                .append(": ").append(cost(perTick)).append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                .append(tr("report.worst", "worst %s", time(th.maxNs())).withStyle(ChatFormatting.GRAY));
        return line;
    }

    static Component group(int rank, ScanReport.Group g, ScanReport r, boolean teleport) {
        long perTick = r.perTick(g.totalNs());
        MutableComponent name = Component.literal(g.name()).withStyle(ChatFormatting.WHITE);
        if (g.kind() >= 0) {
            name.append(Component.literal(" (").append(tr("kind." + Kind.key(g.kind()), Kind.english(g.kind()))).append(")")
                    .withStyle(ChatFormatting.GRAY));
        }
        return Component.literal(rank + ". ").withStyle(ChatFormatting.GRAY).append(name)
                .append(": ").append(cost(perTick))
                .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                .append(tr("report.count", "%s of them, the worst %s", g.things(), Component.empty()
                        .append(place(g.worst().level(), g.worst().blockPos(), teleport))).withStyle(ChatFormatting.GRAY));
    }

    static Component chunk(int rank, ScanReport.ChunkGroup c, ScanReport r, boolean teleport) {
        long perTick = r.perTick(c.totalNs());
        return Component.literal(rank + ". ").withStyle(ChatFormatting.GRAY)
                .append(tr("report.chunk", "chunk %s, %s", c.x(), c.z()).withStyle(ChatFormatting.WHITE))
                .append(" ").append(place(c.level(), c.centre(), teleport))
                .append(": ").append(cost(perTick))
                .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                .append(tr("report.chunk.top", "%s things, most of it %s (%s)", c.things(), c.topType(), time(r.perTick(c.topTypeNs())))
                        .withStyle(ChatFormatting.GRAY));
    }

    private static Component cost(long perTick) {
        ChatFormatting colour = perTick >= 2_500_000 ? ChatFormatting.RED : perTick >= 500_000 ? ChatFormatting.GOLD : ChatFormatting.YELLOW;
        return Component.literal(time(perTick) + "/t").withStyle(colour)
                .append(Component.literal(" (" + share(perTick) + ")").withStyle(ChatFormatting.GRAY));
    }

    /** "at 120, 64, -30 in overworld", a click teleporting there when {@code teleport}. */
    static Component place(ResourceKey<Level> level, BlockPos pos, boolean teleport) {
        String dim = level.identifier().getNamespace().equals("minecraft") ? level.identifier().getPath() : level.identifier().toString();
        MutableComponent text = tr("report.at", "at %s, %s, %s in %s", pos.getX(), pos.getY(), pos.getZ(), dim);
        if (!teleport) {
            return text.withStyle(ChatFormatting.GRAY);
        }
        String command = "/execute in " + level.identifier() + " run tp @s " + pos.getX() + " " + (pos.getY() + 1) + " " + pos.getZ();
        return text.withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(tr("report.teleport", "Click to teleport there"))));
    }

    static <T> int count(List<T> list, int wanted) {
        return Math.min(list.size(), wanted);
    }
}
