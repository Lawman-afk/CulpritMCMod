package dev.culprit.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.culprit.Culprit;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import dev.culprit.legacy.RegisterTicketControllersEvent;
import dev.culprit.legacy.TicketController;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Main's GameTests (its {@link CulpritGameTests} list) as 1.21.1 runs them (this branch's own, manual.txt): a generator
 * makes a test function of each, the tests of one batch run side by side and the batches one after another, as main's
 * environments do. Dev runs only (build.gradle names the mod; the jar leaves this package out).
 */
@GameTestHolder(Culprit.MODID)
@EventBusSubscriber(modid = Culprit.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class GameTestRegistration {
    /** The dev runs (build.gradle) name the mod here; a player's game never does. */
    private static final boolean ENABLED = Arrays.asList(System.getProperty("neoforge.enabledGameTestNamespaces", "").split(","))
            .contains(Culprit.MODID);

    /** A chunk loader for the tests (a mod's, as the chunk inspector sees it): "culprit:test". */
    public static final TicketController CONTROLLER = new TicketController(Culprit.id("test"));

    private GameTestRegistration() {}

    @SubscribeEvent
    public static void registerControllers(RegisterTicketControllersEvent event) {
        if (ENABLED) {
            event.register(CONTROLLER);
        }
    }

    @GameTestGenerator
    public static List<TestFunction> tests() {
        // -PonlyTests=<name>,<name>: only those tests (a quicker run).
        String only = System.getProperty("culprit.onlyTests", "");
        List<String> chosen = only.isBlank() ? List.of() : Arrays.asList(only.split(","));
        List<TestFunction> out = new ArrayList<>();
        for (CulpritGameTests.TestSpec spec : CulpritGameTests.specs()) {
            if (!chosen.isEmpty() && !chosen.contains(spec.name())) {
                continue;
            }
            out.add(new TestFunction(spec.batch(), spec.name(), spec.structure().toString(), Rotation.NONE, spec.maxTicks(), 0L, true,
                    1, 1, spec.body()));
        }
        return out;
    }
}
