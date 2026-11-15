package dev.culprit.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import dev.culprit.Culprit;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/**
 * GameTest harness: every test, a function run over a structure within a timeout, listed here; how they reach the game
 * is {@link GameTestRegistration}'s (each version its own). The developer's checks, left out of the released jar. Run them all with {@code gradlew runGameTestServer}, or in a
 * dev world with {@code /test runall}. Test structures are NBT templates under {@code data/culprit/structure/gametest/}.
 */
public final class CulpritGameTests {
    /** Structure with a 16x16 smooth-stone floor at y=0 and 15 blocks of air above. */
    public static final ResourceLocation FLOOR_16 = Culprit.id("gametest/floor_16");

    private static final List<TestSpec> SPECS = new ArrayList<>();

    /**
     * A test: its name, the structure it runs in, its timeout, whether its plot has open sky, its batch (tests in one
     * batch run side by side; batches run one after another), and what it does.
     */
    public record TestSpec(String name, ResourceLocation structure, int maxTicks, boolean sky, String batch, Consumer<GameTestHelper> body) {}

    static {
        test("harness_floor", FLOOR_16, 20, helper -> {
            helper.assertBlockPresent(Blocks.SMOOTH_STONE, 0, 0, 0);
            helper.assertBlockPresent(Blocks.SMOOTH_STONE, 15, 0, 15);
            helper.assertBlockPresent(Blocks.AIR, 7, 1, 7);
            helper.succeed();
        });

        ScanTests.register();
        BenchTests.register();
    }

    private CulpritGameTests() {}

    static void test(String name, ResourceLocation structure, int maxTicks, Consumer<GameTestHelper> body) {
        SPECS.add(new TestSpec(name, structure, maxTicks, false, "default", body));
    }

    /** A test in a batch of its own: run alone, as a test that scans must be (one scan at a time). */
    static void testAlone(String name, ResourceLocation structure, int maxTicks, Consumer<GameTestHelper> body) {
        SPECS.add(new TestSpec(name, structure, maxTicks, false, name, body));
    }

    /** A test with open sky over its plot (no barrier lid), for things that reach above it. */
    static void testOpenSky(String name, ResourceLocation structure, int maxTicks, Consumer<GameTestHelper> body) {
        SPECS.add(new TestSpec(name, structure, maxTicks, true, "default", body));
    }

    /** Every test, in the order listed. */
    public static List<TestSpec> specs() {
        return Collections.unmodifiableList(SPECS);
    }
}
