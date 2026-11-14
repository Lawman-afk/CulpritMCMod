package dev.culprit.gametest;

import java.util.List;

import dev.culprit.scan.Kind;
import dev.culprit.scan.Probe;
import dev.culprit.scan.ScanReport;
import dev.culprit.scan.TickScan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

/** The tick scan finds what it should, where it is (DESIGN §7). */
final class ScanTests {
    private static final int SCAN_TICKS = 40;

    private ScanTests() {}

    static void register() {
        // Lag sources of each kind at known places: a row of hoppers (block entities), pigs (entities), an observer
        // clock (scheduled block ticks) driving a piston (block events); the plot's chunk (chunk ticks). One scan
        // must find each of them at its place; then a long scan stopped early must report the ticks it had.
        CulpritGameTests.test("scan_finds_lag_sources", CulpritGameTests.FLOOR_16, 400, helper -> {
            for (int x = 0; x < 16; x++) {
                helper.setBlock(x, 1, 2, Blocks.HOPPER);
            }
            helper.setBlock(new BlockPos(2, 1, 8), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST));
            helper.setBlock(new BlockPos(3, 1, 8), Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.EAST));
            helper.setBlock(new BlockPos(4, 1, 8), Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.WEST));
            for (int i = 0; i < 12; i++) {
                helper.spawn(EntityTypes.PIG, 2 + (i % 6) * 2, 1, 11 + (i / 6) * 2);
            }
            helper.runAfterDelay(10, () -> waitThenScan(helper));
        });
    }

    /** Another test's scan may be running: wait for it, then scan. */
    private static void waitThenScan(GameTestHelper helper) {
        if (TickScan.ticksLeft() >= 0) {
            helper.runAfterDelay(5, () -> waitThenScan(helper));
            return;
        }
        // The report arrives at the end of a server tick; the checks run in the test's own time, where a failed
        // assertion fails the test.
        helper.assertTrue(TickScan.start(SCAN_TICKS, report -> helper.runAfterDelay(1, () -> check(helper, report))), "the scan starts");
    }

    private static void check(GameTestHelper helper, ScanReport r) {
        helper.assertTrue(r.ticks == SCAN_TICKS, "the scan lasted " + SCAN_TICKS + " ticks, not " + r.ticks);
        helper.assertFalse(Probe.active, "the hooks are idle once the scan ends");
        BlockPos a = helper.absolutePos(BlockPos.ZERO);
        BlockPos b = helper.absolutePos(new BlockPos(15, 15, 15));
        BlockPos min = BlockPos.min(a, b);
        BlockPos max = BlockPos.max(a, b);
        var level = helper.getLevel().dimension();

        List<ScanReport.Thing> hoppers = r.thingsWithin(level, Kind.BLOCK_ENTITY, min, max).stream()
                .filter(t -> t.type().equals("minecraft:hopper")).toList();
        helper.assertTrue(hoppers.size() == 16, "16 hoppers measured, not " + hoppers.size());
        for (int x = 0; x < 16; x++) {
            BlockPos at = helper.absolutePos(new BlockPos(x, 1, 2));
            helper.assertTrue(hoppers.stream().anyMatch(t -> t.blockPos().equals(at)), "the hopper at " + at + " is in the report");
        }
        helper.assertTrue(hoppers.stream().allMatch(t -> t.calls() >= SCAN_TICKS), "each hopper ticked every tick of the scan");
        helper.assertTrue(hoppers.stream().mapToLong(ScanReport.Thing::totalNs).sum() > 0, "the hoppers took time");

        long pigs = r.thingsWithin(level, Kind.ENTITY, min.offset(-8, -4, -8), max.offset(8, 4, 8)).stream()
                .filter(t -> t.type().equals("minecraft:pig")).count();
        helper.assertTrue(pigs >= 12, "12 pigs measured, not " + pigs);

        BlockPos observerA = helper.absolutePos(new BlockPos(3, 1, 8));
        BlockPos observerB = helper.absolutePos(new BlockPos(4, 1, 8));
        List<ScanReport.Thing> ticks = r.thingsWithin(level, Kind.BLOCK_TICK, min, max);
        helper.assertTrue(ticks.stream().anyMatch(t -> t.type().equals("minecraft:observer") && (t.blockPos().equals(observerA) || t.blockPos().equals(observerB))),
                "the observer clock's scheduled ticks are found at the observers");
        BlockPos piston = helper.absolutePos(new BlockPos(2, 1, 8));
        helper.assertTrue(r.thingsWithin(level, Kind.BLOCK_EVENT, min, max).stream()
                        .anyMatch(t -> t.type().equals("minecraft:piston") && t.blockPos().equals(piston)),
                "the piston's block events are found at the piston");

        ChunkPos chunk = ChunkPos.containing(observerA);
        helper.assertTrue(r.things.stream().anyMatch(t -> t.kind() == Kind.CHUNK && t.level().equals(level)
                        && (t.blockPos().getX() >> 4) == chunk.x() && (t.blockPos().getZ() >> 4) == chunk.z()),
                "the plot's chunk has its own tick measured");
        helper.assertTrue(r.chunks.stream().anyMatch(c -> c.level().equals(level) && c.x() == chunk.x() && c.z() == chunk.z() && c.totalNs() > 0),
                "the plot's chunk is in the chunk list");
        helper.assertTrue(r.types.stream().anyMatch(g -> g.name().equals("minecraft:hopper") && g.kind() == Kind.BLOCK_ENTITY && g.things() >= 16),
                "the hoppers are folded into one type");
        helper.assertTrue(r.mods.stream().anyMatch(g -> g.name().equals("minecraft")), "the vanilla things are folded into minecraft");

        // A long scan stopped early reports the ticks it had and leaves the hooks idle.
        helper.assertTrue(TickScan.start(10_000, report -> helper.runAfterDelay(1, () -> {
            helper.assertTrue(report.ticks > 0 && report.ticks < 10_000, "the stopped scan reports its " + report.ticks + " ticks");
            helper.assertFalse(Probe.active, "the hooks are idle after a stop");
            helper.succeed();
        })), "the second scan starts");
        helper.runAfterDelay(12, () -> helper.assertTrue(TickScan.stopNow(true), "the long scan stops"));
    }
}
