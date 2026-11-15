package dev.culprit.gametest;

import java.util.Arrays;

import dev.culprit.Culprit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * The idle cost of the tick hooks (DESIGN §5.2): a plot full of hoppers and pigs, the server's tick timed over many
 * ticks with no scan running. Run it twice, with the hooks and without them ({@code -PnoHooks}), and compare: the hooks
 * cost what the two medians differ by. Registered only for {@code -Pbench}; the usual test runs leave it out.
 */
final class BenchTests {
    private static final boolean ENABLED = Boolean.getBoolean("culprit.bench");
    private static final boolean HOOKS = !Boolean.getBoolean("culprit.noHooks");
    private static final int WARMUP = 200, MEASURE = 1200;

    private static long tickStart;
    private static int measured = -1;
    private static final long[] TICKS = new long[MEASURE];

    private BenchTests() {}

    static void register() {
        if (!ENABLED) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent e) -> { if (e.phase != net.minecraftforge.event.TickEvent.Phase.START) return; tickStart = System.nanoTime(); });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent e) -> {if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            if (measured >= 0 && measured < MEASURE) {
                TICKS[measured++] = System.nanoTime() - tickStart;
            }
        });
        CulpritGameTests.testAlone("bench_idle_cost", CulpritGameTests.FLOOR_16, WARMUP + MEASURE + 100, helper -> {
            // 1,792 hoppers (16 x 16 x 7) and 200 pigs: 2,000 hooked calls a tick.
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 1; y <= 7; y++) {
                        helper.setBlock(x, y, z, Blocks.HOPPER);
                    }
                }
            }
            for (int i = 0; i < 200; i++) {
                helper.spawn(EntityType.PIG, 0.5f + (i % 16), 8f, 0.5f + (i / 16));
            }
            helper.runAfterDelay(WARMUP, () -> measured = 0);
            helper.runAfterDelay(WARMUP + MEASURE + 5, () -> {
                long[] sorted = Arrays.copyOf(TICKS, measured);
                Arrays.sort(sorted);
                double mean = Arrays.stream(sorted).average().orElse(0) / 1000.0;
                double median = sorted.length == 0 ? 0 : sorted[sorted.length / 2] / 1000.0;
                double p10 = sorted.length == 0 ? 0 : sorted[sorted.length / 10] / 1000.0;
                double p90 = sorted.length == 0 ? 0 : sorted[sorted.length * 9 / 10] / 1000.0;
                Culprit.LOGGER.info("BENCH hooks={} ticks={} median={} us mean={} us p10={} us p90={} us", HOOKS ? "on" : "off", sorted.length,
                        String.format("%.1f", median), String.format("%.1f", mean), String.format("%.1f", p10), String.format("%.1f", p90));
                helper.succeed();
            });
        });
    }
}
