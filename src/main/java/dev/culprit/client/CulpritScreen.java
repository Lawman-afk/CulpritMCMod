package dev.culprit.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.culprit.command.ReportText;
import dev.culprit.net.ReportData;
import dev.culprit.scan.Kind;
import dev.culprit.scan.Phase;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The results screen (F9 by default; DESIGN §4.4): the last scan's whole tick as a bar, and its lists, costliest
 * first: the tick's parts, things, types, mods (with the game's own work beside them, so they add up to the tick) and
 * chunks. A click on a line marks it on the world; buttons for a new scan, showing or hiding the overlay, and
 * teleporting to the marked line. It fits itself to the window.
 */
public final class CulpritScreen extends Screen {
    private static final int ROW = 11;
    private static final int PANEL = 0xEC101216, EDGE = 0xFF3A3F4A, ROW_DARK = 0x22FFFFFF, ROW_HOVER = 0x3C7FB2FF, ROW_PICKED = 0x6066A8FF;
    private static final int TEXT = 0xFFFFFFFF, DIM = 0xFFA8AFBA, HEAD = 0xFFFFC34D, GAME = 0xFFC9CED6;
    private static final int TICK = 0, THINGS = 1, TYPES = 2, MODS = 3, CHUNKS = 4;
    private static final String[] TABS = {"tick", "things", "types", "mods", "chunks"};
    private static final String[] TAB_NAMES = {"Tick", "Things", "Types", "Mods", "Chunks"};
    private static final String[] NAME_HEADS = {"Part of the tick", "What", "Type", "Mod", "Chunk"};
    private static final String[] LAST_HEADS = {"What it is", "Worst", "Worst at", "Worst at", "Mostly"};

    /**
     * A line of the Tick or Mods list: a name, a count, its time summed over the scan, a note, whether it is the game's
     * own, its group's colour (or -1), and a place to mark (dim -1 for none) with the kind of thing there.
     */
    private record Line(Component name, String count, long totalNs, Component note, boolean game, int group, int dim, long pos, int kind) {}

    private static int tab = TICK;
    private int scroll;
    private int picked = -1;
    private int left, top, right, bottom, listTop, listBottom;
    /** The header's layout for this window and report: the summary's lines, the bar, the key's entries and rows (init). */
    private int summaryLines = 2, barY, legendRows = 1;
    private final int[] legendW = new int[Breakdown.GROUPS];
    private @Nullable ReportData layoutFor;
    /** Each tab's widest entry in its where and last columns for the report shown, or -1 until worked out. */
    private final int[] whereWidth = {-1, -1, -1, -1, -1}, lastWidth = {-1, -1, -1, -1, -1};
    private final List<Button> tabButtons = new ArrayList<>();
    private @Nullable Button overlay, teleport;
    private @Nullable ReportData linesFor;
    private List<Line> tickLines = List.of(), modLines = List.of();

    public CulpritScreen() {
        super(Component.translatableWithFallback("culprit.screen.title", "Culprit"));
    }

    /** Which list the screen shows next (and now, if it is open). */
    public static void showTab(int index) {
        tab = Math.clamp(index, 0, TABS.length - 1);
        if (Minecraft.getInstance().gui.screen() instanceof CulpritScreen open) {
            open.scroll = 0;
            open.picked = -1;
            open.refreshTabs();
        }
    }

