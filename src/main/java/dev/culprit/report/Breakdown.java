package dev.culprit.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.culprit.net.ReportData;
import dev.culprit.scan.FramePhase;
import dev.culprit.scan.Kind;
import dev.culprit.scan.Phase;
import net.minecraft.network.chat.Component;

/**
 * Where a scanned tick (or frame) went, all of it: the things by kind, the parts of the tick or frame, Culprit's own
 * timing and what nothing measured, adding up to the whole (DESIGN §4.1, §4.2). Each part belongs to one of a few
 * groups, coloured in the results' bar: for a tick, entities, block entities, blocks, chunks, the server, Culprit and
 * the rest; for a frame, entities, block entities, the world, the client, the HUD and mods, waiting, Culprit and the rest.
 */
public final class Breakdown {
    public static final int ENTITIES = 0, BLOCK_ENTITIES = 1, BLOCKS = 2, CHUNKS = 3, SERVER = 4, SELF = 5, REST = 6, GROUPS = 7;
    public static final int F_ENTITIES = 0, F_BLOCK_ENTITIES = 1, F_WORLD = 2, F_CLIENT = 3, F_SCREEN = 4, F_WAITING = 5, F_SELF = 6, F_REST = 7,
            FRAME_GROUPS = 8;
    /** The groups' colours (Okabe and Ito's set, told apart by colour-blind players too). */
    private static final int[] COLOUR = {0xE69F00, 0x56B4E9, 0x009E73, 0xF0E442, 0x0072B2, 0xCC79A7, 0x8C8C8C};
    private static final int[] FRAME_COLOUR = {0xE69F00, 0x56B4E9, 0x009E73, 0xF0E442, 0x0072B2, 0xD55E00, 0xCC79A7, 0x8C8C8C};
    private static final String[] GROUP_KEYS = {"entities", "block_entities", "blocks", "chunks", "server", "self", "rest"};
    private static final String[] GROUP_ENGLISH = {"Entities", "Block entities", "Blocks", "Chunks", "Server", "Culprit's timing", "Not measured"};
    private static final String[] FRAME_GROUP_KEYS = {"entities", "block_entities", "world", "client", "screen", "waiting", "self", "rest"};
    private static final String[] FRAME_GROUP_ENGLISH = {"Entities", "Block entities", "World", "Client", "HUD and mods", "Waiting",
            "Culprit's timing", "Not measured"};

    /** One part: its name, what it is, its time summed over the scan, its group. */
    public record Part(Component name, Component about, long totalNs, int group) {}

    private Breakdown() {}

    public static int groupCount(ReportData d) {
        return d.frames ? FRAME_GROUPS : GROUPS;
    }

    public static int colour(ReportData d, int group) {
        return d.frames ? FRAME_COLOUR[group] : COLOUR[group];
    }

    public static Component groupName(ReportData d, int group) {
        return d.frames ? Component.translatableWithFallback("culprit.group.frame." + FRAME_GROUP_KEYS[group], FRAME_GROUP_ENGLISH[group])
                : Component.translatableWithFallback("culprit.group." + GROUP_KEYS[group], GROUP_ENGLISH[group]);
    }

    /** Every part, costliest first. */
    public static List<Part> parts(ReportData d) {
        List<Part> parts = d.frames ? frameParts(d) : tickParts(d);
        parts.add(new Part(Component.translatableWithFallback("culprit.part.self", "Culprit's own timing"),
                Component.translatableWithFallback("culprit.part.about.self", "measuring every call, estimated"), d.selfNs,
                d.frames ? F_SELF : SELF));
        parts.add(new Part(Component.translatableWithFallback("culprit.part.rest", "Not measured"),
                Component.translatableWithFallback(d.frames ? "culprit.part.about.rest_frame" : "culprit.part.about.rest",
                        d.frames ? "work between the parts" : "tick events and work between the parts"), d.unaccountedNs(), d.frames ? F_REST : REST));
        parts.sort(Comparator.comparingLong(Part::totalNs).reversed());
        return parts;
    }

    private static List<Part> tickParts(ReportData d) {
        List<Part> parts = new ArrayList<>();
        for (int k = 0; k < Kind.COUNT; k++) {
            if (k == Kind.PARTICLE) {
                continue;                                   // (a frame's, not a tick's)
            }
            int group = switch (k) {
                case Kind.ENTITY -> ENTITIES;
                case Kind.BLOCK_ENTITY -> BLOCK_ENTITIES;
                case Kind.CHUNK, Kind.SPAWNING -> CHUNKS;
                default -> BLOCKS;
            };
            parts.add(new Part(Component.literal(capital(Component.translatableWithFallback("culprit.kind." + Kind.key(k), Kind.english(k)).getString())),
                    Component.translatableWithFallback("culprit.kind.about." + Kind.key(k), Kind.about(k)), d.kindNs[k], group));
        }
        for (int p = 0; p < Math.min(Phase.COUNT, d.phaseNs.length); p++) {
            parts.add(new Part(Component.translatableWithFallback("culprit.phase." + Phase.key(p), capital(Phase.english(p))),
                    Component.translatableWithFallback("culprit.phase.about." + Phase.key(p), Phase.about(p)), d.phaseNs[p],
                    p == Phase.CHUNK_SOURCE ? CHUNKS : SERVER));
        }
        return parts;
    }

    private static List<Part> frameParts(ReportData d) {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part(Component.translatableWithFallback("culprit.frame.entities", "Entities"),
                Component.translatableWithFallback("culprit.frame.about.entities2", "each one drawn, its parts buffered for the GPU"),
                d.kindNs[Kind.ENTITY], F_ENTITIES));
        parts.add(new Part(Component.translatableWithFallback("culprit.frame.block_entities", "Block entities"),
                Component.translatableWithFallback("culprit.frame.about.block_entities2", "chests, signs: each one drawn"),
                d.kindNs[Kind.BLOCK_ENTITY], F_BLOCK_ENTITIES));
        parts.add(new Part(Component.translatableWithFallback("culprit.frame.particle_kinds", "Particles"),
                Component.translatableWithFallback("culprit.frame.about.particle_kinds", "each kind, moved and drawn"),
                d.kindNs[Kind.PARTICLE], F_WORLD));
        for (int p = 0; p < Math.min(FramePhase.COUNT, d.phaseNs.length); p++) {
            if (d.phaseNs[p] == 0) {
                continue;                                       // (no shader pack with shadows, no Distant Horizons, or a part a
            }                                                   // version has no step of its own for: none to show)
            int group = switch (p) {
                case FramePhase.CLIENT_TICK, FramePhase.PACKETS -> F_CLIENT;
                case FramePhase.GUI, FramePhase.MOD_EVENTS -> F_SCREEN;
                case FramePhase.PRESENT, FramePhase.LIMIT -> F_WAITING;
                default -> F_WORLD;
            };
            parts.add(new Part(Component.translatableWithFallback("culprit.frame." + FramePhase.key(p), capital(FramePhase.english(p))),
                    Component.translatableWithFallback("culprit.frame.about." + FramePhase.key(p), FramePhase.about(p)), d.phaseNs[p], group));
        }
        return parts;
    }

    /** Each group's time summed over the scan; with the rest, they make the whole tick or frame. */
    public static long[] groups(ReportData d) {
        long[] g = new long[groupCount(d)];
        for (Part p : parts(d)) {
            g[p.group()] += p.totalNs();
        }
        return g;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
