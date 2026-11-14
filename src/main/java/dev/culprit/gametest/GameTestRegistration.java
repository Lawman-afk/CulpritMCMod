package dev.culprit.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.culprit.Culprit;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * How the tests {@link CulpritGameTests} lists reach the game on 26.3: each test's function in the vanilla TEST_FUNCTION
 * registry, and a test instance (function, structure, timeout) on {@link RegisterGameTestsEvent}. (The backports will
 * have their own.)
 */
final class GameTestRegistration {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Culprit.MODID);

    private record Registered(CulpritGameTests.TestSpec spec, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function) {}

    private static final List<Registered> REGISTERED = new ArrayList<>();

    private GameTestRegistration() {}

    static void register(IEventBus modBus, List<CulpritGameTests.TestSpec> specs) {
        for (CulpritGameTests.TestSpec spec : specs) {
            REGISTERED.add(new Registered(spec, FUNCTIONS.register(spec.name(), spec::body)));
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(GameTestRegistration::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Culprit.id("default"));
        // -PonlyTests=<name>,<name>: only those tests are set up (a quicker run).
        String only = System.getProperty("culprit.onlyTests", "");
        List<String> chosen = only.isBlank() ? List.of() : java.util.Arrays.asList(only.split(","));
        for (Registered r : REGISTERED) {
            CulpritGameTests.TestSpec spec = r.spec();
            if (!chosen.isEmpty() && !chosen.contains(spec.name())) {
                continue;
            }
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, net.minecraft.world.level.Level.OVERWORLD,
                    spec.structure(), spec.maxTicks(), 0, true, net.minecraft.world.level.block.Rotation.NONE, false, 1, 1, spec.sky(), 0);
            event.registerTest(r.function().getId(), new FunctionGameTestInstance(r.function().key(), data));
        }
    }
}
