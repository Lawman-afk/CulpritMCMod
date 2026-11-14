package dev.culprit.scan;

/** What a measured thing is: the kinds of server work a tick scan times, each at a place (DESIGN §4.1, §5.1). */
public final class Kind {
    public static final byte BLOCK_ENTITY = 0;
    public static final byte ENTITY = 1;
    /** A position's scheduled block ticks (repeaters, observers, crops growing on schedule...). */
    public static final byte BLOCK_TICK = 2;
    /** A position's scheduled fluid ticks (water and lava spreading). */
    public static final byte FLUID_TICK = 3;
    /** A position's block events (pistons moving, note blocks, chests and bells). */
    public static final byte BLOCK_EVENT = 4;
    /** A chunk's own upkeep: picking the blocks to tick at random, snow and ice. */
    public static final byte CHUNK = 5;
    /** A chunk's random ticks of one kind of block (crops growing, grass spreading, leaves decaying). */
    public static final byte RANDOM_TICK = 6;
    /** A chunk's mob spawning. */
    public static final byte SPAWNING = 7;
    public static final int COUNT = 8;

    private static final String[] KEYS = {"block_entity", "entity", "block_tick", "fluid_tick", "block_event", "chunk", "random_tick", "spawning"};
    private static final String[] ENGLISH = {"block entities", "entities", "block ticks", "fluid ticks", "block events", "chunk upkeep",
            "random ticks", "mob spawning"};
    private static final String[] ENGLISH_ONE = {"block entity", "entity", "block ticks", "fluid ticks", "block events", "chunk upkeep",
            "random ticks", "mob spawning"};
    /** What each kind is, in a few words. */
    private static final String[] ABOUT = {"hoppers, furnaces, machines", "mobs, items, players, vehicles",
            "repeaters, observers, crops on a timer", "water and lava spreading", "pistons, note blocks, chests opening",
            "picking blocks to tick, snow and ice", "crops, grass, leaves, by chance", "new mobs appearing"};

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

    /** What it is, in English. */
    public static String about(int kind) {
        return ABOUT[kind];
    }

    /** Whether things of this kind are a whole chunk's (charged to the chunk, with no one block to point at). */
    public static boolean isChunkWide(int kind) {
        return kind == CHUNK || kind == RANDOM_TICK || kind == SPAWNING;
    }

    /** Whether things of this kind belong to the game rather than to a block or entity's mod. */
    public static boolean isGames(int kind) {
        return kind == CHUNK || kind == SPAWNING;
    }
}