    @Override
    protected void init() {
        left = 10;
        top = 8;
        right = this.width - 10;
        bottom = this.height - 8;
        int inner = right - left - 16;
        // The header follows the window and the report (a new report lays it out again): the summary's lines, the bar,
        // the key's rows, then the tabs.
        ReportData d = ClientReport.data();
        layoutFor = d;
        Component summary = d != null ? summary(d) : summaryText(Component.literal("99.99 ms"), "99.99 ms", 99999);
        summaryLines = Math.clamp(this.font.split(summary, inner).size(), 1, 3);
        barY = top + 18 + summaryLines * 10 + 2;
        long[] groups = d != null ? Breakdown.groups(d) : null;
        for (int i = 0; i < Breakdown.GROUPS; i++) {
            String time = d != null ? ReportText.time(d.perTick(groups[i])) : "99.99 ms";
            legendW[i] = 8 + this.font.width(Breakdown.groupName(i)) + 4 + this.font.width(time) + 10;
        }
        legendRows = legendRows(inner);
        int tabsY = barY + 10 + legendRows * 10 + 2;
        listTop = tabsY + 21;
        listBottom = bottom - 28;
        tabButtons.clear();
        int tabW = Math.min(58, (inner - 16) / TABS.length);
        int x = left + 8;
        for (int i = 0; i < TABS.length; i++) {
            int index = i;
            tabButtons.add(addRenderableWidget(Button.builder(tabLabel(i), button -> showTab(index)).bounds(x, tabsY, tabW, 16).build()));
            x += tabW + 4;
        }
        Component[] labels = {tr("new_scan", "New scan (%s)", CulpritKeys.SCAN.getTranslatedKeyMessage()), overlayLabel(),
                tr("teleport", "Teleport there"), tr("done", "Done")};
        int[] widths = new int[labels.length];
        int sum = 0;
        for (int i = 0; i < labels.length; i++) {
            widths[i] = this.font.width(labels[i]) + 14;
            sum += widths[i];
        }
        float shrink = Math.min(1f, (inner - 4f * (labels.length - 1)) / sum);
        int bx = left + 8, by = bottom - 23;
        Button[] made = new Button[labels.length];
        for (int i = 0; i < labels.length; i++) {
            int w = Math.max(24, (int) (widths[i] * shrink));
            int index = i;
            made[i] = addRenderableWidget(Button.builder(labels[i], b -> press(index, b)).bounds(bx, by, w, 18).build());
            bx += w + 4;
        }
        overlay = made[1];
        teleport = made[2];
        refreshTabs();
        tick();
    }

    private void press(int index, Button button) {
        switch (index) {
            case 0 -> Minecraft.getInstance().gui.setScreen(new ScanScreen());
            case 1 -> {
                Overlay.setShown(!Overlay.shown());
                button.setMessage(overlayLabel());
            }
            case 2 -> teleport();
            default -> onClose();
        }
    }

    private static MutableComponent tr(String key, String english, Object... args) {
        return Component.translatableWithFallback("culprit.screen." + key, english, args);
    }

