package dev.culprit.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.culprit.scan.Kind;
import dev.culprit.scan.Probe;
import dev.culprit.scan.ScanReport;
import dev.culprit.scan.TickScan;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

/**
 * The tick scan's tests (DESIGN §7). Each plants lag sources whose places and kinds are known, scans, and checks the
 * report: that every source is found where it is, charged as the kind it is, its time its own, and the report's
 * figures adding up. Only one scan runs at a time, so a test that scans waits until none runs.
 */
final class ScanTests {
    private static final int SCAN_TICKS = 40;

    private ScanTests() {}

    static void register() {
        // One lag source of each kind: a row of 16 hoppers (block entities), 12 pigs (entities), an observer clock
        // (scheduled block ticks) driving a piston (block events), and the plot's own chunk (chunk ticks). Then a long
        // scan stopped early.
        CulpritGameTests.testAlone("scan_finds_lag_sources", CulpritGameTests.FLOOR_16, 400, helper -> {
            for (int x = 0; x < 16; x++) {
                helper.setBlock(x, 1, 2, Blocks.HOPPER);
            }
            helper.setBlock(new BlockPos(2, 1, 8), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST));
            helper.setBlock(new BlockPos(3, 1, 8), Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.EAST));
            helper.setBlock(new BlockPos(4, 1, 8), Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.WEST));
            for (int i = 0; i < 12; i++) {
                helper.spawn(EntityTypes.PIG, 2 + (i % 6) * 2, 1, 11 + (i / 6) * 2);
            }
            // The report arrives at the end of a server tick; the checks run in the test's own time, where a failed
            // check fails the test.
            helper.runAfterDelay(10, () -> whenNoScan(helper, () -> helper.assertTrue(
                    TickScan.start(SCAN_TICKS, report -> helper.runAfterDelay(1, () -> checkSources(helper, report))), "the scan starts")));
        });

        // The arithmetic of nested calls: a measured call inside another is charged its own time, and the outer one
        // only the rest (a vehicle and its passengers, a block entity setting off block events).
        CulpritGameTests.testAlone("probe_charges_own_time", CulpritGameTests.FLOOR_16, 200, helper -> whenNoScan(helper, () -> {
            long outerStart = Probe.begin();
            spin(1_000_000);                       // 1 ms of the outer call's own work
            long innerStart = Probe.begin();
            spin(2_000_000);                       // 2 ms in a nested measured call
            long inner = Probe.end(innerStart);
            spin(1_000_000);                       // 1 ms more of the outer call's own
            long outer = Probe.end(outerStart);
            helper.assertTrue(inner >= 1_900_000 && inner < 6_000_000, "the nested call is charged its own 2 ms, not " + inner + " ns");
            helper.assertTrue(outer >= 1_900_000 && outer < 3_500_000,
                    "the outer call is charged its own 2 ms without the nested call's 2 ms, not " + outer + " ns");
            helper.succeed();
        }));

        // A pig riding a minecart: the minecart is ticked from the level's loop, the pig inside the minecart's tick;
        // each must be in the report as its own thing, every tick.
        CulpritGameTests.testAlone("scan_charges_passengers_apart", CulpritGameTests.FLOOR_16, 300, helper -> {
            Minecart cart = helper.spawn(EntityTypes.MINECART, 7, 1, 7);
            Pig pig = helper.spawn(EntityTypes.PIG, 7, 1, 7);
            helper.assertTrue(pig.startRiding(cart, true, false), "the pig gets into the minecart");
            helper.runAfterDelay(5, () -> whenNoScan(helper, () -> helper.assertTrue(TickScan.start(20, report -> helper.runAfterDelay(1, () -> {
                helper.assertTrue(pig.getVehicle() == cart, "the pig is still riding");
                var cartThing = report.things.stream().filter(t -> t.kind() == Kind.ENTITY && t.type().equals("minecraft:minecart")
                        && t.blockPos().closerThan(cart.blockPosition(), 2.5)).findFirst();
                var pigThing = report.things.stream().filter(t -> t.kind() == Kind.ENTITY && t.type().equals("minecraft:pig")
                        && t.blockPos().closerThan(cart.blockPosition(), 2.5)).findFirst();
                helper.assertTrue(cartThing.isPresent(), "the minecart is in the report");
                helper.assertTrue(pigThing.isPresent(), "the pig riding it is in the report as its own thing");
                helper.assertTrue(cartThing.get().calls() >= 20 && pigThing.get().calls() >= 20,
                        "both were measured every tick (" + cartThing.get().calls() + " and " + pigThing.get().calls() + " calls)");
                helper.succeed();
            })), "the scan starts")));
        });

        // The commands, as an operator would type them: scan starts a scan, stop ends it with a summary, top prints
        // its header and lines.
        CulpritGameTests.testAlone("commands_scan_stop_top", CulpritGameTests.FLOOR_16, 300, helper -> {
            helper.setBlock(5, 1, 5, Blocks.HOPPER);
            whenNoScan(helper, () -> {
                List<String> said = new ArrayList<>();
                MinecraftServer server = helper.getLevel().getServer();
                CommandSourceStack source = server.createCommandSourceStack().withSource(new CommandSource() {
                    @Override
                    public void sendSystemMessage(Component message) {
                        said.add(message.getString());
                    }

                    @Override
                    public boolean acceptsSuccess() {
                        return true;
                    }

                    @Override
                    public boolean acceptsFailure() {
                        return true;
                    }

                    @Override
                    public boolean shouldInformAdmins() {
                        return false;
                    }
                });
                server.getCommands().performPrefixedCommand(source, "culprit scan 5");
                helper.assertTrue(TickScan.ticksLeft() >= 0, "/culprit scan starts a scan: " + said);
                helper.runAfterDelay(10, () -> {
                    server.getCommands().performPrefixedCommand(source, "culprit stop");
                    helper.runAfterDelay(2, () -> {
                        helper.assertTrue(TickScan.ticksLeft() < 0, "/culprit stop ends the scan");
                        helper.assertTrue(said.stream().anyMatch(s -> s.contains("Culprit scanned")), "the stopped scan sends its summary: " + said);
                        said.clear();
                        server.getCommands().performPrefixedCommand(source, "culprit top types 3");
                        helper.assertTrue(said.size() >= 2 && said.getFirst().contains("costliest types"), "/culprit top types prints a header and lines: " + said);
                        helper.succeed();
                    });
                });
            });
        });
    }

    private static void checkSources(GameTestHelper helper, ScanReport r) {
        helper.assertTrue(r.ticks == SCAN_TICKS, "the scan lasted " + SCAN_TICKS + " ticks, not " + r.ticks);
        helper.assertFalse(Probe.active, "the hooks are idle once the scan ends");
        BlockPos a = helper.absolutePos(BlockPos.ZERO);
        BlockPos b = helper.absolutePos(new BlockPos(15, 15, 15));
        BlockPos min = BlockPos.min(a, b);
        BlockPos max = BlockPos.max(a, b);
        var level = helper.getLevel().dimension();

        // Block entities: all 16 hoppers, each where it stands, each every tick.
        List<ScanReport.Thing> hoppers = r.thingsWithin(level, Kind.BLOCK_ENTITY, min, max).stream()
                .filter(t -> t.type().equals("minecraft:hopper")).toList();
        helper.assertTrue(hoppers.size() == 16, "16 hoppers measured, not " + hoppers.size());
        for (int x = 0; x < 16; x++) {
            BlockPos at = helper.absolutePos(new BlockPos(x, 1, 2));
            helper.assertTrue(hoppers.stream().anyMatch(t -> t.blockPos().equals(at)), "the hopper at " + at + " is in the report");
        }
        helper.assertTrue(hoppers.stream().allMatch(t -> t.calls() >= SCAN_TICKS), "each hopper ticked every tick of the scan");
        helper.assertTrue(hoppers.stream().allMatch(t -> t.totalNs() > 0), "each hopper took time");
        helper.assertTrue(hoppers.stream().allMatch(t -> t.maxNs() >= r.perTick(t.totalNs())), "each hopper's worst tick is at least its mean");

        // Entities: the 12 pigs, near where they were let out.
        long pigs = r.thingsWithin(level, Kind.ENTITY, min.offset(-8, -4, -8), max.offset(8, 4, 8)).stream()
                .filter(t -> t.type().equals("minecraft:pig")).count();
        helper.assertTrue(pigs >= 12, "12 pigs measured, not " + pigs);

        // Scheduled block ticks at the observers; block events at the piston they drive.
        BlockPos observerA = helper.absolutePos(new BlockPos(3, 1, 8));
        BlockPos observerB = helper.absolutePos(new BlockPos(4, 1, 8));
        helper.assertTrue(r.thingsWithin(level, Kind.BLOCK_TICK, min, max).stream().anyMatch(t -> t.type().equals("minecraft:observer")
                        && (t.blockPos().equals(observerA) || t.blockPos().equals(observerB))),
                "the observer clock's scheduled ticks are found at the observers");
        BlockPos piston = helper.absolutePos(new BlockPos(2, 1, 8));
        helper.assertTrue(r.thingsWithin(level, Kind.BLOCK_EVENT, min, max).stream()
                        .anyMatch(t -> t.type().equals("minecraft:piston") && t.blockPos().equals(piston)),
                "the piston's block events are found at the piston");

        // Chunk ticks: the plot's chunk, measured and listed.
        ChunkPos chunk = ChunkPos.containing(observerA);
        helper.assertTrue(r.things.stream().anyMatch(t -> t.kind() == Kind.CHUNK && t.level().equals(level)
                        && (t.blockPos().getX() >> 4) == chunk.x() && (t.blockPos().getZ() >> 4) == chunk.z()),
                "the plot's chunk has its own tick measured");
        helper.assertTrue(r.chunks.stream().anyMatch(c -> c.level().equals(level) && c.x() == chunk.x() && c.z() == chunk.z() && c.totalNs() > 0),
                "the plot's chunk is in the chunk list");

        // The folds: the hoppers as one type, the vanilla things under minecraft; and every list adds up to the same.
        helper.assertTrue(r.types.stream().anyMatch(g -> g.name().equals("minecraft:hopper") && g.kind() == Kind.BLOCK_ENTITY && g.things() >= 16),
                "the hoppers are folded into one type");
        helper.assertTrue(r.mods.stream().anyMatch(g -> g.name().equals("minecraft")), "the vanilla things are folded into minecraft");
        long things = r.things.stream().mapToLong(ScanReport.Thing::totalNs).sum();
        helper.assertTrue(r.measuredNs() == things, "the kinds add up to the things");
        helper.assertTrue(r.types.stream().mapToLong(ScanReport.Group::totalNs).sum() == things, "the types add up to the things");
        helper.assertTrue(r.chunks.stream().mapToLong(ScanReport.ChunkGroup::totalNs).sum() == things, "the chunks add up to the things");
        helper.assertTrue(r.mods.stream().mapToLong(ScanReport.Group::totalNs).sum() + r.kindNs[Kind.CHUNK] == things,
                "the mods add up to the things (chunk ticks apart)");
        // Each thing charged only its own time: together they fit inside the ticks they were measured in.
        helper.assertTrue(r.measuredNs() <= r.tickNsTotal, "what was measured (" + r.measuredNs() + " ns) fits in the ticks (" + r.tickNsTotal + " ns)");
        helper.assertTrue(r.tickNsMax >= r.perTick(r.tickNsTotal), "the worst tick is at least the mean");

        // A long scan stopped early reports the ticks it had and leaves the hooks idle.
        helper.assertTrue(TickScan.start(10_000, report -> helper.runAfterDelay(1, () -> {
            helper.assertTrue(report.ticks > 0 && report.ticks < 10_000, "the stopped scan reports its " + report.ticks + " ticks");
            helper.assertFalse(Probe.active, "the hooks are idle after a stop");
            helper.succeed();
        })), "the second scan starts");
        helper.runAfterDelay(12, () -> helper.assertTrue(TickScan.stopNow(true), "the long scan stops"));
    }

    /** Runs {@code then} once no scan is running (another test's may be). */
    private static void whenNoScan(GameTestHelper helper, Runnable then) {
        if (TickScan.ticksLeft() >= 0) {
            helper.runAfterDelay(5, () -> whenNoScan(helper, then));
            return;
        }
        then.run();
    }

    /** Busy for {@code ns} nanoseconds of wall time. */
    private static void spin(long ns) {
        long end = System.nanoTime() + ns;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }
}
