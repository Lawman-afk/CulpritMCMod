package dev.culprit.scan;

/**
 * The parts of the server's tick that are not one thing at one place (DESIGN §4.1): timed whole, each charged only
 * the time its measured things inside it did not take, so the things and the phases together cover the tick.
 */
public final class Phase {
    /** Loading, saving and sending chunks; thunder. */
    public static final int CHUNK_SOURCE = 0;
    /** Each level's own upkeep: weather, time, raids, the lists of what ticks. */
    public static final int LEVEL = 1;
    /** Other mods' code run on every level tick (NeoForge's level tick events). */
    public static final int LEVEL_EVENTS = 2;
    /** Players' connections: their packets, their moves. */
    public static final int CONNECTIONS = 3;
    /** The server's own work around the levels: functions, clocks, the player list. */
    public static final int SERVER = 4;
    /** Saving the world every few minutes. */
    public static final int AUTOSAVE = 5;
    public static final int COUNT = 6;

    private static final String[] KEYS = {"chunk_source", "level", "level_events", "connections", "server", "autosave"};
    private static final String[] ENGLISH = {"chunk loading", "levels' upkeep", "mods' tick events", "players' connections",
            "the server's own work", "autosave"};
    private static final String[] ABOUT = {"loading, saving, sending chunks; thunder", "weather, time, raids, the tick lists",
            "other mods' code run every tick", "players' packets and moves", "functions, clocks, the player list",
            "saving the world every few minutes"};

    private Phase() {}

    public static String key(int phase) {
        return KEYS[phase];
    }

    public static String english(int phase) {
        return ENGLISH[phase];
    }

    public static String about(int phase) {
        return ABOUT[phase];
    }
}
