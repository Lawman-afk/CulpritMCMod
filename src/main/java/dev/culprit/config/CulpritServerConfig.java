package dev.culprit.config;

import net.minecraft.commands.Commands;


import net.minecraftforge.common.ForgeConfigSpec;

/** The server's settings for Culprit (DESIGN §5.5): who may scan, and how long a scan may last. */
public final class CulpritServerConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    static {
        B.comment("Who may use Culprit's tick scans on this server.").push("permissions");
    }

    public static final ForgeConfigSpec.IntValue SCAN_LEVEL = B
            .comment("Permission level needed to start and stop tick scans and to see their results: 0 everyone, 1 moderators,",
                    "2 operators (the default), 3 admins, 4 the owner. Teleporting to a result uses the game's own /tp permission.")
            .defineInRange("scanLevel", 2, 0, 4);

    static {
        B.pop().comment("Tick scans.").push("scans");
    }

    public static final ForgeConfigSpec.IntValue MAX_SECONDS = B
            .comment("The longest tick scan anyone may start, in seconds.")
            .defineInRange("maxSeconds", 120, 5, 600);

    static {
        B.pop();
    }

    public static final ForgeConfigSpec SPEC = B.build();

    private CulpritServerConfig() {}

    public static int scanLevel() {
        return SPEC.isLoaded() ? SCAN_LEVEL.get() : 2;
    }

    /** Whether a command's source may scan: 1.21.1's permission levels (main checks 26.x's permission sets). */
    public static boolean mayScan(net.minecraft.commands.CommandSourceStack source) {
        return source.hasPermission(scanLevel());
    }

    public static boolean mayScan(net.minecraft.world.entity.player.Player player) {
        return player.hasPermissions(scanLevel());
    }

    public static int maxSeconds() {
        return SPEC.isLoaded() ? MAX_SECONDS.get() : 120;
    }
}
