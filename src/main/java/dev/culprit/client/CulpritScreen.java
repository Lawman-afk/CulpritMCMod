package dev.culprit.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.culprit.command.ReportText;
import dev.culprit.net.CulpritNetwork;
import dev.culprit.net.ReportData;
import dev.culprit.scan.Kind;
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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Culprit screen (DESIGN §4.4): the last scan's summary and its lists (things, types, mods, chunks) costliest
 * first; a click on a line marks it on the world; buttons to scan, stop, show or hide the overlay and teleport to the
 * marked line (operators). It fits itself to the window: columns are set from the right, the last one dropped when
 * there is no room, the buttons shrunk to fit.
 */
public final class CulpritScreen extends Screen {
    private static final int ROW = 11;
    private static final int PANEL = 0xEC101216, EDGE = 0xFF3A3F4A, ROW_DARK = 0x22FFFFFF, ROW_HOVER = 0x3C7FB2FF, ROW_PICKED = 0x6066A8FF;
    private static final int TEXT = 0xFFFFFFFF, DIM = 0xFFA8AFBA, HEAD = 0xFFFFC34D;
    private static final String[] TABS = {"things", "types", "mods", "chunks"};
    private static final String[] TAB_NAMES = {"Things", "Types", "Mods", "Chunks"};

