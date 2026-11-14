package dev.culprit.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dev.culprit.command.ReportText;
import dev.culprit.net.ReportData;
import dev.culprit.scan.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * A scan's report as files to share with a pack's makers or a server's staff (DESIGN §4.4): a Markdown page to read
 * (the summary, every part of the tick or frame, the costliest things, types, mods and chunks, the lag spikes or
 * stutters) and the same as JSON for tools. Written by {@code /culprit export} on the server and the results screen's
 * Save button on a client, into the game folder's culprit/reports. Common code.
 */
public final class ReportFile {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final int THINGS = 50, TYPES = 30, MODS = 30, CHUNKS = 30, SPIKE_THINGS = 20;

    private ReportFile() {}

    /** Writes the report's two files into {@code gameDir}/culprit/reports; returns the Markdown one. */
    public static Path write(ReportData d, Path gameDir) throws IOException {
        Path dir = gameDir.resolve("culprit").resolve("reports");
        Files.createDirectories(dir);
        LocalDateTime now = LocalDateTime.now();
        String base = "culprit-" + (d.frames ? "frames" : "ticks") + "-" + STAMP.format(now);
        Path md = dir.resolve(base + ".md");
        Files.writeString(md, markdown(d, now), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(base + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(json(d, now)), StandardCharsets.UTF_8);
        return md;
    }

    // ---- Markdown ----

    static String markdown(ReportData d, LocalDateTime when) {
        String unit = d.frames ? "frame" : "tick";
        StringBuilder s = new StringBuilder();
        s.append("# Culprit report: ").append(d.frames ? "frames" : "server ticks").append("\n\n");
        s.append("Saved ").append(WHEN.format(when)).append(". ");
        if (d.frames) {
            long mean = d.perTick(d.tickNsTotal);
            s.append("A frame took ").append(ReportText.time(mean)).append(" on average (")
                    .append(mean > 0 ? Math.round(1e9 / mean) : 0).append(" FPS) and ").append(ReportText.time(d.tickNsMax))
                    .append(" at worst, over ").append(d.ticks).append(" frames.\n\n");
        } else {
            s.append("The server tick took ").append(ReportText.time(d.perTick(d.tickNsTotal))).append(" on average and ")
                    .append(ReportText.time(d.tickNsMax)).append(" at worst (it has 50 ms), over ").append(d.ticks).append(" ticks.\n\n");
        }
        ReportData spikes = d.spikes;
        String slow = d.frames ? "stutters" : "lag spikes";
        if (spikes != null && spikes.ticks > 0) {
            s.append(capital(slow)).append(": ").append(spikes.ticks).append(" ").append(unit).append("s over ")
                    .append(ReportText.time(d.spikeThresholdNs)).append(" (twice the usual ").append(ReportText.time(d.medianTickNs))
                    .append("), ").append(ReportText.time(spikes.perTick(spikes.tickNsTotal))).append(" on average.\n\n");
        } else {
            s.append("No ").append(slow).append(".\n\n");
        }
        s.append("## Where the time went\n\n| Part | Per ").append(unit).append(" | Share | What it is |\n|---|---:|---:|---|\n");
        for (Breakdown.Part p : sortedParts(d)) {
            s.append("| ").append(cell(p.name().getString())).append(" | ").append(ReportText.time(d.perTick(p.totalNs()))).append(" | ")
                    .append(ReportText.share(p.totalNs(), d.tickNsTotal)).append(" | ").append(cell(p.about().getString())).append(" |\n");
        }
        things(s, d, THINGS, "## The costliest things");
        types(s, d, unit);
        mods(s, d, unit);
        chunks(s, d, unit);
        if (spikes != null && spikes.ticks > 0) {
            things(s, spikes, SPIKE_THINGS, "## The costliest things in the " + slow);
        }
        s.append("\nMade by Culprit. Times are each thing's own, its share of the whole ").append(unit).append(" of the scan.\n");
        return s.toString();
    }

    private static void things(StringBuilder s, ReportData d, int most, String title) {
        String unit = d.frames ? "frame" : "tick";
        s.append('\n').append(title).append("\n\n| # | What | Kind | Where | Per ").append(unit).append(" | Share | Worst | Mod |\n")
                .append("|---:|---|---|---|---:|---:|---:|---|\n");
        for (int i = 0; i < Math.min(most, d.things); i++) {
            String type = d.names[d.type[i]];
            s.append("| ").append(i + 1).append(" | ").append(cell(Names.of(type, d.kind[i]))).append(" | ").append(Kind.englishOne(d.kind[i]))
                    .append(" | ").append(cell(place(d, d.dim[i], d.pos[i], d.kind[i]))).append(" | ").append(ReportText.time(d.perTick(d.total[i])))
                    .append(" | ").append(ReportText.share(d.total[i], d.tickNsTotal)).append(" | ").append(ReportText.time(d.max[i]))
                    .append(" | ").append(cell(Names.mod(namespace(type)))).append(" |\n");
        }
    }

    private static void types(StringBuilder s, ReportData d, String unit) {
        s.append("\n## Types\n\n| # | Type | Kind | How many | Per ").append(unit).append(" | Share | Worst at |\n|---:|---|---|---:|---:|---:|---|\n");
        for (int i = 0; i < Math.min(TYPES, d.types); i++) {
            String type = d.names[d.typeName[i]];
            s.append("| ").append(i + 1).append(" | ").append(cell(Names.of(type, d.typeKind[i]))).append(" | ").append(Kind.englishOne(d.typeKind[i]))
                    .append(" | ").append(d.typeCount[i]).append(" | ").append(ReportText.time(d.perTick(d.typeTotal[i]))).append(" | ")
                    .append(ReportText.share(d.typeTotal[i], d.tickNsTotal)).append(" | ")
                    .append(cell(place(d, d.typeWorstDim[i], d.typeWorstPos[i], d.typeKind[i]))).append(" |\n");
        }
    }

    private static void mods(StringBuilder s, ReportData d, String unit) {
        s.append("\n## Mods\n\n| # | Mod | Things | Per ").append(unit).append(" | Share |\n|---:|---|---:|---:|---:|\n");
        for (int i = 0; i < Math.min(MODS, d.mods); i++) {
            s.append("| ").append(i + 1).append(" | ").append(cell(Names.mod(d.names[d.modName[i]]))).append(" | ").append(d.modCount[i])
                    .append(" | ").append(ReportText.time(d.perTick(d.modTotal[i]))).append(" | ").append(ReportText.share(d.modTotal[i], d.tickNsTotal))
                    .append(" |\n");
        }
    }

    private static void chunks(StringBuilder s, ReportData d, String unit) {
        s.append("\n## Chunks\n\n| # | Chunk | Dimension | Things | Per ").append(unit).append(" | Share | Mostly |\n|---:|---|---|---:|---:|---:|---|\n");
        for (int i = 0; i < Math.min(CHUNKS, d.chunks); i++) {
            String top = d.chunkTop[i] >= 0 ? Names.of(d.names[d.chunkTop[i]], d.chunkTopKind[i]) : "";
            s.append("| ").append(i + 1).append(" | ").append(d.chunkX[i]).append(", ").append(d.chunkZ[i]).append(" | ")
                    .append(cell(d.dims[d.chunkDim[i]])).append(" | ").append(d.chunkCount[i]).append(" | ")
                    .append(ReportText.time(d.perTick(d.chunkTotal[i]))).append(" | ").append(ReportText.share(d.chunkTotal[i], d.tickNsTotal))
                    .append(" | ").append(cell(top)).append(" |\n");
        }
    }

    // ---- JSON ----

    static JsonObject json(ReportData d, LocalDateTime when) {
        JsonObject o = new JsonObject();
        o.addProperty("culpritReport", 1);
        o.addProperty("of", d.frames ? "frames" : "ticks");
        o.addProperty("saved", when.toString());
        add(o, d);
        if (d.spikes != null) {
            JsonObject spikes = new JsonObject();
            add(spikes, d.spikes);
            o.add(d.frames ? "stutters" : "lagSpikes", spikes);
        }
        return o;
    }

    private static void add(JsonObject o, ReportData d) {
        o.addProperty("count", d.ticks);
        o.addProperty("meanNs", d.perTick(d.tickNsTotal));
        o.addProperty("worstNs", d.tickNsMax);
        o.addProperty("medianNs", d.medianTickNs);
        o.addProperty("spikeThresholdNs", d.spikeThresholdNs);
        o.addProperty("thingsMeasured", d.thingCount);
        JsonArray parts = new JsonArray();
        for (Breakdown.Part p : sortedParts(d)) {
            JsonObject part = new JsonObject();
            part.addProperty("name", p.name().getString());
            part.addProperty("nsPerCount", d.perTick(p.totalNs()));
            parts.add(part);
        }
        o.add("parts", parts);
        JsonArray things = new JsonArray();
        for (int i = 0; i < d.things; i++) {
            JsonObject t = new JsonObject();
            String type = d.names[d.type[i]];
            t.addProperty("name", Names.of(type, d.kind[i]));
            t.addProperty("type", type);
            t.addProperty("kind", Kind.key(d.kind[i]));
            t.addProperty("dimension", d.dims[d.dim[i]]);
            BlockPos pos = BlockPos.of(d.pos[i]);
            t.addProperty("x", pos.getX());
            t.addProperty("y", pos.getY());
            t.addProperty("z", pos.getZ());
            t.addProperty("nsPerCount", d.perTick(d.total[i]));
            t.addProperty("worstNs", d.max[i]);
            things.add(t);
        }
        o.add("things", things);
        JsonArray types = new JsonArray();
        for (int i = 0; i < d.types; i++) {
            JsonObject t = new JsonObject();
            t.addProperty("type", d.names[d.typeName[i]]);
            t.addProperty("kind", Kind.key(d.typeKind[i]));
            t.addProperty("count", d.typeCount[i]);
            t.addProperty("nsPerCount", d.perTick(d.typeTotal[i]));
            types.add(t);
        }
        o.add("types", types);
        JsonArray mods = new JsonArray();
        for (int i = 0; i < d.mods; i++) {
            JsonObject m = new JsonObject();
            m.addProperty("mod", d.names[d.modName[i]]);
            m.addProperty("things", d.modCount[i]);
            m.addProperty("nsPerCount", d.perTick(d.modTotal[i]));
            mods.add(m);
        }
        o.add("mods", mods);
        JsonArray chunks = new JsonArray();
        for (int i = 0; i < d.chunks; i++) {
            JsonObject c = new JsonObject();
            c.addProperty("dimension", d.dims[d.chunkDim[i]]);
            c.addProperty("x", d.chunkX[i]);
            c.addProperty("z", d.chunkZ[i]);
            c.addProperty("things", d.chunkCount[i]);
            c.addProperty("nsPerCount", d.perTick(d.chunkTotal[i]));
            chunks.add(c);
        }
        o.add("chunks", chunks);
    }

    // ---- helpers ----

    private static List<Breakdown.Part> sortedParts(ReportData d) {
        List<Breakdown.Part> parts = new java.util.ArrayList<>(Breakdown.parts(d));
        parts.sort((a, b) -> Long.compare(b.totalNs(), a.totalNs()));
        return parts;
    }

    private static String place(ReportData d, int dim, long pos, int kind) {
        BlockPos p = BlockPos.of(pos);
        String where = Kind.isChunkWide(kind) ? "chunk " + (p.getX() >> 4) + ", " + (p.getZ() >> 4) : p.getX() + ", " + p.getY() + ", " + p.getZ();
        String dimension = dim >= 0 && dim < d.dims.length ? d.dims[dim] : "";
        return dimension.equals("minecraft:overworld") || dimension.isEmpty() ? where : where + " (" + shortDim(dimension) + ")";
    }

    private static String shortDim(String dim) {
        return dim.startsWith("minecraft:") ? dim.substring(10) : dim;
    }

    private static String namespace(String type) {
        Identifier id = Identifier.tryParse(type);
        return id == null ? "minecraft" : id.getNamespace();
    }

    private static String cell(String s) {
        return s.replace("|", "\\|").replace("\n", " ");
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
