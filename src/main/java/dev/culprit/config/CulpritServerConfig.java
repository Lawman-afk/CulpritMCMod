package dev.culprit.config;

import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionSet;
import net.neoforged.neoforge.common.ModConfigSpec;

/** The server's settings for Culprit (DESIGN §5.5): who may scan, and how long a scan may last. */
public final class CulpritServerConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.comment("Who may use Culprit's tick scans on this server.").push("permissions");
    }

    public static final ModConfigSpec.IntValue SCAN_LEVEL = B
            .comment("Permission level needed to start and stop tick scans and to see their results: 0 everyone, 1 moderators,",
                    "2 operators (the default), 3 admins, 4 the owner. Teleporting to a result uses the game's own /tp permission.")
            .defineInRange("scanLevel", 2, 0, 4);

    static {
        B.pop().comment("Tick scans.").push("scans");
    }

    public static final ModConfigSpec.IntValue MAX_SECONDS = B
            .comment("The longest tick scan anyone may start, in seconds.")
            .defineInRange("maxSeconds", 120, 5, 600);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private CulpritServerConfig() {}

    /** The check a command source or player must pass to scan. */
    public static PermissionCheck scanCheck() {
        int level = SPEC.isLoaded() ? SCAN_LEVEL.get() : 2;
        return switch (level) {
            case 0 -> Commands.LEVEL_ALL;
            case 1 -> Commands.LEVEL_MODERATORS;
            case 3 -> Commands.LEVEL_ADMINS;
            case 4 -> Commands.LEVEL_OWNERS;
            default -> Commands.LEVEL_GAMEMASTERS;
        };
    }

    public static boolean mayScan(PermissionSet permissions) {
        return scanCheck().check(permissions);
    }

    public static int maxSeconds() {
        return SPEC.isLoaded() ? MAX_SECONDS.get() : 120;
    }
}
