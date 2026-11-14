package dev.culprit.scan;

/** What a measured thing is: the kinds of server work a tick scan times (DESIGN §4.1, §5.1). */
public final class Kind {
    public static final byte BLOCK_ENTITY = 0;
    public static final byte ENTITY = 1;
    /** A position's scheduled block ticks (repeaters, observers, crops growing on schedule...). */
    public static final byte BLOCK_TICK = 2;
    /** A position's scheduled fluid ticks (water and lava spreading). */
    public static final byte FLUID_TICK = 3;
    /** A position's block events (pistons moving, note blocks, chests and bells). */
    public static final byte BLOCK_EVENT = 4;
    /** A chunk's own tick: random ticks, weather (snow, ice, lightning). */
    public static final byte CHUNK = 5;
    public static final int COUNT = 6;

    private static final String[] KEYS = {"block_entity", "entity", "block_tick", "fluid_tick", "block_event", "chunk"};
    private static final String[] ENGLISH = {"block entities", "entities", "block ticks", "fluid ticks", "block events", "chunk ticks"};
    private static final String[] ENGLISH_ONE = {"block entity", "entity", "block ticks", "fluid ticks", "block events", "chunk ticks"};

    private Kind() {}

    /** Its key in lang files and reports, e.g. {@code block_entity}. */
    public static String key(int kind) {
        return KEYS[kind];
    }

    /** Its English name for many, the fallback for players without the mod (vanilla clients). */
    public static String english(int kind) {
        return ENGLISH[kind];
    }

    /** Its English name for one thing of the kind. */
    public static String englishOne(int kind) {
        return ENGLISH_ONE[kind];
    }
}
