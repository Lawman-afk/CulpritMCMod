package dev.culprit.gametest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dev.culprit.Culprit;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * How the tests {@link CulpritGameTests} lists reach the game on 26.3: each test's function in the vanilla
 * TEST_FUNCTION registry, and a test instance (function, structure, timeout) on {@link RegisterGameTestsEvent}. (The
 * backports will have their own.)
 * <p>
 * The tests are the developer's checks, not part of the mod: this package and the test structures are left out of the
 * released jar (build.gradle's jar task), and nothing in the jar names them; the class is found by its annotation in
 * the dev runs, and does anything only when the run names the mod in {@code neoforge.enabledGameTestNamespaces}.
 */
@EventBusSubscriber(modid = Culprit.MODID)
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

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        if (!ENABLED) {
            return;
        }
        event.register(Registries.TEST_FUNCTION, helper -> {
            for (CulpritGameTests.TestSpec spec : CulpritGameTests.specs()) {
                Consumer<GameTestHelper> body = spec.body();
                helper.register(Culprit.id(spec.name()), body);
            }
        });
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        if (!ENABLED) {
            return;
        }
        Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
        // -PonlyTests=<name>,<name>: only those tests are set up (a quicker run).
        String only = System.getProperty("culprit.onlyTests", "");
        List<String> chosen = only.isBlank() ? List.of() : Arrays.asList(only.split(","));
        for (CulpritGameTests.TestSpec spec : CulpritGameTests.specs()) {
            if (!chosen.isEmpty() && !chosen.contains(spec.name())) {
                continue;
            }
            // One environment per batch: the runner runs each environment's tests as a batch, one batch after another.
            Holder<TestEnvironmentDefinition<?>> environment = environments.computeIfAbsent(spec.batch(), b -> event.registerEnvironment(Culprit.id(b)));
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, spec.structure(), spec.maxTicks(), 0,
                    true, Rotation.NONE, false, 1, 1, spec.sky(), 0);
            ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, Culprit.id(spec.name()));
            event.registerTest(Culprit.id(spec.name()), new FunctionGameTestInstance(function, data));
        }
    }
}
