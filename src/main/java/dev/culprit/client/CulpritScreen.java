package dev.culprit.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import dev.culprit.command.ReportText;
import dev.culprit.net.ReportData;
import dev.culprit.scan.FrameScan;
import dev.culprit.scan.Kind;
import dev.culprit.scan.Phase;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The results screen (F9 by default; DESIGN §4.4): the last scan's whole tick as a bar, and its lists, costliest
 * first: the tick's parts, things, types, mods (with the game's own work beside them, so they add up to the tick) and
 * chunks; a search box that narrows the list shown (Enter picks the first found). A click on a line marks it on the
 * world; buttons for a new scan, showing the results on the world (off until asked for), and teleporting to the
 * marked line. It fits itself to the window.
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
    private record Line(Component name, String count, long totalNs, Component note, boolean game, int group, int dim, long pos, int kind,
                        String key) {}

    private static int tab = TICK;
    private int scroll;
    private int picked = -1;
    private int left, top, right, bottom, listTop, listBottom;
    /** The header's layout for this window and report: the summary's lines, the bar, the key's entries and rows (init). */
    private int summaryLines = 2, barY, searchLeft, sectionRight;
    /** The bar of the tick's height: its key is written inside it. */
    private static final int BAR_H = 11;
    private @Nullable ReportData layoutData;
    private boolean layoutSpikes, layoutFrames, layoutChunks;
    private dev.culprit.net.@Nullable ChunkData layoutChunkData;
    private final ChunksPanel panel = new ChunksPanel();
    /** Each tab's widest entry in its where and last columns for the report shown, or -1 until worked out. */
    private final int[] whereWidth = {-1, -1, -1, -1, -1}, lastWidth = {-1, -1, -1, -1, -1};
    private final List<Button> tabButtons = new ArrayList<>();
    private @Nullable Button overlay, teleport;
    private @Nullable ReportData linesFor;
    private List<Line> tickLines = List.of(), modLines = List.of();
    private @Nullable EditBox search;
    private String query = "";
    /** The current tab's lines the search finds (indices in its whole list), for the report, tab and words found for. */
    private int @Nullable [] matches;
    private @Nullable ReportData matchesFor;
    private int matchesTab = -1;
    private String matchesQuery = "";

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
        // The header follows the window, the report and the view (a new one lays it out again): the title row with the
        // search box at its right, the summary's lines, the bar of the tick with its key inside, then the tabs and the
        // Average / Lag spikes switch.
        layoutData = ClientReport.base();
        layoutSpikes = ClientReport.spikesShown();
        layoutFrames = ClientReport.framesShown();
        layoutChunks = ClientReport.chunksShown();
        layoutChunkData = ClientReport.chunksData();
        ReportData d = ClientReport.view();
        summaryLines = Math.clamp(this.font.split(layoutChunks ? chunksHeadline() : headline(layoutData, d), inner).size(), 1, 3);
        barY = top + 20 + summaryLines * 10 + 2;
        int tabsY = barY + BAR_H + 5;
        listTop = tabsY + 21;
        listBottom = bottom - 28;
        tabButtons.clear();
        if (layoutChunks) {
            // The loaded chunks: the map and the loaders under the headline, no tabs.
            listTop = top + 20 + summaryLines * 10 + 6;
            panel.layout(left + 8, listTop + 2, right - 12, listBottom);
        }
        int viewW = Math.max(this.font.width(viewLabel(false)), this.font.width(viewLabel(true))) + 12;
        int tabW = Math.min(58, (inner - viewW - 4 * (TABS.length + 1)) / TABS.length);
        int x = left + 8;
        for (int i = 0; i < TABS.length && !layoutChunks; i++) {
            int index = i;
            tabButtons.add(addRenderableWidget(Button.builder(tabLabel(i), button -> showTab(index)).bounds(x, tabsY, tabW, 16).build()));
            x += tabW + 4;
        }
        Button viewSwitch = addRenderableWidget(Button.builder(viewLabel(layoutSpikes), b -> {
            ClientReport.showSpikes(!ClientReport.spikesShown());
            scroll = 0;
            picked = -1;
        }).bounds(x, tabsY, viewW, 16).tooltip(Tooltip.create(layoutFrames
                ? tr("view.about_frames", "Average: every frame of the scan. Stutters: only the frames that took over twice the usual frame (and at least 20 ms), to find what makes them slow.")
                : tr("view.about", "Average: every tick of the scan. Lag spikes: only the ticks that took over twice the usual tick (and at least 5 ms), to find what makes them slow."))).build());
        viewSwitch.visible = !layoutChunks;
        // Server ticks / Frames / Loaded chunks, after the title.
        int sx = left + 8 + this.font.width(this.title.copy().withStyle(ChatFormatting.BOLD)) + 8;
        for (int s = 0; s < 3; s++) {
            int section = s;
            Component label = sectionLabel(s);
            int w = this.font.width(label) + 10;
            addRenderableWidget(Button.builder(label, b -> {
                ClientReport.showSection(section);
                if (section == 2 && ClientReport.chunksData() == null) {
                    ClientReport.requestChunks();              // a first look as the section opens
                }
                scroll = 0;
                picked = -1;
            }).bounds(sx, top + 3, w, 14).build());
            sx += w + 3;
        }
        sectionRight = sx;
        int searchW = Math.clamp(inner / 4, 80, 160);
        searchLeft = right - 8 - searchW;
        search = addRenderableWidget(new EditBox(this.font, searchLeft, top + 3, searchW, 14, tr("search", "Search")));
        search.setHint(tr("search.hint", "Search").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            scroll = 0;
            panel.search(text);
        });
        panel.search(query);
        Component[] labels = {layoutChunks ? (layoutChunkData == null ? tr("look", "Look now") : tr("look_again", "Look again"))
                : tr("new_scan", "New scan (%s)", CulpritKeys.SCAN.getTranslatedKeyMessage()), overlayLabel(),
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
            case 0 -> {
                if (ClientReport.chunksShown()) {
                    ClientReport.requestChunks();
                } else {
                    Minecraft.getInstance().gui.setScreen(new ScanScreen());
                }
            }
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
        MutableComponent name = i == 0 && ClientReport.framesShown() ? tr("tab.frame", "Frame") : tr("tab." + TABS[i], TAB_NAMES[i]);
        return i == tab ? name.withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE) : name;
    }

    private void refreshTabs() {
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).setMessage(tabLabel(i));
        }
    }

    private static Component overlayLabel() {
        return Overlay.shown() ? tr("overlay.on", "Hide from the world") : tr("overlay.off", "Show on the world");
    }

    @Override
    public void tick() {
        if (ClientReport.base() != layoutData || ClientReport.spikesShown() != layoutSpikes || ClientReport.framesShown() != layoutFrames
                || ClientReport.chunksShown() != layoutChunks || ClientReport.chunksData() != layoutChunkData) {
            rebuildWidgets();                                  // a new report, or the other view: the header laid out anew
            return;
        }
        if (teleport != null) {
            teleport.active = layoutChunks ? panel.picked() != null : picked >= 0 && markable();
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
        ReportData all = ClientReport.base();
        ReportData d = ClientReport.view();
        if (d != null && linesFor != d) {
            linesFor = d;
            tickLines = tickLines(d);
            modLines = modLines(d);
            java.util.Arrays.fill(whereWidth, -1);
            java.util.Arrays.fill(lastWidth, -1);
        }
        Component status = statusLine(d);
        if (ClientReport.chunksShown()) {
            if (searchLeft - 6 - this.font.width(status) >= sectionRight + 4) {
                g.text(this.font, status, searchLeft - 6 - this.font.width(status), top + 6, DIM);
            }
            int y = top + 20;
            for (FormattedCharSequence line : this.font.split(chunksHeadline(), right - left - 16)) {
                if (y >= top + 20 + summaryLines * 10) {
                    break;
                }
                g.text(this.font, line, left + 8, y, TEXT);
                y += 10;
            }
            panel.draw(g, this.font, mouseX, mouseY);
            return;
        }
        if (searchLeft - 6 - this.font.width(status) >= sectionRight + 4) {
            g.text(this.font, status, searchLeft - 6 - this.font.width(status), top + 6, DIM);
        }
        int inner = right - left - 16;
        if (all == null) {
            int y = listTop + 6;
            Component empty = ClientReport.framesShown()
                    ? tr("empty_frames", "No frame scan yet. Press %s and pick a length under Frames: what each thing in view costs your frame rate shows here.",
                            CulpritKeys.SCAN.getTranslatedKeyMessage())
                    : tr("empty", "No scan yet. Press %s to scan the server (operators); the results show here.", CulpritKeys.SCAN.getTranslatedKeyMessage());
            for (FormattedCharSequence line : this.font.split(empty, inner - 8)) {
                g.text(this.font, line, left + 12, y, DIM);
                y += 10;
            }
            return;
        }
        int y = top + 20;
        for (FormattedCharSequence line : this.font.split(headline(all, d), inner)) {
            if (y >= top + 20 + summaryLines * 10) {
                break;
            }
            g.text(this.font, line, left + 8, y, TEXT);
            y += 10;
        }
        if (d == null) {
            return;                                            // the lag spikes' view of a scan that had none: the headline says so
        }
        bar(g, d, left + 8, barY, inner, mouseX, mouseY);
        rows(g, d, mouseX, mouseY);
    }

    /** The Loaded chunks section's sentence: the dimension's chunks, or why there are none to show. */
    private Component chunksHeadline() {
        if (!ScanScreen.serverHasCulprit()) {
            return tr("chunks_no_server", "This server does not have Culprit: its loaded chunks cannot be looked at.");
        }
        if (ClientReport.state() == dev.culprit.net.CulpritNetwork.Status.DENIED) {
            return tr("chunks_denied", "Only operators can look at this server's loaded chunks.");
        }
        return ChunksPanel.headline(ClientReport.chunksData() == null ? null : panel.dim());
    }

    private Component statusLine(@Nullable ReportData d) {
        if (ClientReport.chunksShown()) {
            if (ClientReport.chunksData() == null) {
                return Component.empty();
            }
            long ago = (System.currentTimeMillis() - ClientReport.chunksReceivedMillis()) / 1000;
            return tr("looked", "Looked %s ago", ago < 60 ? ago + " s" : ago / 60 + " min");
        }
        int secondsLeft = ClientReport.framesShown() ? FrameScan.secondsLeft() : ClientReport.secondsLeft();
        if (secondsLeft >= 0) {
            return tr("scanning", "Scanning the server: %s s left...", secondsLeft).withStyle(ChatFormatting.YELLOW);
        }
        if (d != null && !query.isBlank()) {
            return tr("found", "%s of %s found", visible(d).length, rowCount(d));
        }
        if (d != null) {
            long ago = (System.currentTimeMillis() - ClientReport.baseReceivedMillis()) / 1000;
            return tr("age", "Results from %s ago", ago < 60 ? ago + " s" : ago / 60 + " min");
        }
        return Component.empty();
    }

    private static Component summary(ReportData d) {
        return summaryText(Component.literal(ReportText.time(d.perTick(d.tickNsTotal))).withColor(Heat.rgb(Heat.of(d.perTick(d.tickNsTotal)))),
                ReportText.time(d.tickNsMax), d.ticks);
    }

    /** The sentence over the lists: the whole scan's, its lag spikes', or that it had none. */
    private static Component headline(@Nullable ReportData all, @Nullable ReportData d) {
        if (all == null) {
            return summaryText(Component.literal("99.99 ms"), "99.99 ms", 99999);    // (no report yet: the layout's widest)
        }
        if (all.frames) {
            return frameHeadline(all, d);
        }
        if (!ClientReport.spikesShown()) {
            return summary(all);
        }
        if (d == null) {
            return tr("no_spikes", "No lag spikes in this scan: no tick took over %s (twice the usual %s, and at least 5 ms). The slowest took %s.",
                    ReportText.time(all.spikeThresholdNs), ReportText.time(all.medianTickNs), ReportText.time(all.tickNsMax));
        }
        return tr("spikes_summary", "%s lag spikes: ticks over %s (twice the usual %s, and at least 5 ms). In them the tick took %s on average and %s at worst. Below, what each part took in them.",
                d.ticks, ReportText.time(all.spikeThresholdNs), ReportText.time(all.medianTickNs),
                Component.literal(ReportText.time(d.perTick(d.tickNsTotal))).withColor(Heat.rgb(Heat.of(d.perTick(d.tickNsTotal)))),
                ReportText.time(d.tickNsMax));
    }

    /** A frame scan's sentence over the lists: its frames', its stutters', or that it had none. */
    private static Component frameHeadline(ReportData all, @Nullable ReportData d) {
        if (!ClientReport.spikesShown()) {
            long mean = all.perTick(all.tickNsTotal);
            return tr("frames_summary", "A frame took %s on average (%s FPS) and %s at worst over %s frames. Below, all of it: what each part took.",
                    Component.literal(ReportText.time(mean)).withColor(Heat.rgb(Heat.of(mean / 4))), mean <= 0 ? "-" : String.valueOf(Math.round(1e9 / mean)),
                    ReportText.time(all.tickNsMax), all.ticks);
        }
        if (d == null) {
            return tr("no_stutters", "No stutters in this scan: no frame took over %s (twice the usual %s, and at least 20 ms). The slowest took %s.",
                    ReportText.time(all.spikeThresholdNs), ReportText.time(all.medianTickNs), ReportText.time(all.tickNsMax));
        }
        return tr("stutters_summary", "%s stutters: frames over %s (twice the usual %s, and at least 20 ms). In them a frame took %s on average and %s at worst. Below, what each part took in them.",
                d.ticks, ReportText.time(all.spikeThresholdNs), ReportText.time(all.medianTickNs),
                Component.literal(ReportText.time(d.perTick(d.tickNsTotal))).withColor(Heat.rgb(Heat.of(d.perTick(d.tickNsTotal) / 4))),
                ReportText.time(d.tickNsMax));
    }

    private static Component viewLabel(boolean spikes) {
        if (ClientReport.framesShown()) {
            return spikes ? tr("view.stutters", "Stutters").withStyle(ChatFormatting.GOLD) : tr("view.average", "Average");
        }
        return spikes ? tr("view.spikes", "Lag spikes").withStyle(ChatFormatting.GOLD) : tr("view.average", "Average");
    }

    private static Component sectionLabel(int section) {
        MutableComponent label = switch (section) {
            case 1 -> tr("section.frames", "Frames");
            case 2 -> tr("section.chunks", "Loaded chunks");
            default -> tr("section.ticks", "Server ticks");
        };
        int shown = ClientReport.chunksShown() ? 2 : ClientReport.framesShown() ? 1 : 0;
        return section == shown ? label.withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE) : label;
    }

    private static Component summaryText(Component mean, String worst, int ticks) {
        return tr("summary2", "The server tick took %s on average and %s at worst over %s ticks (a tick has 50 ms). Below, all of it: what each part took.",
                mean, worst, ticks);
    }

    /**
     * The whole tick as one bar, a coloured length per group, each with its name and time per tick written inside when
     * they fit (the time alone when only it does), and all of it in a tooltip over the part under the mouse.
     */
    private void bar(GuiGraphicsExtractor g, ReportData d, int x, int y, int width, int mouseX, int mouseY) {
        long[] groups = Breakdown.groups(d);
        long total = 0;
        for (long ns : groups) {
            total += ns;
        }
        if (total <= 0) {
            return;
        }
        int bx = x;
        Component hovered = null;
        int count = Breakdown.groupCount(d);
        for (int i = 0; i < count; i++) {
            int w = (int) Math.round((double) groups[i] / total * width);
            if (i == count - 1) {
                w = x + width - bx;
            }
            if (w > 0) {
                int colour = Breakdown.colour(d, i);
                g.fill(bx, y, bx + w, y + BAR_H, 0xFF000000 | colour);
                String time = ReportText.time(d.perTick(groups[i]));
                Component full = Breakdown.groupName(d, i).copy().append(" " + time);
                Component label = this.font.width(full) + 6 <= w ? full : this.font.width(time) + 4 <= w ? Component.literal(time) : null;
                if (label != null) {
                    // Dark writing on the light colours, white on the dark ones.
                    int r = colour >> 16 & 0xFF, gr = colour >> 8 & 0xFF, b = colour & 0xFF;
                    int ink = 0.299 * r + 0.587 * gr + 0.114 * b > 140 ? 0xFF101216 : 0xFFFFFFFF;
                    g.text(this.font, label, bx + (w - this.font.width(label)) / 2, y + 2, ink, false);
                }
                if (mouseX >= bx && mouseX < bx + w && mouseY >= y && mouseY < y + BAR_H) {
                    hovered = full;
                }
            }
            bx += w;
        }
        if (hovered != null) {
            g.setTooltipForNextFrame(hovered, mouseX, mouseY);
        }
    }

    // --- the lists ----------------------------------------------------------------------------------------------------

    private List<Line> tickLines(ReportData d) {
        List<Line> lines = new ArrayList<>();
        for (Breakdown.Part p : Breakdown.parts(d)) {
            lines.add(new Line(p.name(), "", p.totalNs(), p.about(), true, p.group(), -1, 0, -1, ""));
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
            String id = d.names[d.modName[i]];
            lines.add(new Line(Component.literal(Names.mod(id)), String.valueOf(d.modCount[i]), d.modTotal[i],
                    Component.literal(place(d.dims[d.modWorstDim[i]], d.modWorstPos[i], d.modWorstKind[i], here())), false, -1,
                    d.modWorstDim[i], d.modWorstPos[i], d.modWorstKind[i], id));
        }
        long game = d.phasesNs() + (d.frames ? 0 : d.kindNs[Kind.CHUNK] + d.kindNs[Kind.SPAWNING]);
        lines.add(new Line(tr("game_work", "The game's own work"), "", game, d.frames
                ? tr("game_work.about_frames", "the world, the HUD, waiting: each in the Frame tab")
                : tr("game_work.about", "chunks, levels, connections: each in the Tick tab"),
                true, -1, -1, 0, -1, "minecraft"));
        lines.add(new Line(Component.translatableWithFallback("culprit.part.self", "Culprit's own timing"), "", d.selfNs,
                Component.translatableWithFallback("culprit.part.about.self", "measuring every call, estimated"), true, -1, -1, 0, -1, "culprit"));
        lines.add(new Line(Component.translatableWithFallback("culprit.part.rest", "Not measured"), "", d.unaccountedNs(),
                Component.translatableWithFallback("culprit.part.about.rest", "tick events and work between the parts"), true, -1, -1, 0, -1, ""));
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
        Component shareHead = d.frames ? tr("col.share_frame", "% of frame") : tr("col.share", "% of tick");
        Component costHead = d.frames ? tr("col.cost_frame", "Per frame") : tr("col.cost", "Per tick");
        int shareW = Math.max(this.font.width("99.9%"), this.font.width(shareHead)) + 8;
        int costW = Math.max(this.font.width("999 µs"), this.font.width(costHead)) + 8;
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
        g.text(this.font, tab == TICK && d.frames ? tr("col.name.frame", "Part of the frame") : tr("col.name." + TABS[tab], NAME_HEADS[tab]),
                cName, y, HEAD);
        if (tab != TICK) {
            g.text(this.font, tr(tab == THINGS ? "col.where" : "col.count", tab == THINGS ? "Where" : "How many"), cWhere, y, HEAD);
        }
        g.text(this.font, costHead, cCost, y, HEAD);
        g.text(this.font, shareHead, cShare, y, HEAD);
        if (roomForLast) {
            g.text(this.font, lastHead, cLast, y, HEAD);
        }
        int[] found = visible(d);
        int n = found.length;
        int rows = visibleRows();
        scroll = Math.clamp(scroll, 0, Math.max(0, n - rows));
        if (n == 0 && !query.isBlank()) {
            g.text(this.font, tr("none_found", "Nothing in this list matches \"%s\".", query), cName, listTop + ROW + 2, DIM);
        }
        for (int r = 0; r < rows && scroll + r < n; r++) {
            int i = found[scroll + r];
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
                    g.fill(nx, ry + 1, nx + 6, ry + 7, 0xFF000000 | Breakdown.colour(d, line.group()));
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

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Enter in the search box picks the first line found (marked, ready to teleport to).
        if (search != null && search.isFocused() && event.isConfirmation()) {
            ReportData d = ClientReport.view();
            if (d != null) {
                int[] found = visible(d);
                if (found.length > 0) {
                    pick(d, found[0]);
                    scroll = 0;
                }
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        // Typing anywhere searches: the box takes the first letter and keeps the rest (its hint stays seen until then).
        if (search != null && !search.isFocused() && event.isAllowedChatCharacter() && !Character.isWhitespace(event.codepoint())) {
            setFocused(search);
            return search.charTyped(event);
        }
        return super.charTyped(event);
    }

    /** Fills the search box as a player would (the Lab's pictures). */
    public void setSearch(String text) {
        if (search != null) {
            search.setValue(text);
        }
    }

    /** The current tab's lines the search finds, by their index in the whole list; all of them with no search. */
    private int[] visible(ReportData d) {
        if (matches != null && matchesFor == d && matchesTab == tab && matchesQuery.equals(query)) {
            return matches;
        }
        int n = rowCount(d);
        String[] words = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        IntArrayList out = new IntArrayList(n);
        for (int i = 0; i < n; i++) {
            if (query.isBlank() || allIn(searchText(d, i), words)) {
                out.add(i);
            }
        }
        matches = out.toIntArray();
        matchesFor = d;
        matchesTab = tab;
        matchesQuery = query;
        return matches;
    }

    private static boolean allIn(String text, String[] words) {
        for (String word : words) {
            if (!text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** What the search looks in for a line: its name as shown and its id, its kind, its place or chunk, its note. */
    private String searchText(ReportData d, int i) {
        String text = switch (tab) {
            case TICK -> tickLines.get(i).name().getString() + " " + tickLines.get(i).note().getString();
            case MODS -> modLines.get(i).name().getString() + " " + modLines.get(i).key() + " " + modLines.get(i).note().getString();
            case TYPES -> Names.of(d.names[d.typeName[i]], d.typeKind[i]) + suffix(d.typeKind[i]) + " " + d.names[d.typeName[i]] + " "
                    + Kind.english(d.typeKind[i]);
            case CHUNKS -> "chunk " + d.chunkX[i] + ", " + d.chunkZ[i] + " " + d.dims[d.chunkDim[i]] + " "
                    + Names.of(d.names[d.chunkTop[i]], d.chunkTopKind[i]) + " " + d.names[d.chunkTop[i]];
            default -> Names.of(d.names[d.type[i]], d.kind[i]) + suffix(d.kind[i]) + " " + d.names[d.type[i]] + " " + Kind.english(d.kind[i]) + " "
                    + place(d.dims[d.dim[i]], d.pos[i], d.kind[i], here());
        };
        return text.toLowerCase(Locale.ROOT);
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
        if (ClientReport.chunksShown() && panel.scroll(x, y, scrollY)) {
            return true;
        }
        if (y >= listTop && y < listBottom) {
            scroll -= (int) Math.signum(scrollY) * 3;
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (ClientReport.chunksShown() && event.button() == 0 && panel.click(event.x(), event.y())) {
            tick();
            return true;
        }
        ReportData d = ClientReport.view();
        if (d != null && event.button() == 0 && event.y() >= listTop + ROW && event.y() < listBottom && event.x() >= left + 6 && event.x() < right - 10) {
            int r = (int) ((event.y() - (listTop + ROW)) / ROW);
            int[] found = visible(d);
            if (r < visibleRows() && scroll + r < found.length) {
                pick(d, found[scroll + r]);
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
        ReportData d = ClientReport.view();
        if (d != null && i >= 0 && i < rowCount(d)) {
            pick(d, i);
        }
    }

    private void teleport() {
        ReportData d = ClientReport.view();
        Minecraft mc = Minecraft.getInstance();
        if (ClientReport.chunksShown()) {
            ChunksPanel.Group loader = panel.picked();
            if (loader != null && mc.getConnection() != null && mc.level != null) {
                String here = mc.level.dimension().identifier().toString();
                BlockPos at = surface((loader.sx() << 4) + 8, (loader.sz() << 4) + 8, here);
                mc.getConnection().sendCommand("execute in " + here + " run tp @s " + (at.getX() + 0.5) + " " + (at.getY() + 1) + " " + (at.getZ() + 0.5));
            }
            return;
        }
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
