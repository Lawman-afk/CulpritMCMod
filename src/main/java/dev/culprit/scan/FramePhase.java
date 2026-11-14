package dev.culprit.scan;

/**
 * The parts of a frame a frame scan times beside the things it draws (DESIGN §4.2, §5.3): each part's own time, what
 * is measured inside it (an entity's drawing inside the world's) taken out, so the parts and the things add up to the
 * frame.
 */
public final class FramePhase {
    public static final int CLIENT_TICK = 0, PACKETS = 1, EXTRACT = 2, PARTICLES = 3, SUBMIT = 4, BATCHING = 5, DRAWING = 6, CHUNKS = 7,
            GUI = 8, MOD_EVENTS = 9, PRESENT = 10, LIMIT = 11, FEATURES = 12;
    public static final int COUNT = 13;

    private static final String[] KEYS = {"client_tick", "packets", "extract", "particles", "submit", "batching", "drawing", "chunks", "gui",
            "mod_events", "present", "limit", "features"};
    private static final String[] ENGLISH = {"client ticks", "packets and tasks", "finding what is in view", "particles, handed in",
            "gathering what to draw", "sorting what to draw", "drawing the terrain", "building chunk meshes", "HUD and screens",
            "mods' frame events", "showing the frame", "frame-rate limit", "drawing the handed-in parts"};
    private static final String[] ABOUT = {"mobs and sounds moving between frames", "the server's packets and queued tasks",
            "the world's blocks, sky and weather in view", "all of them handed in, and the rarer kinds made ready",
            "the world's other parts, handed in", "the handed-in parts put in order (the things' shares are with them)",
            "terrain, sky and clouds sent to the GPU", "changed chunks turned into meshes", "the HUD, chat and screens",
            "other mods' code run every frame", "waiting for the GPU or the screen, and the frame's upkeep", "waiting, as the frame-rate limit asks",
            "the sorted parts sent to the GPU (the things' shares are with them)"};

    private FramePhase() {}

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