    private Component tabLabel(int i) {
        MutableComponent name = tr("tab." + TABS[i], TAB_NAMES[i]);
        return i == tab ? name.withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE) : name;
    }

    private void refreshTabs() {
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).setMessage(tabLabel(i));
        }
    }

    private static Component overlayLabel() {
        return Overlay.shown() ? tr("overlay.on", "On the world: shown") : tr("overlay.off", "On the world: hidden");
    }

    @Override
    public void tick() {
        if (ClientReport.data() != layoutFor) {
            rebuildWidgets();                                  // a new report: its header laid out anew
            return;
        }
        if (teleport != null) {
            teleport.active = picked >= 0 && markable();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        g.fill(left, top, right, bottom, PANEL);
        g.outline(left, top, right - left, bottom - top, EDGE);
        g.fill(left + 4, listTop - 1, right - 4, listBottom + 1, 0x40000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), left + 8, top + 6, HEAD);
        ReportData d = ClientReport.data();
        Component status = statusLine(d);
        g.text(this.font, status, right - 8 - this.font.width(status), top + 6, DIM);
        int inner = right - left - 16;
        if (d == null) {
            int y = listTop + 6;
            for (FormattedCharSequence line : this.font.split(tr("empty",
                    "No scan yet. Press %s to scan the server (operators): the results show here and on the world.",
                    CulpritKeys.SCAN.getTranslatedKeyMessage()), inner - 8)) {
                g.text(this.font, line, left + 12, y, DIM);
                y += 10;
            }
            return;
        }
        if (linesFor != d) {
            linesFor = d;
            tickLines = tickLines(d);
            modLines = modLines(d);
            java.util.Arrays.fill(whereWidth, -1);
            java.util.Arrays.fill(lastWidth, -1);
        }
        int y = top + 18;
        for (FormattedCharSequence line : this.font.split(summary(d), inner)) {
            if (y >= top + 18 + summaryLines * 10) {
                break;
            }
            g.text(this.font, line, left + 8, y, TEXT);
            y += 10;
        }
        bar(g, d, left + 8, barY, inner);
        rows(g, d, mouseX, mouseY);
    }

    private Component statusLine(@Nullable ReportData d) {
        int secondsLeft = ClientReport.secondsLeft();
        if (secondsLeft >= 0) {
            return tr("scanning", "Scanning the server: %s s left...", secondsLeft).withStyle(ChatFormatting.YELLOW);
        }
        if (d != null) {
            long ago = (System.currentTimeMillis() - ClientReport.receivedMillis()) / 1000;
            return tr("age", "Results from %s ago", ago < 60 ? ago + " s" : ago / 60 + " min");
        }
        return Component.empty();
    }

    private static Component summary(ReportData d) {
        return summaryText(Component.literal(ReportText.time(d.perTick(d.tickNsTotal))).withColor(Heat.rgb(Heat.of(d.perTick(d.tickNsTotal)))),
                ReportText.time(d.tickNsMax), d.ticks);
    }

    private static Component summaryText(Component mean, String worst, int ticks) {
        return tr("summary2", "The server tick took %s on average and %s at worst over %s ticks (a tick has 50 ms). Below, all of it: what each part took.",
                mean, worst, ticks);
    }

    private int legendRows(int width) {
        int kx = 0, rows = 1;
        for (int i = 0; i < Breakdown.GROUPS; i++) {
            int w = legendW[i];
            if (kx > 0 && kx + w > width) {
                rows++;
                kx = 0;
            }
            kx += w;
        }
        return rows;
    }

    /** The whole tick as one bar, a coloured length per group, and the groups' names and times under it. */
    private void bar(GuiGraphicsExtractor g, ReportData d, int x, int y, int width) {
        long[] groups = Breakdown.groups(d);
        long total = 0;
        for (long ns : groups) {
            total += ns;
        }
        if (total <= 0) {
            return;
        }
        int bx = x;
        for (int i = 0; i < Breakdown.GROUPS; i++) {
            int w = (int) Math.round((double) groups[i] / total * width);
            if (i == Breakdown.GROUPS - 1) {
                w = x + width - bx;
            }
            if (w > 0) {
                g.fill(bx, y, bx + w, y + 7, 0xFF000000 | Breakdown.COLOUR[i]);
            }
            bx += w;
        }
        // The key: each group's square, name and time per tick, as many to a line as fit.
        int kx = x, ky = y + 10;
        for (int i = 0; i < Breakdown.GROUPS; i++) {
            Component name = Breakdown.groupName(i);
            String time = ReportText.time(d.perTick(groups[i]));
            int w = legendW[i];
            if (kx > x && kx + w > x + width) {
                kx = x;
                ky += 10;
            }
            g.fill(kx, ky + 1, kx + 6, ky + 7, 0xFF000000 | Breakdown.COLOUR[i]);
            g.text(this.font, name, kx + 8, ky, DIM);
            g.text(this.font, time, kx + 8 + this.font.width(name) + 4, ky, TEXT);
            kx += w;
        }
    }

    // --- the lists ----------------------------------------------------------------------------------------------------

    private List<Line> tickLines(ReportData d) {
        List<Line> lines = new ArrayList<>();
        for (Breakdown.Part p : Breakdown.parts(d)) {
            lines.add(new Line(p.name(), "", p.totalNs(), p.about(), true, p.group(), -1, 0, -1));
        }
        return lines;
    }

    /**
     * The mods (what their blocks and entities cost), and beside them the game's own work (chunks, levels, connections:
     * the Tick tab has each), Culprit's own timing and what was not measured: together, the whole tick.
     */
    private List<Line> modLines(ReportData d) {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < d.mods; i++) {
            lines.add(new Line(Component.literal(d.names[d.modName[i]]), String.valueOf(d.modCount[i]), d.modTotal[i],
                    Component.literal(place(d.dims[d.modWorstDim[i]], d.modWorstPos[i], d.modWorstKind[i], here())), false, -1,
                    d.modWorstDim[i], d.modWorstPos[i], d.modWorstKind[i]));
        }
        long game = 0;
        for (Breakdown.Part p : Breakdown.parts(d)) {
            if (p.group() == Breakdown.CHUNKS || p.group() == Breakdown.SERVER) {
                game += p.totalNs();
            }
        }
        lines.add(new Line(tr("game_work", "The game's own work"), "", game, tr("game_work.about", "chunks, levels, connections: each in the Tick tab"),
                true, -1, -1, 0, -1));
        lines.add(new Line(Component.translatableWithFallback("culprit.part.self", "Culprit's own timing"), "", d.selfNs,
                Component.translatableWithFallback("culprit.part.about.self", "measuring every call, estimated"), true, -1, -1, 0, -1));
        lines.add(new Line(Component.translatableWithFallback("culprit.part.rest", "Not measured"), "", d.unaccountedNs(),
                Component.translatableWithFallback("culprit.part.about.rest", "tick events and work between the parts"), true, -1, -1, 0, -1));
        lines.sort(Comparator.comparingLong(Line::totalNs).reversed());
        return lines;
    }

    private int rowCount(ReportData d) {
        return switch (tab) {
            case TICK -> tickLines.size();
            case TYPES -> d.types;
            case MODS -> modLines.size();
            case CHUNKS -> d.chunks;
            default -> d.things;
        };
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop - ROW) / ROW);
    }

    private static String here() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }

    private void rows(GuiGraphicsExtractor g, ReportData d, int mouseX, int mouseY) {
        String here = here();
        int x0 = left + 8, end = right - 12;
        int cName = x0 + this.font.width("999") + 6;
        // Columns from the right: the last (what it is, the worst, where the worst is), the share, per tick; then where
        // (or how many); the name takes the rest. Each is as wide as its widest entry in this report; the name keeps at
        // least 100 across, the last column taking what is left up to its width (a third at most for notes), and going
        // when under 40.
        int shareW = Math.max(this.font.width("99.9%"), this.font.width(tr("col.share", "% of tick"))) + 8;
        int costW = Math.max(this.font.width("999 µs"), this.font.width(tr("col.cost", "Per tick"))) + 8;
        int whereW = switch (tab) {
            case TICK -> 0;
            case THINGS -> Math.max(widest(d, true), this.font.width(tr("col.where", "Where"))) + 10;
            default -> Math.max(this.font.width("99999"), this.font.width(tr("col.count", "How many"))) + 10;
        };
        Component lastHead = tr("col.last." + TABS[tab], LAST_HEADS[tab]);
        int lastWanted = Math.max(widest(d, false), this.font.width(lastHead)) + 4;
        if (tab == TICK || tab == MODS) {
            lastWanted = Math.min(lastWanted, (end - x0) / 3);
        }
        int lastW = Math.min(lastWanted, end - cName - whereW - costW - shareW - 100);
        boolean roomForLast = lastW >= 40;
        int cLast = roomForLast ? end - lastW : end;
        int cShare = cLast - shareW, cCost = cShare - costW, cWhere = cCost - whereW;

        int y = listTop + 2;
        g.text(this.font, tr("col.name." + TABS[tab], NAME_HEADS[tab]), cName, y, HEAD);
        if (tab != TICK) {
            g.text(this.font, tr(tab == THINGS ? "col.where" : "col.count", tab == THINGS ? "Where" : "How many"), cWhere, y, HEAD);
        }
        g.text(this.font, tr("col.cost", "Per tick"), cCost, y, HEAD);
        g.text(this.font, tr("col.share", "% of tick"), cShare, y, HEAD);
        if (roomForLast) {
            g.text(this.font, lastHead, cLast, y, HEAD);
        }
        int n = rowCount(d);
        int rows = visibleRows();
        scroll = Math.clamp(scroll, 0, Math.max(0, n - rows));
        for (int r = 0; r < rows && scroll + r < n; r++) {
            int i = scroll + r;
            int ry = listTop + ROW + 2 + r * ROW;
            boolean hover = mouseX >= x0 && mouseX < right - 8 && mouseY >= ry - 1 && mouseY < ry + ROW - 1;
            if (i == picked) {
                g.fill(x0 - 2, ry - 2, right - 6, ry + ROW - 2, ROW_PICKED);
            } else if (hover) {
                g.fill(x0 - 2, ry - 2, right - 6, ry + ROW - 2, ROW_HOVER);
            } else if (r % 2 == 1) {
                g.fill(x0 - 2, ry - 2, right - 6, ry + ROW - 2, ROW_DARK);
            }
            g.text(this.font, String.valueOf(i + 1), x0, ry, DIM);
            long total;
            if (tab == TICK || tab == MODS) {
                Line line = (tab == TICK ? tickLines : modLines).get(i);
                total = line.totalNs();
                int nx = cName;
                if (line.group() >= 0) {
                    g.fill(nx, ry + 1, nx + 6, ry + 7, 0xFF000000 | Breakdown.COLOUR[line.group()]);
                    nx += 9;
                }
                g.text(this.font, clip(line.name(), (tab == TICK || line.count().isEmpty() ? cCost : cWhere) - nx - 6), nx, ry,
                        line.game() && tab == MODS ? GAME : TEXT);
                if (tab == MODS) {
                    g.text(this.font, line.count(), cWhere, ry, DIM);
                }
                if (roomForLast) {
                    g.text(this.font, clip(line.note(), end - cLast), cLast, ry, DIM);
                }
            } else {
                String name, where, last;
                switch (tab) {
                    case TYPES -> {
                        total = d.typeTotal[i];
                        name = Names.of(d.names[d.typeName[i]], d.typeKind[i]) + suffix(d.typeKind[i]);
                        where = String.valueOf(d.typeCount[i]);
                        last = place(d.dims[d.typeWorstDim[i]], d.typeWorstPos[i], d.typeKind[i], here);
                    }
                    case CHUNKS -> {
                        total = d.chunkTotal[i];
                        String dim = d.dims[d.chunkDim[i]];
                        name = d.chunkX[i] + ", " + d.chunkZ[i] + (dim.equals(here) ? "" : "  " + shortDim(dim));
                        where = String.valueOf(d.chunkCount[i]);
                        last = Names.of(d.names[d.chunkTop[i]], d.chunkTopKind[i]) + suffix(d.chunkTopKind[i]);
                    }
                    default -> {
                        total = d.total[i];
                        name = Names.of(d.names[d.type[i]], d.kind[i]) + suffix(d.kind[i]);
                        where = place(d.dims[d.dim[i]], d.pos[i], d.kind[i], here);
                        last = ReportText.time(d.max[i]);
                    }
                }
                g.text(this.font, clip(name, cWhere - cName - 6), cName, ry, TEXT);
                g.text(this.font, clip(where, cCost - cWhere - 6), cWhere, ry, DIM);
                if (roomForLast) {
                    g.text(this.font, clip(last, end - cLast), cLast, ry, DIM);
                }
            }
            long perTick = d.perTick(total);
            g.text(this.font, ReportText.time(perTick), cCost, ry, Heat.text(perTick));
            g.text(this.font, ReportText.share(total, d.tickNsTotal), cShare, ry, DIM);
        }
        if (n > rows) {
            int barTop = listTop + ROW, barH = listBottom - barTop;
            int knob = Math.max(8, barH * rows / n);
            int knobY = barTop + (barH - knob) * scroll / Math.max(1, n - rows);
            g.fill(right - 8, barTop, right - 6, listBottom, 0x30FFFFFF);
            g.fill(right - 8, knobY, right - 6, knobY + knob, 0xA0FFFFFF);
        }
    }

    /** The widest entry of the current tab's where column (or its last column) in this report, worked out once. */
    private int widest(ReportData d, boolean where) {
        int[] cache = where ? whereWidth : lastWidth;
        if (cache[tab] >= 0) {
            return cache[tab];
        }
        String here = here();
        int w = 0;
        switch (tab) {
            case TICK -> {
                for (Line line : tickLines) {
                    w = Math.max(w, this.font.width(line.note()));
                }
            }
            case THINGS -> {
                for (int i = 0; i < d.things; i++) {
                    w = Math.max(w, this.font.width(where ? place(d.dims[d.dim[i]], d.pos[i], d.kind[i], here) : ReportText.time(d.max[i])));
                }
            }
            case TYPES -> {
                for (int i = 0; i < d.types; i++) {
                    w = Math.max(w, this.font.width(place(d.dims[d.typeWorstDim[i]], d.typeWorstPos[i], d.typeKind[i], here)));
                }
            }
            case MODS -> {
                for (Line line : modLines) {
                    w = Math.max(w, this.font.width(line.note()));
                }
            }
            default -> {
                for (int i = 0; i < d.chunks; i++) {
                    w = Math.max(w, this.font.width(Names.of(d.names[d.chunkTop[i]], d.chunkTopKind[i]) + suffix(d.chunkTopKind[i])));
                }
            }
        }
        cache[tab] = w;
        return w;
    }

    /** After a thing's name, its kind in brackets, unless the name says it (block entities, entities, the game's own). */
    private static String suffix(int kind) {
        return kind < 0 || kind == Kind.BLOCK_ENTITY || kind == Kind.ENTITY || kind == Kind.CHUNK || kind == Kind.SPAWNING ? ""
                : "  (" + Component.translatableWithFallback("culprit.kind.one." + Kind.key(kind), Kind.englishOne(kind)).getString() + ")";
    }

    private String clip(String s, int width) {
        if (width <= 0) {
            return "";
        }
        if (this.font.width(s) <= width) {
            return s;
        }
        return this.font.plainSubstrByWidth(s, Math.max(0, width - this.font.width("..."))) + "...";
    }

    private FormattedCharSequence clip(Component c, int width) {
        if (width <= 0) {
            return FormattedCharSequence.EMPTY;
        }
        if (this.font.width(c) <= width) {
            return c.getVisualOrderText();
        }
        return Component.literal(clip(c.getString(), width)).withStyle(c.getStyle()).getVisualOrderText();
    }

    /** A place as "x, y, z" (a whole chunk's thing as "chunk x, z"), with its dimension when it is not the player's. */
    private static String place(String dim, long packed, int kind, String here) {
        BlockPos p = BlockPos.of(packed);
        String at = Kind.isChunkWide(kind) ? tr("chunk_place", "chunk %s, %s", p.getX() >> 4, p.getZ() >> 4).getString()
                : p.getX() + ", " + p.getY() + ", " + p.getZ();
        return at + (dim.equals(here) ? "" : "  " + shortDim(dim));
    }

    /** Where to stand for a thing: at it, or for a whole chunk's, on the ground in the chunk's middle. */
    private static BlockPos landing(long packed, int kind, String dim) {
        BlockPos p = BlockPos.of(packed);
        return Kind.isChunkWide(kind) ? surface(p.getX(), p.getZ(), dim) : p;
    }

    /** The top block at x, z when this client has that chunk, else the player's height. */
    private static BlockPos surface(int x, int z, String dim) {
        Minecraft mc = Minecraft.getInstance();
        int y = mc.player == null ? 64 : (int) Math.floor(mc.player.getY());
        if (mc.level != null && mc.level.dimension().identifier().toString().equals(dim) && mc.level.hasChunk(x >> 4, z >> 4)) {
            y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        }
        return new BlockPos(x, y, z);
    }

    private static String shortDim(String dim) {
        return dim.startsWith("minecraft:") ? dim.substring(10) : dim;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (y >= listTop && y < listBottom) {
            scroll -= (int) Math.signum(scrollY) * 3;
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ReportData d = ClientReport.data();
        if (d != null && event.button() == 0 && event.y() >= listTop + ROW && event.y() < listBottom && event.x() >= left + 6 && event.x() < right - 10) {
            int r = (int) ((event.y() - (listTop + ROW)) / ROW);
            int i = scroll + r;
            if (r < visibleRows() && i < rowCount(d)) {
                pick(d, i);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Whether the picked line has a place to mark and teleport to. */
    private boolean markable() {
        return switch (tab) {
            case TICK -> false;
            case MODS -> picked >= 0 && picked < modLines.size() && modLines.get(picked).dim() >= 0;
            default -> true;
        };
    }

    private void pick(ReportData d, int i) {
        picked = i;
        switch (tab) {
            case TYPES -> Overlay.markPlace(d.typeWorstDim[i], d.typeWorstPos[i],
                    Names.of(d.names[d.typeName[i]], d.typeKind[i]) + "  " + ReportText.time(d.perTick(d.typeTotal[i])) + "/t");
            case MODS -> {
                Line line = modLines.get(i);
                if (line.dim() >= 0) {
                    Overlay.markPlace(line.dim(), line.pos(), line.name().getString() + "  " + ReportText.time(d.perTick(line.totalNs())) + "/t");
                }
            }
            case CHUNKS -> Overlay.markChunk(i);
            case THINGS -> Overlay.markThing(i);
            default -> {}
        }
        if (overlay != null) {
            overlay.setMessage(overlayLabel());
        }
        tick();
    }

    /** Selects a line as a click would (the Lab's pictures). */
    public void pickRow(int i) {
        ReportData d = ClientReport.data();
        if (d != null && i >= 0 && i < rowCount(d)) {
            pick(d, i);
        }
    }

    private void teleport() {
        ReportData d = ClientReport.data();
        Minecraft mc = Minecraft.getInstance();
        if (d == null || picked < 0 || !markable() || mc.getConnection() == null || mc.player == null) {
            return;
        }
        String dim;
        BlockPos p;
        switch (tab) {
            case TYPES -> {
                dim = d.dims[d.typeWorstDim[picked]];
                p = landing(d.typeWorstPos[picked], d.typeKind[picked], dim);
            }
            case MODS -> {
                Line line = modLines.get(picked);
                dim = d.dims[line.dim()];
                p = landing(line.pos(), line.kind(), dim);
            }
            case CHUNKS -> {
                dim = d.dims[d.chunkDim[picked]];
                p = surface((d.chunkX[picked] << 4) + 8, (d.chunkZ[picked] << 4) + 8, dim);
            }
            default -> {
                dim = d.dims[d.dim[picked]];
                p = landing(d.pos[picked], d.kind[picked], dim);
            }
        }
        mc.getConnection().sendCommand("execute in " + dim + " run tp @s " + (p.getX() + 0.5) + " " + (p.getY() + 1) + " " + (p.getZ() + 0.5));
    }
}
