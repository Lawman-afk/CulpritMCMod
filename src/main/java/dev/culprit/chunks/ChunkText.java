package dev.culprit.chunks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.culprit.net.ChunkData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The chunk inspector's findings as text (DESIGN §4.4): for consoles and players without the mod, per dimension how
 * many chunks are loaded and how active, and what keeps them loaded, the chunk loaders (mods, /forceload, pearls...)
 * named with how many chunks each keeps.
 */
public final class ChunkText {
    private static final String[] REASON_KEYS = {"players", "mods", "forced", "portals", "pearls", "spawn", "other"};
    private static final String[] REASON_ENGLISH = {"players", "mods", "/forceload", "portals", "ender pearls", "the world spawn", "other"};

    private ChunkText() {}

    public static Component reasonName(int reason) {
        return Component.translatableWithFallback("culprit.chunks.reason." + REASON_KEYS[reason], REASON_ENGLISH[reason]);
    }

    public static Component summary(ChunkData data) {
        MutableComponent all = Component.empty();
        for (ChunkData.Dim d : data.dims) {
            if (d.total == 0) {
                continue;
            }
            int[] status = new int[3];
            int[] byReason = new int[ChunkData.REASONS];
            for (int i = 0; i < d.chunks; i++) {
                status[d.status[i]]++;
                byReason[d.reason[i]]++;
            }
            MutableComponent reasons = Component.empty();
            for (int r = 0; r < ChunkData.REASONS; r++) {
                if (byReason[r] > 0) {
                    reasons.append(reasons.getSiblings().isEmpty() ? "" : ", ").append(reasonName(r)).append(" " + byReason[r]);
                }
            }
            if (!all.getSiblings().isEmpty()) {
                all.append("\n");
            }
            all.append(Component.translatableWithFallback("culprit.chunks.summary",
                    "%s: %s chunks loaded (%s ticking entities, %s ticking blocks, %s at the border; %s more at the edges). Kept loaded by: %s.",
                    shortDim(d.dim), d.total, status[ChunkData.ENTITY_TICKING], status[ChunkData.BLOCK_TICKING], status[ChunkData.BORDER], d.edges,
                    reasons).withStyle(ChatFormatting.GOLD));
            for (String line : loaders(d, 8)) {
                all.append("\n").append(Component.literal("  " + line).withStyle(ChatFormatting.GRAY));
            }
        }
        if (all.getSiblings().isEmpty()) {
            all.append(Component.translatableWithFallback("culprit.chunks.none", "No chunks are loaded."));
        }
        return all;
    }

    /** The loaders that are not players, with how many chunks each keeps playable: "create:... (x y z): 9 chunks". */
    public static List<String> loaders(ChunkData.Dim d, int max) {
        Map<String, int[]> counts = new HashMap<>();
        for (int s = 0; s < d.sources; s++) {
            if (d.sreason[s] == ChunkData.PLAYERS || d.sowner[s].equals(ChunkCensus.PASSING)) {
                continue;                                   // (players' areas are expected; passing requests go a tick later)
            }
            String key = d.sowner[s] + " at chunk " + d.sx[s] + ", " + d.sz[s];
            int reach = d.reach(s);
            counts.computeIfAbsent(key, k -> new int[1])[0] = Math.max(counts.get(key) == null ? 0 : counts.get(key)[0], (2 * reach + 1) * (2 * reach + 1));
        }
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(max, sorted.size()); i++) {
            int n = sorted.get(i).getValue()[0];
            lines.add(sorted.get(i).getKey() + ": up to " + n + (n == 1 ? " chunk" : " chunks"));
        }
        if (sorted.size() > max) {
            lines.add("and " + (sorted.size() - max) + " more");
        }
        return lines;
    }

    private static String shortDim(String dim) {
        return dim.startsWith("minecraft:") ? dim.substring(10) : dim;
    }
}
