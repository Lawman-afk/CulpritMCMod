package dev.culprit.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.culprit.chunks.ChunkText;
import dev.culprit.net.ChunkData;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import dev.culprit.port.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

/**
 * The results' Loaded chunks section (DESIGN §4.3): a map of the dimension's loaded chunks round the player, each one
 * coloured by why it is loaded and shaded by how busy it is (ticking entities, ticking blocks, the border), the player's
 * chunk outlined; beside it what keeps them loaded, each loader with the chunks it is the strongest for, mods and
 * forced chunks first. A click on a loader marks its chunks on the map and the world; the search box narrows the list.
 */
final class ChunksPanel {
    /** The reasons' colours (Okabe and Ito's set): players, mods, /forceload, portals, ender pearls, the spawn, other. */
    static final int[] COLOUR = {0x56B4E9, 0xE69F00, 0xD55E00, 0xCC79A7, 0x009E73, 0xF0E442, 0x8C8C8C, 0xC8C8C8};
    private static final int ROW = 11, TEXT = 0xFFFFFFFF, DIM = 0xFFA8AFBA, HEAD = 0xFFFFC34D;

    /** A loader: why, who or what, its chunk, how many chunks it keeps loaded, and the sources it gathers. */
    record Group(byte reason, String owner, int sx, int sz, int chunks, List<Integer> sources) {}

    private ChunkData.@Nullable Dim dim;
    private @Nullable ChunkData groupsFor;
    private List<Group> groups = List.of();
    private final Long2IntOpenHashMap chunkIndex = new Long2IntOpenHashMap();
    private @Nullable Group picked;
    private int scroll;
    private int x0, y0, x1, y1, mapSize, listX;
    private String query = "";

    ChunksPanel() {
        chunkIndex.defaultReturnValue(-1);
    }

