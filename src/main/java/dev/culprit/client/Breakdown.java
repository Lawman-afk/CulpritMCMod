package dev.culprit.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.culprit.net.ReportData;
import dev.culprit.scan.Kind;
import dev.culprit.scan.Phase;
import net.minecraft.network.chat.Component;

/**
 * Where a scanned tick went, all of it: the things by kind, the tick's phases, Culprit's own timing and what nothing
 * measured, adding up to the whole tick (DESIGN §4.1). Each part belongs to one of a few groups, coloured in the
 * screen's bar: entities, block entities, blocks, chunks, the server, Culprit, and the rest.
 */
public final class Breakdown {
    public static final int ENTITIES = 0, BLOCK_ENTITIES = 1, BLOCKS = 2, CHUNKS = 3, SERVER = 4, SELF = 5, REST = 6, GROUPS = 7;
    /** The groups' colours (Okabe and Ito's set, told apart by colour-blind players too). */
    public static final int[] COLOUR = {0xE69F00, 0x56B4E9, 0x009E73, 0xF0E442, 0x0072B2, 0xCC79A7, 0x8C8C8C};
    private static final String[] GROUP_KEYS = {"entities", "block_entities", "blocks", "chunks", "server", "self", "rest"};
    private static final String[] GROUP_ENGLISH = {"Entities", "Block entities", "Blocks", "Chunks", "Server", "Culprit's timing", "Not measured"};

    /** One part of the tick: its name, what it is, its time summed over the scan, its group. */
    public record Part(Component name, Component about, long totalNs, int group) {}

    private Breakdown() {}

    public static Component groupName(int group) {
        return Component.translatableWithFallback("culprit.group." + GROUP_KEYS[group], GROUP_ENGLISH[group]);
    }

    /** Every part of the tick, costliest first. */
    public static List<Part> parts(ReportData d) {
        List<Part> parts = new ArrayList<>();
        for (int k = 0; k < Kind.COUNT; k++) {
            int group = switch (k) {
                case Kind.ENTITY -> ENTITIES;
                case Kind.BLOCK_ENTITY -> BLOCK_ENTITIES;
                case Kind.CHUNK, Kind.SPAWNING -> CHUNKS;
                default -> BLOCKS;
            };
            parts.add(new Part(Component.literal(capital(Component.translatableWithFallback("culprit.kind." + Kind.key(k), Kind.english(k)).getString())),
                    Component.translatableWithFallback("culprit.kind.about." + Kind.key(k), Kind.about(k)), d.kindNs[k], group));
        }
        for (int p = 0; p < Phase.COUNT; p++) {
            parts.add(new Part(Component.translatableWithFallback("culprit.phase." + Phase.key(p), capital(Phase.english(p))),
                    Component.translatableWithFallback("culprit.phase.about." + Phase.key(p), Phase.about(p)), d.phaseNs[p],
                    p == Phase.CHUNK_SOURCE ? CHUNKS : SERVER));
        }
        parts.add(new Part(Component.translatableWithFallback("culprit.part.self", "Culprit's own timing"),
                Component.translatableWithFallback("culprit.part.about.self", "measuring every call, estimated"), d.selfNs, SELF));
        parts.add(new Part(Component.translatableWithFallback("culprit.part.rest", "Not measured"),
                Component.translatableWithFallback("culprit.part.about.rest", "tick events and work between the parts"), d.unaccountedNs(), REST));
        parts.sort(Comparator.comparingLong(Part::totalNs).reversed());
        return parts;
    }

    /** Each group's time summed over the scan; with the rest, they make the whole tick. */
    public static long[] groups(ReportData d) {
        long[] g = new long[GROUPS];
        for (Part p : parts(d)) {
            g[p.group()] += p.totalNs();
        }
        return g;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
