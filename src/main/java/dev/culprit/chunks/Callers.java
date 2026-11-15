package dev.culprit.chunks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import dev.culprit.Culprit;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.jspecify.annotations.Nullable;

/**
 * Whose code asked, from the stack (DESIGN §4.3): the first class that is no part of the game, NeoForge or a library
 * is a mod's, named by the mod file holding it ("mod:<id>"); with none, the first of the game's own classes that is not
 * the way through ("game:<class>"); else nothing. Walked only when a chunk is forced or loaded on request, which is
 * rare beside the work it causes.
 */
final class Callers {
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final Map<String, String> MOD_OF_CLASS = new ConcurrentHashMap<>();
    private static final String[] LIBRARIES = {"java.", "javax.", "jdk.", "sun.", "kotlin.", "scala.", "com.google.", "it.unimi.",
            "org.apache.", "io.netty.", "org.spongepowered.", "com.llamalad7.", "cpw.mods.", "org.slf4j."};

    static final String MOD = "mod:", GAME = "game:";

    private Callers() {}

    /** Who asked, past the classes {@code wayThrough} names (the call being recorded and what any code calls it by). */
    static @Nullable String of(Predicate<String> wayThrough) {
        List<String> classes = WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getClassName).limit(128).toList());
        String game = null;
        for (String name : classes) {
            if (name.startsWith("dev.culprit.chunks.") || wayThrough.test(name) || isLibrary(name)) {
                continue;
            }
            if (name.startsWith("net.minecraft.") || name.startsWith("net.minecraftforge.") || name.startsWith("cpw.mods.") || name.startsWith("com.mojang.")) {
                if (game == null) {
                    game = name.substring(name.lastIndexOf('.') + 1);
                }
                continue;                                       // (a mod may have called the game, which asked)
            }
            return MOD + modOf(name);
        }
        return game == null ? null : GAME + game;
    }

    /** "Mekanism", "a mod (com.example)", "the game (Raid)": who a record names. */
    static String describe(String by) {
        if (by.startsWith(MOD)) {
            String mod = by.substring(MOD.length());
            return mod.startsWith("?") ? "a mod (" + mod.substring(1) + ")" : OwnerNames.mod(mod);
        }
        return by.startsWith(GAME) ? "the game (" + by.substring(GAME.length()) + ")" : by;
    }

    private static boolean isLibrary(String name) {
        for (String prefix : LIBRARIES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** The mod whose file holds a class; "?package" when none does. */
    private static String modOf(String className) {
        return MOD_OF_CLASS.computeIfAbsent(className, name -> {
            String path = name.replace('.', '/') + ".class";
            try {
                for (IModFileInfo file : ModList.get().getModFiles()) {
                    if (!file.getMods().isEmpty() && java.nio.file.Files.exists(file.getFile().findResource(path))) {
                        return dev.culprit.legacy.J21.first(file.getMods()).getModId();
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                Culprit.LOGGER.debug("Culprit: could not find the mod of {}", name, e);
            }
            int dot = name.lastIndexOf('.');
            return "?" + (dot < 0 ? name : name.substring(0, dot));
        });
    }
}