    void layout(int x0, int y0, int x1, int y1) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        mapSize = Math.max(40, Math.min(y1 - y0, (x1 - x0) * 2 / 5));
        listX = x0 + mapSize + 8;
    }

    void search(String query) {
        if (!this.query.equals(query)) {
            this.query = query;
            scroll = 0;
        }
    }

    @Nullable Group picked() {
        return picked;
    }

    /** The player's dimension's findings, and its loaders worked out once a report. */
    ChunkData.@Nullable Dim dim() {
        ChunkData data = ClientReport.chunksData();
        Minecraft mc = Minecraft.getInstance();
        if (data == null || mc.level == null) {
            return null;
        }
        if (data != groupsFor) {
            groupsFor = data;
            picked = null;
            String here = mc.level.dimension().location().toString();
            dim = data.dims.stream().filter(d -> d.dim.equals(here)).findFirst().orElse(null);
            groups = dim == null ? List.of() : groups(dim);
            chunkIndex.clear();
            if (dim != null) {
                for (int i = 0; i < dim.chunks; i++) {
                    chunkIndex.put(ChunkPos.asLong(dim.x[i], dim.z[i]), i);
                }
            }
        }
        return dim;
    }

    /** The loaders: sources with the same reason, owner and chunk as one, each with the chunks it keeps loaded. */
    private static List<Group> groups(ChunkData.Dim d) {
        int[] reached = new int[d.sources];
        for (int i = 0; i < d.chunks; i++) {
            for (int s = 0; s < d.sources; s++) {
                if (Math.max(Math.abs(d.x[i] - d.sx[s]), Math.abs(d.z[i] - d.sz[s])) <= d.reach(s)) {
                    reached[s]++;
                }
            }
        }
        Map<String, List<Integer>> bySame = new HashMap<>();
        for (int s = 0; s < d.sources; s++) {
            // A player's tickets are one row, as are the passing requests of each who asked, wherever they are; any other
            // loader one per chunk (two of a mod's machines are two loaders).
            boolean anywhere = d.sreason[s] == ChunkData.PLAYERS || d.sreason[s] == ChunkData.REQUESTS;
            bySame.computeIfAbsent(d.sreason[s] + "|" + d.sowner[s] + (anywhere ? "" : "|" + d.sx[s] + "|" + d.sz[s]), k -> new ArrayList<>()).add(s);
        }
        List<Group> out = new ArrayList<>();
        for (List<Integer> same : bySame.values()) {
            int first = same.getFirst(), chunks = 0;
            if (same.size() == 1) {
                chunks = reached[first];
            } else {
                for (int i = 0; i < d.chunks; i++) {
                    for (int s : same) {
                        if (Math.max(Math.abs(d.x[i] - d.sx[s]), Math.abs(d.z[i] - d.sz[s])) <= d.reach(s)) {
                            chunks++;
                            break;
                        }
                    }
                }
            }
            out.add(new Group(d.sreason[first], d.sowner[first], d.sx[first], d.sz[first], chunks, same));
        }
        // Mods and forced chunks first (what stays loaded when everyone leaves), then players, then the passing requests.
        out.sort((a, b) -> {
            int ra = rank(a), rb = rank(b);
            return ra != rb ? Integer.compare(ra, rb) : Integer.compare(b.chunks, a.chunks);
        });
        return out;
    }

    private static int rank(Group group) {
        return group.owner.equals(dev.culprit.chunks.ChunkCensus.PASSING) ? 2 : group.reason == ChunkData.PLAYERS ? 1 : 0;
    }

    private List<Group> shown() {
        if (query.isBlank()) {
            return groups;
        }
        String[] words = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<Group> out = new ArrayList<>();
        for (Group group : groups) {
            String text = (group.owner + " " + ChunkText.reasonName(group.reason).getString() + " chunk " + group.sx + ", " + group.sz).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) {
                all &= text.contains(w);
            }
            if (all) {
                out.add(group);
            }
        }
        return out;
    }

    /** Whether the picked loader keeps a chunk loaded. */
    private boolean inPicked(ChunkData.Dim d, int x, int z) {
        if (picked == null) {
            return false;
        }
        for (int s : picked.sources) {
            if (Math.max(Math.abs(x - d.sx[s]), Math.abs(z - d.sz[s])) <= d.reach(s)) {
                return true;
            }
        }
        return false;
    }

    void draw(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        ChunkData.Dim d = dim();
        if (d == null) {
            return;
        }
        drawMap(g, d, mouseX, mouseY);
        drawList(g, font, d, mouseX, mouseY);
    }

    private void drawMap(GuiGraphicsExtractor g, ChunkData.Dim d, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int pcx = mc.player == null ? 0 : mc.player.chunkPosition().x, pcz = mc.player == null ? 0 : mc.player.chunkPosition().z;
        int radius = 4;
        for (int i = 0; i < d.chunks; i++) {
            radius = Math.max(radius, Math.max(Math.abs(d.x[i] - pcx), Math.abs(d.z[i] - pcz)));
        }
        radius = Math.min(radius, 96);
        int cells = 2 * radius + 1;
        int cell = Math.max(1, mapSize / cells);
        int size = cell * cells, mx = x0 + (mapSize - size) / 2, my = y0 + (mapSize - size) / 2;
        g.fill(mx - 1, my - 1, mx + size + 1, my + size + 1, 0xFF0A0B0D);
        int hovered = -1;
        for (int i = 0; i < d.chunks; i++) {
            int cx = d.x[i] - pcx + radius, cz = d.z[i] - pcz + radius;
            if (cx < 0 || cz < 0 || cx >= cells || cz >= cells) {
                continue;
            }
            int alpha = d.status[i] == ChunkData.ENTITY_TICKING ? 0xFF : d.status[i] == ChunkData.BLOCK_TICKING ? 0xB0 : 0x60;
            boolean dimmed = picked != null && !inPicked(d, d.x[i], d.z[i]);
            int colour = (dimmed ? alpha / 4 : alpha) << 24 | COLOUR[d.reason[i]];
            int px = mx + cx * cell, py = my + cz * cell;
            g.fill(px, py, px + cell, py + cell, colour);
            if (mouseX >= px && mouseX < px + cell && mouseY >= py && mouseY < py + cell) {
                hovered = i;
            }
        }
        // The player's chunk.
        int ppx = mx + radius * cell, ppy = my + radius * cell;
        g.outline(ppx - 1, ppy - 1, cell + 2, cell + 2, 0xFFFFFFFF);
        if (hovered >= 0) {
            g.setTooltipForNextFrame(tooltip(d, hovered), mouseX, mouseY);
        }
    }

    private static Component tooltip(ChunkData.Dim d, int i) {
        Component status = switch (d.status[i]) {
            case ChunkData.ENTITY_TICKING -> Component.translatableWithFallback("culprit.chunks.status.entity", "ticking entities and blocks");
            case ChunkData.BLOCK_TICKING -> Component.translatableWithFallback("culprit.chunks.status.block", "ticking blocks");
            default -> Component.translatableWithFallback("culprit.chunks.status.border", "at the border: loaded, not ticking");
        };
        String by = d.source[i] >= 0 ? d.sowner[d.source[i]] : "?";
        // The reason after the loader, unless the loader's name says it ("/forceload by Steve").
        String reason = ChunkText.reasonName(d.reason[i]).getString();
        String kind = by.toLowerCase(Locale.ROOT).startsWith(reason.toLowerCase(Locale.ROOT)) ? "" : " (" + reason + ")";
        StringBuilder also = new StringBuilder();
        for (int r = 0; r < ChunkData.REASONS; r++) {
            if (r != d.reason[i] && (d.reasons[i] & (1 << r)) != 0) {
                also.append(also.isEmpty() ? "" : ", ").append(ChunkText.reasonName(r).getString());
            }
        }
        return Component.translatableWithFallback("culprit.chunks.tooltip", "Chunk %s, %s: %s\nKept loaded by %s%s%s\n%s block entities, %s entities",
                d.x[i], d.z[i], status, by, kind,
                also.isEmpty() ? "" : Component.translatableWithFallback("culprit.chunks.also", "; also %s", also.toString()).getString(),
                d.blockEntities[i], d.entities[i]);
    }

    private void drawList(GuiGraphicsExtractor g, Font font, ChunkData.Dim d, int mouseX, int mouseY) {
        int y = y0;
        int countX = x1 - font.width("99999") - 4;
        g.text(font, Component.translatableWithFallback("culprit.chunks.col.loader", "What keeps them loaded"), listX + 9, y, HEAD);
        g.text(font, Component.translatableWithFallback("culprit.chunks.col.chunks", "Chunks"), countX - 8, y, HEAD);
        List<Group> shown = shown();
        int rows = Math.max(1, (y1 - y0 - ROW) / ROW);
        scroll = Math.clamp(scroll, 0, Math.max(0, shown.size() - rows));
        for (int r = 0; r < rows && scroll + r < shown.size(); r++) {
            Group group = shown.get(scroll + r);
            int ry = y0 + ROW + 2 + r * ROW;
            boolean hover = mouseX >= listX && mouseX < x1 && mouseY >= ry - 1 && mouseY < ry + ROW - 1;
            if (group == picked) {
                g.fill(listX - 2, ry - 2, x1, ry + ROW - 2, 0x6066A8FF);
            } else if (hover) {
                g.fill(listX - 2, ry - 2, x1, ry + ROW - 2, 0x3C7FB2FF);
            } else if (r % 2 == 1) {
                g.fill(listX - 2, ry - 2, x1, ry + ROW - 2, 0x22FFFFFF);
            }
            g.fill(listX, ry + 1, listX + 6, ry + 7, 0xFF000000 | COLOUR[group.reason]);
            String name = group.reason == ChunkData.PLAYERS ? group.owner + " (" + ChunkText.reasonName(group.reason).getString() + ")" : group.owner;
            g.text(font, clip(font, name, countX - listX - 20), listX + 9, ry, TEXT);
            g.text(font, String.valueOf(group.chunks), countX, ry, DIM);
        }
        if (shown.isEmpty()) {
            g.text(font, Component.translatableWithFallback("culprit.chunks.no_loaders", "Nothing here matches."), listX + 9, y0 + ROW + 2, DIM);
        }
    }

    private static String clip(Font font, String s, int width) {
        if (font.width(s) <= width) {
            return s;
        }
        return font.plainSubstrByWidth(s, Math.max(0, width - font.width("..."))) + "...";
    }

    /** A click in the list picks a loader (again, lets it go); true if it was the panel's. */
    boolean click(double x, double y) {
        if (x < listX || x >= x1 || y < y0 + ROW || y >= y1) {
            return false;
        }
        int r = (int) ((y - (y0 + ROW)) / ROW);
        List<Group> shown = shown();
        if (scroll + r < shown.size()) {
            Group group = shown.get(scroll + r);
            picked = group == picked ? null : group;
            Overlay.markLoader(picked == null ? null : picked.sources);
            return true;
        }
        return false;
    }

    boolean scroll(double x, double y, double amount) {
        if (x >= listX && x < x1 && y >= y0 && y < y1) {
            scroll -= (int) Math.signum(amount) * 3;
            return true;
        }
        return false;
    }

    /** The headline for the section: the dimension's chunks, how busy, and what keeps them loaded. */
    static Component headline(ChunkData.@Nullable Dim d) {
        if (d == null) {
            return Component.translatableWithFallback("culprit.chunks.empty",
                    "Press Look now to see every loaded chunk of this dimension, how busy it is, and what keeps it loaded.");
        }
        int[] status = new int[3];
        int[] byReason = new int[ChunkData.REASONS];
        for (int i = 0; i < d.chunks; i++) {
            status[d.status[i]]++;
            byReason[d.reason[i]]++;
        }
        StringBuilder reasons = new StringBuilder();
        for (int r = 0; r < ChunkData.REASONS; r++) {
            if (byReason[r] > 0) {
                reasons.append(reasons.isEmpty() ? "" : ", ").append(ChunkText.reasonName(r).getString()).append(' ').append(byReason[r]);
            }
        }
        return Component.translatableWithFallback("culprit.chunks.headline",
                "%s chunks loaded here: %s ticking entities, %s ticking blocks, %s at the border (and %s more at the edges). Kept loaded by %s.",
                d.total, status[ChunkData.ENTITY_TICKING], status[ChunkData.BLOCK_TICKING], status[ChunkData.BORDER], d.edges, reasons.toString());
    }
}
