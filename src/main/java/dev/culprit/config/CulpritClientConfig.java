package dev.culprit.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * A player's own settings for Culprit (DESIGN §5.5): how the results show on the world, and the HUD line. Changed in
 * the game's mod list (Config) or in config/culprit-client.toml.
 */
public final class CulpritClientConfig {
    /** Where the HUD line sits. */
    public enum Corner { TOP_LEFT, TOP_RIGHT }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.comment("The results on the world (shown from the results screen).").translation("culprit.configuration.world").push("world");
    }

    public static final ModConfigSpec.IntValue RANGE = B
            .comment("How far from you things are boxed, in blocks.")
            .translation("culprit.configuration.range")
            .defineInRange("range", 64, 16, 128);

    public static final ModConfigSpec.IntValue LABELS = B
            .comment("How many of the costliest things near you are labelled with their cost (0: none; the one you look at is",
                    "always read out under the crosshair).")
            .translation("culprit.configuration.labels")
            .defineInRange("labels", 12, 0, 30);

    public static final ModConfigSpec.IntValue HIDE_AFTER = B
            .comment("Minutes after which the results hide from the world by themselves (0: they stay until you hide them).")
            .translation("culprit.configuration.hideAfterMinutes")
            .defineInRange("hideAfterMinutes", 0, 0, 120);

    static {
        B.pop().comment("The HUD line: the server's ticks and your frames, always on screen.").translation("culprit.configuration.hud").push("hud");
    }

    public static final ModConfigSpec.BooleanValue HUD_LINE = B
            .comment("Show the server's ticks a second and time a tick (on a server, when you may scan there) and your frames a",
                    "second and time a frame in a line on screen. A key in Controls turns it on and off (none set at first).")
            .translation("culprit.configuration.hudLine")
            .define("line", false);

    public static final ModConfigSpec.EnumValue<Corner> HUD_CORNER = B
            .comment("Which corner of the screen the HUD line sits in.")
            .translation("culprit.configuration.hudCorner")
            .defineEnum("corner", Corner.TOP_LEFT);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private CulpritClientConfig() {}

    public static int range() {
        return SPEC.isLoaded() ? RANGE.get() : 64;
    }

    public static int labels() {
        return SPEC.isLoaded() ? LABELS.get() : 12;
    }

    public static int hideAfterMinutes() {
        return SPEC.isLoaded() ? HIDE_AFTER.get() : 0;
    }

    public static boolean hudLine() {
        return SPEC.isLoaded() && HUD_LINE.get();
    }

    public static Corner hudCorner() {
        return SPEC.isLoaded() ? HUD_CORNER.get() : Corner.TOP_LEFT;
    }

    public static void setHudLine(boolean on) {
        if (SPEC.isLoaded()) {
            HUD_LINE.set(on);
            SPEC.save();
        }
    }
}
