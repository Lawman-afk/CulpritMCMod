package dev.culprit.gametest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import dev.culprit.Culprit;
import dev.culprit.chunks.ChunkCensus;
import dev.culprit.chunks.ChunkText;
import dev.culprit.chunks.ForcedBy;
import dev.culprit.net.ChunkData;
import dev.culprit.scan.Kind;
import dev.culprit.scan.Phase;
import dev.culprit.scan.Probe;
import dev.culprit.scan.ScanReport;
import dev.culprit.scan.TickScan;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The tick scan's tests (DESIGN §7). Each plants lag sources whose places and kinds are known, scans, and checks the
 * report: that every source is found where it is, charged as the kind it is, its time its own, and the report's
 * figures adding up. Only one scan runs at a time, so a test that scans waits until none runs.
 */
final class ScanTests {
    private static final int SCAN_TICKS = 40;
    private static final int RANDOM_SCAN_TICKS = 20, RANDOM_TICK_SPEED = 200;
    private static final int SPIKE_SCAN_TICKS = 60, SPIKES = 4;
    private static final long SPIKE_NS = 25_000_000;

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

        // Random ticks: a field of grass with the random tick speed raised for the scan. They are charged to the grass
        // in the plot's chunks, as random ticks, a type of their own, apart from the chunks' upkeep.
        CulpritGameTests.testAlone("scan_charges_random_ticks", CulpritGameTests.FLOOR_16, 300, helper -> {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    helper.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
                }
            }
            GameRules rules = helper.getLevel().getGameRules();
            MinecraftServer server = helper.getLevel().getServer();
            int speed = rules.get(GameRules.RANDOM_TICK_SPEED);
            helper.runAfterDelay(5, () -> whenNoScan(helper, () -> {
                helper.assertTrue(TickScan.start(RANDOM_SCAN_TICKS, report -> {
                    rules.set(GameRules.RANDOM_TICK_SPEED, speed, server);
                    helper.runAfterDelay(1, () -> checkRandomTicks(helper, report));
                }), "the scan starts");
                rules.set(GameRules.RANDOM_TICK_SPEED, RANDOM_TICK_SPEED, server);
            }));
        });

        // Lag spikes: a row of hoppers, and four ticks of the scan made slow on purpose (busy for 25 ms in the test's own
        // callback, which runs in the server's own work). They are the spikes, found where they ran; the average keeps
        // every tick.
        CulpritGameTests.testAlone("scan_catches_lag_spikes", CulpritGameTests.FLOOR_16, 400, helper -> {
            for (int x = 0; x < 16; x++) {
                helper.setBlock(x, 1, 2, Blocks.HOPPER);
            }
            helper.runAfterDelay(5, () -> whenNoScan(helper, () -> {
                helper.assertTrue(TickScan.start(SPIKE_SCAN_TICKS, report -> helper.runAfterDelay(1, () -> checkSpikes(helper, report))),
                        "the scan starts");
                for (int k = 1; k <= SPIKES; k++) {
                    // Each callback its own object: the test keeps them in a map by callback, so one lambda used four
                    // times would run once.
                    long ns = SPIKE_NS + k;
                    helper.runAfterDelay(k * 12, () -> spin(ns));
                }
            }));
        });

        // The chunk inspector: far from the plot, a chunk forced by /forceload (the server runs it), one forced the game's
        // way by a mod's code (this one's), one kept by a mod's chunk loader that a hopper owns, and one by a loader
        // owned by an id that is no entity (a team's, the way FTB Chunks keeps a team's claims). Each is named as such,
        // with the mod, block or owner behind it, their neighbours are loaded by them, the plot's own chunk is the test
        // structure's, and the record of who forced what is saved and loaded whole and drops a chunk let go of.
        CulpritGameTests.testAlone("chunks_name_their_loaders", CulpritGameTests.FLOOR_16, 400, helper -> {
            ServerLevel level = helper.getLevel();
            helper.setBlock(0, 1, 0, Blocks.HOPPER);
            BlockPos owner = helper.absolutePos(new BlockPos(0, 1, 0));
            ChunkPos home = ChunkPos.containing(owner);
            Loaders at = new Loaders(home.x() + 40, home.x() + 46, home.x() + 52, home.x() + 58, home.z());
            level.getServer().getCommands().performPrefixedCommand(serverSource(level), "forceload add " + at.command() * 16 + " " + at.z() * 16);
            level.setChunkForced(at.code(), at.z(), true);
            GameTestRegistration.CONTROLLER.forceChunk(level, owner, at.block(), at.z(), true, false);
            GameTestRegistration.CONTROLLER.forceChunk(level, TEAM, at.team(), at.z(), true, false);
            waitForChunks(helper, level, owner, at, 0);
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
        // The piston's moving block is a new block entity at every push: a place's block entities are one thing.
        List<ScanReport.Thing> moving = r.thingsWithin(level, Kind.BLOCK_ENTITY, min, max).stream()
                .filter(t -> t.type().equals("minecraft:piston")).toList();
        helper.assertTrue(!moving.isEmpty(), "the piston's moving block is measured");
        helper.assertTrue(moving.stream().map(ScanReport.Thing::pos).distinct().count() == moving.size(),
                "each place's moving block is one thing, not one a push (" + moving.size() + " things)");

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
        helper.assertTrue(r.mods.stream().mapToLong(ScanReport.Group::totalNs).sum() + r.kindNs[Kind.CHUNK] + r.kindNs[Kind.SPAWNING] == things,
                "the mods add up to the things (the game's chunk upkeep and mob spawning apart)");
        // Each thing charged only its own time: together they fit inside the ticks they were measured in.
        helper.assertTrue(r.measuredNs() <= r.tickNsTotal, "what was measured (" + r.measuredNs() + " ns) fits in the ticks (" + r.tickNsTotal + " ns)");

        // The rest of the tick: the server's own parts, each charged its own time (what ran inside taken out); with
        // the things they fit in the ticks, and they cover nearly all of it.
        helper.assertTrue(r.phaseNs[Phase.LEVEL] > 0 && r.phaseNs[Phase.SERVER] > 0 && r.phaseNs[Phase.CHUNK_SOURCE] > 0,
                "the levels' upkeep, the server's own work and the chunks' loading were timed: " + Arrays.toString(r.phaseNs));
        long accounted = r.measuredNs() + r.phasesNs();
        helper.assertTrue(accounted <= r.tickNsTotal, "the things and parts (" + accounted + " ns) fit in the ticks (" + r.tickNsTotal + " ns)");
        double covered = (double) (accounted + r.selfNs) / r.tickNsTotal;
        Culprit.LOGGER.info("scan_finds_lag_sources: {} ns a tick: things {}, parts {} {}, Culprit's timing {}, not measured {} ({}% covered)",
                r.perTick(r.tickNsTotal), r.perTick(r.measuredNs()), r.perTick(r.phasesNs()), Arrays.toString(r.phaseNs), r.perTick(r.selfNs),
                r.perTick(r.unaccountedNs()), Math.round(covered * 100));
        helper.assertTrue(covered >= 0.75, "the things, the parts and Culprit's timing cover most of the tick, not " + Math.round(covered * 100) + "%");
        helper.assertTrue(r.tickNsMax >= r.perTick(r.tickNsTotal), "the worst tick is at least the mean");

        // A long scan stopped early reports the ticks it had and leaves the hooks idle.
        helper.assertTrue(TickScan.start(10_000, report -> helper.runAfterDelay(1, () -> {
            helper.assertTrue(report.ticks > 0 && report.ticks < 10_000, "the stopped scan reports its " + report.ticks + " ticks");
            helper.assertFalse(Probe.active, "the hooks are idle after a stop");
            helper.succeed();
        })), "the second scan starts");
        helper.runAfterDelay(12, () -> helper.assertTrue(TickScan.stopNow(true), "the long scan stops"));
    }

    private static void checkRandomTicks(GameTestHelper helper, ScanReport r) {
        BlockPos a = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos b = helper.absolutePos(new BlockPos(15, 1, 15));
        int minX = Math.min(a.getX(), b.getX()) >> 4, maxX = Math.max(a.getX(), b.getX()) >> 4;
        int minZ = Math.min(a.getZ(), b.getZ()) >> 4, maxZ = Math.max(a.getZ(), b.getZ()) >> 4;
        var level = helper.getLevel().dimension();
        List<ScanReport.Thing> grass = r.things.stream().filter(t -> t.kind() == Kind.RANDOM_TICK && t.level().equals(level)
                && t.type().equals("minecraft:grass_block")
                && (t.blockPos().getX() >> 4) >= minX && (t.blockPos().getX() >> 4) <= maxX
                && (t.blockPos().getZ() >> 4) >= minZ && (t.blockPos().getZ() >> 4) <= maxZ).toList();
        long calls = grass.stream().mapToLong(ScanReport.Thing::calls).sum();
        // 256 grass blocks in a section of 4096 at 200 picks a tick: about 12 random ticks a tick, 250 in the scan.
        helper.assertTrue(!grass.isEmpty() && calls >= 100,
                "the grass's random ticks are measured in the plot's chunks (" + calls + " calls in " + grass.size() + " chunks)");
        helper.assertTrue(grass.stream().allMatch(t -> t.totalNs() > 0), "the grass's random ticks took time");
        helper.assertTrue(r.types.stream().anyMatch(g -> g.name().equals("minecraft:grass_block") && g.kind() == Kind.RANDOM_TICK),
                "the grass's random ticks are a type of their own");
        helper.assertTrue(r.mods.stream().anyMatch(g -> g.name().equals("minecraft") && g.totalNs() >= grass.stream().mapToLong(ScanReport.Thing::totalNs).sum()),
                "the grass's random ticks count to its mod, minecraft");
        helper.assertTrue(r.things.stream().anyMatch(t -> t.kind() == Kind.CHUNK && t.level().equals(level)
                        && (t.blockPos().getX() >> 4) >= minX && (t.blockPos().getX() >> 4) <= maxX
                        && (t.blockPos().getZ() >> 4) >= minZ && (t.blockPos().getZ() >> 4) <= maxZ),
                "the plot's chunks have their upkeep measured apart");
        helper.assertTrue(r.measuredNs() + r.phasesNs() <= r.tickNsTotal, "the things and parts fit in the ticks");
        helper.succeed();
    }

    private static void checkSpikes(GameTestHelper helper, ScanReport r) {
        helper.assertTrue(r.ticks == SPIKE_SCAN_TICKS, "the average keeps every tick: " + r.ticks);
        helper.assertTrue(r.medianTickNs > 0 && r.medianTickNs < 20_000_000, "the usual tick is not moved by the spikes: " + r.medianTickNs + " ns");
        helper.assertTrue(r.spikes != null, "the spikes are reported (over " + r.spikeThresholdNs + " ns)");
        ScanReport s = r.spikes;
        helper.assertTrue(s.ticks >= SPIKES && s.ticks <= SPIKES + 4,
                "the " + SPIKES + " slow ticks are the spikes, maybe with a slow tick of the test server's own: " + s.ticks);
        helper.assertTrue(s.tickNsMax >= SPIKE_NS && s.perTick(s.tickNsTotal) >= SPIKE_NS * 4 / 5,
                "the spike ticks took their 25 ms: " + s.perTick(s.tickNsTotal) + " ns on average");
        helper.assertTrue(s.phaseNs[Phase.SERVER] >= SPIKES * (SPIKE_NS - 1_000_000),
                "the slow work is found where it ran, the server's own work: " + Arrays.toString(s.phaseNs));
        helper.assertTrue(s.measuredNs() + s.phasesNs() <= s.tickNsTotal, "the spikes' things and parts fit in their ticks");
        helper.assertTrue(r.tickNsTotal > s.tickNsTotal && r.phaseNs[Phase.SERVER] >= s.phaseNs[Phase.SERVER],
                "the average has the spike ticks in it with every other tick");
        BlockPos a = helper.absolutePos(BlockPos.ZERO);
        BlockPos b = helper.absolutePos(new BlockPos(15, 15, 15));
        List<ScanReport.Thing> hoppers = s.thingsWithin(helper.getLevel().dimension(), Kind.BLOCK_ENTITY, BlockPos.min(a, b), BlockPos.max(a, b))
                .stream().filter(t -> t.type().equals("minecraft:hopper")).toList();
        helper.assertTrue(hoppers.size() == 16 && hoppers.stream().allMatch(t -> t.calls() == s.ticks),
                "the hoppers are in every spike tick: " + hoppers.size() + " of them");
        helper.succeed();
    }

    /** Looks at the chunks until the two far ones are loaded (they are made first), then checks what the census says. */
    /** An id that is no entity, owning a chunk loader the way a team owns FTB Chunks' (its first eight digits name it). */
    private static final UUID TEAM = UUID.nameUUIDFromBytes("culprit test team".getBytes(StandardCharsets.UTF_8));

    /** The chunks x of the four loaders, on one row z. */
    private record Loaders(int command, int code, int block, int team, int z) {}

    private static CommandSourceStack serverSource(ServerLevel level) {
        return level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
    }

    private static void waitForChunks(GameTestHelper helper, ServerLevel level, BlockPos owner, Loaders at, int tries) {
        ChunkData data = ChunkCensus.take(level.getServer());
        ChunkData.Dim d = data.dims.stream().filter(x -> x.dim.equals(level.dimension().identifier().toString())).findFirst().orElseThrow();
        int command = chunkAt(d, at.command(), at.z()), code = chunkAt(d, at.code(), at.z()), block = chunkAt(d, at.block(), at.z());
        int team = chunkAt(d, at.team(), at.z()), next = chunkAt(d, at.command() + 1, at.z());
        if ((command < 0 || code < 0 || block < 0 || team < 0 || next < 0) && tries < 30) {
            helper.runAfterDelay(10, () -> waitForChunks(helper, level, owner, at, tries + 1));
            return;
        }
        long commandKey = ChunkPos.pack(at.command(), at.z()), codeKey = ChunkPos.pack(at.code(), at.z());
        try {
            helper.assertTrue(command >= 0 && code >= 0 && block >= 0 && team >= 0, "the four loaders' chunks are loaded");
            assertKept(helper, d, command, ChunkData.FORCED, "/forceload by the server");
            assertKept(helper, d, code, ChunkData.MODS, "Culprit: forced chunk");
            assertKept(helper, d, block, ChunkData.MODS, "Culprit: Hopper at " + owner.getX() + " " + owner.getY() + " " + owner.getZ());
            assertKept(helper, d, team, ChunkData.MODS, "Culprit: owner " + TEAM.toString().substring(0, 8));
            ChunkPos home = ChunkPos.containing(owner);
            assertKept(helper, d, chunkAt(d, home.x(), home.z()), ChunkData.OTHER, "the game's test structures");
            helper.assertTrue(next >= 0 && d.reason[next] == ChunkData.FORCED && d.status[next] <= d.status[command],
                    "the forced chunk's neighbour is loaded by it, and no more busy than it");
            List<String> lines = ChunkText.loaders(d, 1000);
            for (String loader : List.of("/forceload by the server", "Culprit: forced chunk", "Culprit: Hopper", "Culprit: owner")) {
                helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith(loader)), "the text names " + loader + ": " + lines);
            }

            // The record goes with the level's data: saved and loaded, it is the same.
            ForcedBy record = level.getDataStorage().get(ForcedBy.TYPE);
            helper.assertTrue(record != null && record.get(codeKey) != null, "the level keeps a record of who forced what");
            ForcedBy loaded = ForcedBy.CODEC.parse(NbtOps.INSTANCE, ForcedBy.CODEC.encodeStart(NbtOps.INSTANCE, record).getOrThrow()).getOrThrow();
            helper.assertTrue(record.get(codeKey).equals(loaded.get(codeKey)) && record.get(commandKey).equals(loaded.get(commandKey)),
                    "the record saves and loads as it was");
            level.getServer().getCommands().performPrefixedCommand(serverSource(level), "forceload remove " + at.command() * 16 + " " + at.z() * 16);
            helper.assertTrue(ForcedBy.of(level, commandKey) == null && !level.getForceLoadedChunks().contains(commandKey),
                    "/forceload remove lets the chunk go, and its record with it");
            helper.succeed();
        } finally {
            level.setChunkForced(at.command(), at.z(), false);
            level.setChunkForced(at.code(), at.z(), false);
            GameTestRegistration.CONTROLLER.forceChunk(level, owner, at.block(), at.z(), false, false);
            GameTestRegistration.CONTROLLER.forceChunk(level, TEAM, at.team(), at.z(), false, false);
        }
    }

    private static void assertKept(GameTestHelper helper, ChunkData.Dim d, int chunk, byte reason, String owner) {
        String why = chunk >= 0 && d.source[chunk] >= 0 ? d.sowner[d.source[chunk]] : "nothing";
        helper.assertTrue(chunk >= 0 && d.reason[chunk] == reason && why.equals(owner), "chunk "
                + (chunk >= 0 ? d.x[chunk] + ", " + d.z[chunk] : "?") + " is kept by \"" + owner + "\" (reason " + reason + "), not \"" + why
                + "\" (reason " + (chunk >= 0 ? d.reason[chunk] : -1) + ")");
    }

    private static int chunkAt(dev.culprit.net.ChunkData.Dim d, int x, int z) {
        for (int i = 0; i < d.chunks; i++) {
            if (d.x[i] == x && d.z[i] == z) {
                return i;
            }
        }
        return -1;
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