    private static int tab;
    private int scroll;
    private int picked = -1;
    private int left, top, right, bottom, listTop, listBottom;
    private final List<Button> tabButtons = new ArrayList<>();
    private @Nullable Button scan10, scan30, stop, overlay, teleport;

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
        listTop = top + 82;
        listBottom = bottom - 28;
        tabButtons.clear();
        int tabW = Math.min(64, (inner - 12) / 4);
        int x = left + 8;
        for (int i = 0; i < TABS.length; i++) {
            int index = i;
            tabButtons.add(addRenderableWidget(Button.builder(tabLabel(i), button -> showTab(index)).bounds(x, top + 61, tabW, 16).build()));
            x += tabW + 4;
        }
        // The buttons along the bottom, each as wide as its words, shrunk together if the window is narrow.
        Component[] labels = {tr("scan10", "Scan 10 s"), tr("scan30", "Scan 30 s"), tr("stop", "Stop"), overlayLabel(), tr("teleport", "Teleport there"),
                tr("done", "Done")};
        int[] widths = new int[labels.length];
        int sum = 0;
        for (int i = 0; i < labels.length; i++) {
            widths[i] = this.font.width(labels[i]) + 14;
            sum += widths[i];
        }
        int gaps = 4 * (labels.length - 1);
        float shrink = Math.min(1f, (inner - gaps) / (float) sum);
        int bx = left + 8, by = bottom - 23;
        Button[] made = new Button[labels.length];
        for (int i = 0; i < labels.length; i++) {
            int w = Math.max(24, (int) (widths[i] * shrink));
            int index = i;
            made[i] = addRenderableWidget(Button.builder(labels[i], b -> press(index, b)).bounds(bx, by, w, 18).build());
            bx += w + 4;
        }
        scan10 = made[0];
        scan30 = made[1];
        stop = made[2];
        overlay = made[3];
        teleport = made[4];
        refreshTabs();
        tick();
    }

    private void press(int index, Button button) {
        switch (index) {
            case 0 -> request(10);
            case 1 -> request(30);
            case 2 -> request(0);
            case 3 -> {
                Overlay.setShown(!Overlay.shown());
                button.setMessage(overlayLabel());
            }
            case 4 -> teleport();
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
        Minecraft mc = Minecraft.getInstance();
        boolean server = mc.getConnection() != null && mc.getConnection().hasChannel(CulpritNetwork.ScanRequest.TYPE);
        boolean running = ClientReport.secondsLeft() >= 0;
        if (scan10 != null) {
            scan10.active = server && !running;
            scan30.active = server && !running;
            stop.active = server && running;
            teleport.active = picked >= 0;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void request(int seconds) {
        ClientPacketDistributor.sendToServer(new CulpritNetwork.ScanRequest(seconds));
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
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        g.text(this.font, title, left + 8, top + 6, HEAD);
        ReportData d = ClientReport.data();
        Component status = statusLine(d);
        g.text(this.font, status, right - 8 - this.font.width(status), top + 6, DIM);
        int inner = right - left - 16;
        if (d == null) {
            int y = listTop + 6;
            for (FormattedCharSequence line : this.font.split(tr("empty",
                    "No scan yet. Operators can press Scan 10 s: the results show here and on the world."), inner - 8)) {
                g.text(this.font, line, left + 12, y, DIM);
                y += 10;
            }
            return;
        }
        int y = top + 18;
        for (FormattedCharSequence line : this.font.split(summary(d), inner)) {
            if (y > top + 28) {
                break;
            }
            g.text(this.font, line, left + 8, y, TEXT);
            y += 10;
        }
        kinds(g, d, top + 38, inner);
        rows(g, d, mouseX, mouseY);
    }

    private Component statusLine(@Nullable ReportData d) {
        int secondsLeft = ClientReport.secondsLeft();
        byte state = ClientReport.state();
        if (secondsLeft >= 0) {
            return tr("scanning", "Scanning the server: %s s left...", secondsLeft).withStyle(ChatFormatting.YELLOW);
        }
        if (state == CulpritNetwork.Status.DENIED) {
            return tr("denied", "Only operators can scan this server.").withStyle(ChatFormatting.RED);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || !mc.getConnection().hasChannel(CulpritNetwork.ScanRequest.TYPE)) {
            return tr("no_server", "This server does not have Culprit: its ticks cannot be scanned.");
        }
        if (d != null) {
            long ago = (System.currentTimeMillis() - ClientReport.receivedMillis()) / 1000;
            return tr("age", "Results from %s ago", ago < 60 ? ago + " s" : ago / 60 + " min");
        }
        return Component.empty();
    }

    private static Component summary(ReportData d) {
        return tr("summary", "The server tick took %s on average and %s at worst over %s ticks (a tick has 50 ms). Culprit measured %s of it a tick, in %s things.",
                Component.literal(ReportText.time(d.perTick(d.tickNsTotal))).withColor(Heat.rgb(Heat.of(d.perTick(d.tickNsTotal)))),
                ReportText.time(d.tickNsMax), d.ticks, ReportText.time(d.perTick(d.measuredNs())), d.thingCount);
    }

    /** The kinds' totals in three columns, costliest first. */
    private void kinds(GuiGraphicsExtractor g, ReportData d, int y, int inner) {
        Integer[] order = {0, 1, 2, 3, 4, 5};
        java.util.Arrays.sort(order, (a, b) -> Long.compare(d.kindNs[b], d.kindNs[a]));
        int colW = inner / 3;
        for (int n = 0; n < order.length; n++) {
            int k = order[n];
            long perTick = d.perTick(d.kindNs[k]);
            int x = left + 8 + (n % 3) * colW, ry = y + (n / 3) * 10;
            Component name = Component.translatableWithFallback("culprit.kind." + Kind.key(k), Kind.english(k));
            String cost = ReportText.time(perTick);
            g.text(this.font, name, x, ry, DIM);
            g.text(this.font, cost, x + colW - 8 - this.font.width(cost), ry, Heat.text(perTick));
        }
    }

    private int rowCount(ReportData d) {
        return switch (tab) {
            case 1 -> d.types;
            case 2 -> d.mods;
            case 3 -> d.chunks;
            default -> d.things;
        };
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop - ROW) / ROW);
    }

    private void rows(GuiGraphicsExtractor g, ReportData d, int mouseX, int mouseY) {
        String here = Minecraft.getInstance().level == null ? "" : Minecraft.getInstance().level.dimension().identifier().toString();
        int x0 = left + 8, end = right - 12;
        // Columns from the right: worst (or worst at, or mostly), share, per tick; then where (or how many); the name
        // takes the rest. The last one goes when the window is narrow.
        int lastW = switch (tab) {
            case 0 -> this.font.width("9999 µs") + 6;
            case 3 -> this.font.width("redstone_wire") + 6;
            default -> this.font.width("-29999, 319, -29999") + 6;
        };
        int shareW = this.font.width("99.9%") + 8, costW = this.font.width("999 µs") + 10;
        int whereW = tab == 0 ? this.font.width("-29999, 319, -29999") + 8
                : Math.max(this.font.width("99999"), this.font.width(tr("col.count", "How many"))) + 10;
        boolean roomForLast = end - x0 - lastW - shareW - costW - whereW > 130;
        int cLast = roomForLast ? end - lastW : end;
        int cShare = cLast - shareW, cCost = cShare - costW, cWhere = cCost - whereW, cName = x0 + this.font.width("999") + 6;

        int y = listTop + 2;
        g.text(this.font, tr("col.name", tab == 2 ? "Mod" : tab == 3 ? "Chunk" : tab == 1 ? "Type" : "What"), cName, y, HEAD);
        g.text(this.font, tr(tab == 0 ? "col.where" : "col.count", tab == 0 ? "Where" : "How many"), cWhere, y, HEAD);
        g.text(this.font, tr("col.cost", "Per tick"), cCost, y, HEAD);
        g.text(this.font, tr("col.share", "Share"), cShare, y, HEAD);
        if (roomForLast) {
            g.text(this.font, tr(tab == 0 ? "col.worst" : tab == 3 ? "col.mostly" : "col.worst_at", tab == 0 ? "Worst" : tab == 3 ? "Mostly" : "Worst at"),
                    cLast, y, HEAD);
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
            String name, where, last;
            switch (tab) {
                case 1 -> {
                    total = d.typeTotal[i];
                    name = Overlay.shortName(d.names[d.typeName[i]]) + (d.typeKind[i] == Kind.BLOCK_ENTITY || d.typeKind[i] == Kind.ENTITY
                            || d.typeKind[i] == Kind.CHUNK ? "" : "  (" + Kind.englishOne(d.typeKind[i]) + ")");
                    where = String.valueOf(d.typeCount[i]);
                    last = place(d.dims[d.typeWorstDim[i]], d.typeWorstPos[i], here);
                }
                case 2 -> {
                    total = d.modTotal[i];
                    name = d.names[d.modName[i]];
                    where = String.valueOf(d.modCount[i]);
                    last = place(d.dims[d.modWorstDim[i]], d.modWorstPos[i], here);
                }
                case 3 -> {
                    total = d.chunkTotal[i];
                    String dim = d.dims[d.chunkDim[i]];
                    name = d.chunkX[i] + ", " + d.chunkZ[i] + (dim.equals(here) ? "" : "  " + shortDim(dim));
                    where = String.valueOf(d.chunkCount[i]);
                    last = Overlay.shortName(d.names[d.chunkTop[i]]);
                }
                default -> {
                    total = d.total[i];
                    name = Overlay.shortName(d.names[d.type[i]]) + (d.kind[i] == Kind.BLOCK_ENTITY || d.kind[i] == Kind.ENTITY ? ""
                            : "  (" + Kind.englishOne(d.kind[i]) + ")");
                    where = place(d.dims[d.dim[i]], d.pos[i], here);
                    last = ReportText.time(d.max[i]);
                }
            }
            long perTick = d.perTick(total);
            g.text(this.font, clip(name, cWhere - cName - 6), cName, ry, TEXT);
            g.text(this.font, clip(where, cCost - cWhere - 6), cWhere, ry, DIM);
            g.text(this.font, ReportText.time(perTick), cCost, ry, Heat.text(perTick));
            g.text(this.font, ReportText.share(perTick), cShare, ry, DIM);
            if (roomForLast) {
                g.text(this.font, clip(last, end - cLast), cLast, ry, DIM);
            }
        }
        if (n > rows) {
            int barTop = listTop + ROW, barH = listBottom - barTop;
            int knob = Math.max(8, barH * rows / n);
            int knobY = barTop + (barH - knob) * scroll / Math.max(1, n - rows);
            g.fill(right - 8, barTop, right - 6, listBottom, 0x30FFFFFF);
            g.fill(right - 8, knobY, right - 6, knobY + knob, 0xA0FFFFFF);
        }
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

    /** A place as "x, y, z", with its dimension when it is not the one the player is in. */
    private static String place(String dim, long packed, String here) {
        BlockPos p = BlockPos.of(packed);
        return p.getX() + ", " + p.getY() + ", " + p.getZ() + (dim.equals(here) ? "" : "  " + shortDim(dim));
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

    private void pick(ReportData d, int i) {
        picked = i;
        switch (tab) {
            case 1 -> Overlay.markPlace(d.typeWorstDim[i], d.typeWorstPos[i],
                    Overlay.shortName(d.names[d.typeName[i]]) + "  " + ReportText.time(d.perTick(d.typeTotal[i])) + "/t");
            case 2 -> Overlay.markPlace(d.modWorstDim[i], d.modWorstPos[i], d.names[d.modName[i]] + "  " + ReportText.time(d.perTick(d.modTotal[i])) + "/t");
            case 3 -> Overlay.markChunk(i);
            default -> Overlay.markThing(i);
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
        if (d == null || picked < 0 || mc.getConnection() == null || mc.player == null) {
            return;
        }
        String dim;
        BlockPos p;
        switch (tab) {
            case 1 -> {
                dim = d.dims[d.typeWorstDim[picked]];
                p = BlockPos.of(d.typeWorstPos[picked]);
            }
            case 2 -> {
                dim = d.dims[d.modWorstDim[picked]];
                p = BlockPos.of(d.modWorstPos[picked]);
            }
            case 3 -> {
                dim = d.dims[d.chunkDim[picked]];
                p = new BlockPos((d.chunkX[picked] << 4) + 8, (int) Math.floor(mc.player.getY()), (d.chunkZ[picked] << 4) + 8);
            }
            default -> {
                dim = d.dims[d.dim[picked]];
                p = BlockPos.of(d.pos[picked]);
            }
        }
        mc.getConnection().sendCommand("execute in " + dim + " run tp @s " + (p.getX() + 0.5) + " " + (p.getY() + 1) + " " + (p.getZ() + 0.5));
    }
}
