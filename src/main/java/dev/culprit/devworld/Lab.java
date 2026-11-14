package dev.culprit.devworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import dev.culprit.Culprit;
import dev.culprit.client.ClientReport;
import dev.culprit.client.CulpritScreen;
import dev.culprit.client.Overlay;
import dev.culprit.client.ScanScreen;
import dev.culprit.net.CulpritNetwork;
import dev.culprit.net.ReportData;
import dev.culprit.scan.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Development aid, left out of the released jar (build.gradle's jar task; found by its annotation, so nothing in the
 * jar names it): the world the user tests in (DESIGN §7). With {@code -Dculprit.lab=true} (the {@code lab} Gradle
 * run) the client makes a flat creative world, "Culprit Lab" (run/saves/culprit_lab), builds lag sources of each kind
 * round the spawn with a sign at each saying what it is, scans the server for ten seconds as a player would from the
 * scan menu, photographs the results on the world, in the results screen's tabs, the scan menu (ready, and with a
 * scan running) and the chat (run/screenshots/lab_*.png), and quits, the world saved to copy into the test instance's
 * saves. Daylight, no weather, no mob spawning.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = Culprit.MODID)
public final class Lab {
    private static final boolean ENABLED = Boolean.getBoolean("culprit.lab");
    /** Ticks for the world to come in and the machines to get going before the scan, and between pictures. */
    private static final int SETTLE = 200, EACH = 40, SCAN_TICKS = 200;
    /** What a view shows besides the world, when not a results tab (0 and up): nothing, the scan menu, the menu with a scan just started from it, the chat. */
    private static final int NO_SCREEN = -1, SCAN_MENU = -2, SCAN_RUNNING = -3, CHAT = -4, LOOK = -5;

    /** A picture: its name, where it is taken from and which way it looks, and what is on screen. */
    private record View(String name, Vec3 at, float yaw, float pitch, int screenTab, boolean mark, String search, boolean spikes, boolean frames) {}

    private static boolean worldMade, built, scanned, hudWanted, framesScanned, framesChecked;
    private static int waited, shot;
    private static List<View> views = List.of();
    private static Vec3 spawn = Vec3.ZERO;
    private static int ground;

    private Lab() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.gui.setScreen(null);
        }
        if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        }
        if (mc.level == null && !worldMade && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            try {
                var settings = new LevelSettings("Culprit Lab", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true,
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(fresh(mc, "culprit_lab"), settings, new WorldOptions(0L, false, false), Lab::flat,
                        mc.gui.screen());
            } catch (IOException e) {
                Culprit.LOGGER.error("Lab: could not make a world", e);
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        if (!built) {
            built = true;
            server.execute(() -> build(server.overworld()));
            return;
        }
        waited++;
        if (!scanned) {
            if (waited == SETTLE) {
                scanned = true;
                // As a player starts one from the scan menu (F8): the request, then the chat as it starts and ends.
                ClientPacketDistributor.sendToServer(new CulpritNetwork.ScanRequest(SCAN_TICKS / 20));
                Culprit.LOGGER.info("Lab: scanning for {} ticks", SCAN_TICKS);
            }
            return;
        }
        ReportData report = ClientReport.data();
        if (report == null) {
            return;
        }
        if (!framesScanned) {
            framesScanned = true;
            ClientReport.startFrameScan(6);                        // as the scan menu's Frames row does: over the first views
        }
        ReportData frames = ClientReport.framesData();
        if (!framesChecked && frames != null) {
            // The frame hooks are optional (one that misses its place must not stop a game): say here which parts were never
            // timed, so a hook that missed is seen.
            framesChecked = true;
            StringBuilder never = new StringBuilder();
            for (int p = 0; p < Math.min(dev.culprit.scan.FramePhase.COUNT, frames.phaseNs.length); p++) {
                if (frames.phaseNs[p] == 0) {
                    never.append(' ').append(dev.culprit.scan.FramePhase.key(p));
                }
            }
            Culprit.LOGGER.info("Lab: frame scan: {} frames, {} ns a frame; entities {} ns, block entities {} ns; parts {}; never timed:{}",
                    frames.ticks, frames.perTick(frames.tickNsTotal), frames.perTick(frames.kindNs[Kind.ENTITY]),
                    frames.perTick(frames.kindNs[Kind.BLOCK_ENTITY]), java.util.Arrays.toString(frames.phaseNs), never.length() == 0 ? " none" : never);
        }
        if (mc.gui.hud.isHidden() == hudWanted) {
            mc.gui.hud.toggle();                                   // no hand, hotbar or chat in the pictures, but the chat's
        }
        // Each view: the camera and what is on screen, a wait for the frame to settle, the picture.
        int t = ++shot;
        if (t % EACH != 0) {
            return;
        }
        int step = t / EACH;
        if (step > 1 && step - 2 < views.size()) {
            View v = views.get(step - 2);
            Screenshot.grab(mc.gameDirectory, "lab_" + v.name() + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> Culprit.LOGGER.info("Lab: {}", msg.getString()));
        }
        if (step - 1 < views.size()) {
            View v = views.get(step - 1);
            if (v.mark()) {
                Overlay.markThing(farthestCostly(report, mc));
            }
            hudWanted = v.screenTab() == CHAT || v.screenTab() == LOOK;    // the readout under the crosshair is on the HUD
            Overlay.setShown(true);                                // as the results screen's button does
            if (ClientReport.framesShown() != v.frames()) {
                ClientReport.showFrames(v.frames());               // as the results screen's Server ticks / Frames does
            }
            if (ClientReport.spikesShown() != v.spikes()) {
                ClientReport.showSpikes(v.spikes());               // as the results screen's Average / Lag spikes switch does
            }
            if (v.screenTab() >= 0) {
                CulpritScreen.showTab(v.screenTab());
                if (!(mc.gui.screen() instanceof CulpritScreen)) {
                    mc.gui.setScreen(new CulpritScreen());
                }
                if (mc.gui.screen() instanceof CulpritScreen screen) {
                    screen.setSearch(v.search());
                }
            } else if (v.screenTab() == SCAN_MENU) {
                mc.gui.setScreen(new ScanScreen());
            } else if (v.screenTab() == SCAN_RUNNING) {
                ClientPacketDistributor.sendToServer(new CulpritNetwork.ScanRequest(30));     // as the menu's 30 s button does
                mc.gui.setScreen(new ScanScreen());
            } else if (v.screenTab() == CHAT) {
                mc.gui.setScreen(new ChatScreen("", false));
            } else if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            server.execute(() -> server.getPlayerList().getPlayers().getFirst()
                    .teleportTo(server.overworld(), v.at().x, v.at().y, v.at().z, Set.of(), v.yaw(), v.pitch(), true));
            return;
        }
        if (step - 1 == views.size()) {
            mc.gui.setScreen(null);
            server.execute(() -> server.getPlayerList().getPlayers().getFirst()
                    .teleportTo(server.overworld(), spawn.x, spawn.y, spawn.z, Set.of(), 180f, 10f, true));
            return;
        }
        if (step - 1 > views.size() + 1) {
            Culprit.LOGGER.info("Lab built");
            mc.stop();
        }
    }

    /** The costliest thing at least 12 blocks from the spawn, to mark for the picture of a marked thing. */
    private static int farthestCostly(ReportData r, Minecraft mc) {
        for (int i = 0; i < r.things; i++) {
            if (!Kind.isChunkWide(r.kind[i]) && BlockPos.of(r.pos[i]).distToCenterSqr(spawn) > 12 * 12) {
                return i;
            }
        }
        return 0;
    }

    /**
     * On the server: the rules; round the spawn, lag sources of each kind with a sign at each: hopper chains fed
     * from chests (block entities), a room of villagers and a pen of cows (entities), dropped items (entities), an
     * observer clock driving a row of pistons (scheduled block ticks, block events); then the views to take.
     */
    private static void build(ServerLevel level) {
        var commands = level.getServer().getCommands();
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        for (String c : List.of("time set 6000", "gamerule advance_time false", "gamerule advance_weather false", "weather clear",
                "gamerule spawn_mobs false", "difficulty peaceful", "kill @e[type=!player]", "gamerule command_block_output false",
                "scoreboard objectives add culprit_lab dummy")) {
            commands.performPrefixedCommand(source, c);
        }
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                level.getChunk(cx, cz);
            }
        }
        ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        int y = ground;

        // Hopper chains: six columns, a full chest on top feeding five hoppers down into a chest on the ground.
        for (int i = 0; i < 6; i++) {
            int x = -13 + i * 2;
            level.setBlock(new BlockPos(x, y, 8), Blocks.CHEST.defaultBlockState(), 3);
            for (int h = 1; h <= 5; h++) {
                level.setBlock(new BlockPos(x, y + h, 8), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
            }
            BlockPos top = new BlockPos(x, y + 6, 8);
            level.setBlock(top, Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(top) instanceof Container chest) {
                for (int s = 0; s < chest.getContainerSize(); s++) {
                    chest.setItem(s, new ItemStack(Items.COBBLESTONE, 64));
                }
            }
        }
        sign(level, new BlockPos(-8, y, 5), List.of("Hopper chains", "(block entities):", "chests empty into", "chests below"));

        // A row of 40 idle hoppers beside them: each ticks every tick even with nothing to move.
        for (int x = -13; x <= -4; x++) {
            for (int h = 0; h < 4; h++) {
                level.setBlock(new BlockPos(x, y + h, 11), Blocks.HOPPER.defaultBlockState(), 3);
            }
        }

        // A glass room of villagers (their brains are among the costliest entities).
        box(level, 5, y, 6, 12, y + 3, 13);
        for (int i = 0; i < 24; i++) {
            var villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            if (villager != null) {
                villager.snapTo(6.5 + (i % 6), y, 7.5 + (i / 6) * 1.5, 0f, 0f);
                villager.setPersistenceRequired();
                level.addFreshEntity(villager);
            }
        }
        sign(level, new BlockPos(8, y, 5), List.of("Villagers", "(entities):", "24 in a room", ""));

        // A pen of cows.
        for (int x = -14; x <= -3; x++) {
            for (int z = -15; z <= -6; z++) {
                if (x == -14 || x == -3 || z == -15 || z == -6) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
                }
            }
        }
        for (int i = 0; i < 40; i++) {
            var cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
            if (cow != null) {
                cow.snapTo(-12.5 + (i % 8) * 1.2, y, -13.5 + (i / 8) * 1.4, i * 37f, 0f);
                cow.setPersistenceRequired();
                level.addFreshEntity(cow);
            }
        }
        sign(level, new BlockPos(-8, y, -5), List.of("Cows", "(entities):", "40 in a pen", ""));

        // Dropped items, of many kinds so they do not merge.
        Item[] kinds = {Items.DIRT, Items.STONE, Items.SAND, Items.GRAVEL, Items.OAK_LOG, Items.STICK, Items.APPLE, Items.BONE, Items.STRING,
                Items.FEATHER, Items.WHEAT_SEEDS, Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.REDSTONE, Items.COAL, Items.FLINT, Items.CLAY_BALL,
                Items.SNOWBALL, Items.EGG, Items.LEATHER};
        for (int i = 0; i < 160; i++) {
            ItemEntity item = new ItemEntity(level, 6.2 + (i % 16) * 0.42, y + 0.2, -14.0 + (i / 16) * 0.8, new ItemStack(kinds[i % kinds.length]));
            item.setDeltaMovement(Vec3.ZERO);
            item.setUnlimitedLifetime();
            level.addFreshEntity(item);
        }
        sign(level, new BlockPos(9, y, -5), List.of("Dropped items", "(entities):", "160 lying about", ""));

        // An observer clock driving eight pistons through redstone dust on top of them. The pistons hang a block up,
        // facing down, so their tops stay whole when they push (dust on an extended piston's open side would drop).
        for (int x = -8; x <= -1; x++) {
            level.setBlock(new BlockPos(x, y + 1, -1), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.DOWN), 3);
            level.setBlock(new BlockPos(x, y + 2, -1), Blocks.REDSTONE_WIRE.defaultBlockState(), 3);
        }
        level.setBlock(new BlockPos(0, y + 2, -1), Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.EAST), 3);
        level.setBlock(new BlockPos(1, y + 2, -1), Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.WEST), 3);
        sign(level, new BlockPos(-4, y, 1), List.of("Observer clock", "and pistons:", "block ticks and", "block events"));

        // Lag spikes: command blocks counting ticks, and every 3 seconds a big /fill (8,192 blocks of stone behind
        // everything, then air again): a few slow ticks among quick ones, charged to the repeating command block.
        String box = "-16 " + (y + 1) + " 24 15 " + (y + 8) + " 55";
        List<String> clock = List.of("scoreboard players add #clock culprit_lab 1",
                "execute if score #clock culprit_lab matches 60 run fill " + box + " minecraft:stone",
                "execute if score #clock culprit_lab matches 120.. run fill " + box + " minecraft:air",
                "execute if score #clock culprit_lab matches 120.. run scoreboard players set #clock culprit_lab 0");
        for (int k = 0; k < clock.size(); k++) {
            BlockPos at = new BlockPos(17 + k, y, 0);
            level.setBlock(at, (k == 0 ? Blocks.REPEATING_COMMAND_BLOCK : Blocks.CHAIN_COMMAND_BLOCK).defaultBlockState()
                    .setValue(CommandBlock.FACING, Direction.EAST), 3);
            if (level.getBlockEntity(at) instanceof CommandBlockEntity block) {
                block.getCommandBlock().setCommand(clock.get(k));
                block.setAutomatic(true);
                block.setChanged();
            }
        }
        sign(level, new BlockPos(18, y, 2), List.of("Command blocks:", "a big /fill every", "3 seconds:", "lag spikes"));

        // Particles: twelve signal campfires on hay bales, their smoke rising in tall columns (a frame scan's particles).
        for (int x = 6; x <= 17; x++) {
            level.setBlock(new BlockPos(x, y, 18), Blocks.HAY_BLOCK.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, y + 1, 18), Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.SIGNAL_FIRE, true), 3);
        }
        sign(level, new BlockPos(11, y, 16), List.of("Campfires:", "signal smoke", "(particles, for", "a frame scan)"));

        // The spawn, with what to do.
        sign(level, new BlockPos(0, y, 3), List.of("Culprit Lab", "F8: scan menu", "F9: results", "(rebindable)"));
        sign(level, new BlockPos(2, y, 3), List.of("Or type", "/culprit scan", "then /culprit", "top things"));
        commands.performPrefixedCommand(source, "setworldspawn 1 " + y + " 2");
        spawn = new Vec3(1.5, y, 2.5);
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;                       // (else the camera falls between the views)
        player.onUpdateAbilities();
        player.teleportTo(level, spawn.x, spawn.y, spawn.z, Set.of(), 180f, 10f, true);

        List<View> list = new ArrayList<>();
        list.add(eye("overview", 0.5, y + 16.0, -24.0, 0f, 32f, NO_SCREEN, false));
        list.add(eye("hoppers", -8.5, y + 3.2, 2.0, 0f, 8f, NO_SCREEN, false));
        list.add(eye("villagers", 8.5, y + 4.6, 1.5, 0f, 30f, NO_SCREEN, false));
        list.add(eye("cows_items", -1.0, y + 7.0, -2.0, 180f, 40f, NO_SCREEN, false));
        list.add(eye("pistons", -3.5, y + 2.6, -5.5, 0f, 18f, NO_SCREEN, false));
        list.add(eye("marked", -14.0, y + 3.0, -2.0, -135f, 12f, NO_SCREEN, true));
        list.add(eye("looking", -8.5, y + 1.5, 4.5, 0f, 0f, LOOK, false));          // at the lowest hopper of a chain
        list.add(eye("screen_tick", 1.5, y + 1.6, 2.5, 180f, 10f, 0, false));
        list.add(eye("screen_things", 1.5, y + 1.6, 2.5, 180f, 10f, 1, false));
        list.add(eye("screen_types", 1.5, y + 1.6, 2.5, 180f, 10f, 2, false));
        list.add(eye("screen_mods", 1.5, y + 1.6, 2.5, 180f, 10f, 3, false));
        list.add(eye("screen_chunks", 1.5, y + 1.6, 2.5, 180f, 10f, 4, false));
        Vec3 screenAt = new Vec3(1.5, y + 1.6 - 1.62, 2.5), overviewAt = new Vec3(0.5, y + 16.0 - 1.62, -24.0);
        list.add(new View("screen_search", screenAt, 180f, 10f, 1, false, "hopper", false, false));
        list.add(new View("screen_spikes", screenAt, 180f, 10f, 0, false, "", true, false));
        list.add(new View("screen_spikes_things", screenAt, 180f, 10f, 1, false, "", true, false));
        list.add(new View("overview_spikes", overviewAt, 0f, 32f, NO_SCREEN, false, "", true, false));
        list.add(new View("frames_frame", screenAt, 180f, 10f, 0, false, "", false, true));
        list.add(new View("frames_things", screenAt, 180f, 10f, 1, false, "", false, true));
        list.add(new View("frames_types", screenAt, 180f, 10f, 2, false, "", false, true));
        list.add(new View("frames_overview", overviewAt, 0f, 32f, NO_SCREEN, false, "", false, true));
        list.add(eye("scan_menu", 1.5, y + 1.6, 2.5, 180f, 10f, SCAN_MENU, false));
        list.add(eye("scan_running", 1.5, y + 1.6, 2.5, 180f, 10f, SCAN_RUNNING, false));
        list.add(eye("chat", 1.5, y + 1.6, 2.5, 180f, 10f, CHAT, false));
        views = list;
        Culprit.LOGGER.info("Lab: lag sources set out round the spawn at y {}", y);
    }

    /** A view from an eye at (x, eyeY, z): the player's feet put 1.62 below it. */
    private static View eye(String name, double x, double eyeY, double z, float yaw, float pitch, int screenTab, boolean mark) {
        return new View(name, new Vec3(x, eyeY - 1.62, z), yaw, pitch, screenTab, mark, "", false, false);
    }

    /** A glass room with a floor of stone: walls and roof of glass. */
    private static void box(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            boolean wall = p.getX() == x0 || p.getX() == x1 || p.getZ() == z0 || p.getZ() == z1 || p.getY() == y1;
            level.setBlock(p, wall ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** A standing oak sign facing south (toward the spawn's side), four lines on its front. */
    private static void sign(ServerLevel level, BlockPos at, List<String> lines) {
        level.setBlock(at, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0), 3);
        if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
            SignText.Mutable text = sign.getText(SignTextSlot.FRONT).asMutable();
            for (int i = 0; i < Math.min(4, lines.size()); i++) {
                text.setLine(i, Component.literal(lines.get(i)));
            }
            sign.setText(text.asImmutable(), SignTextSlot.FRONT);
            sign.setChanged();
            level.sendBlockUpdated(at, sign.getBlockState(), sign.getBlockState(), 3);
        }
    }

    /** A flat world of grass over dirt and stone, a plains biome. */
    private static WorldDimensions flat(HolderLookup.Provider registries) {
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        var settings = FlatLevelGeneratorSettings.getDefault(biomes, registries.lookupOrThrow(Registries.STRUCTURE_SET),
                        registries.lookupOrThrow(Registries.PLACED_FEATURE))
                .withBiomeAndLayers(List.of(new FlatLayerInfo(1, Blocks.BEDROCK), new FlatLayerInfo(20, Blocks.STONE),
                        new FlatLayerInfo(3, Blocks.DIRT), new FlatLayerInfo(1, Blocks.GRASS_BLOCK)),
                        java.util.Optional.empty(), biomes.getOrThrow(Biomes.PLAINS));
        return WorldPresets.createTestWorldDimensions(registries).replaceOverworldGenerator(registries, new FlatLevelSource(settings));
    }

    /** Deletes the worlds earlier runs left under {@code name} and gives the folder for this run's. */
    private static String fresh(Minecraft mc, String name) throws IOException {
        Path saves = mc.getLevelSource().getBaseDir();
        if (Files.isDirectory(saves)) {
            try (var old = Files.list(saves)) {
                for (Path dir : old.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith(name)).toList()) {
                    try (var walk = Files.walk(dir)) {
                        walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                    }
                }
            }
        }
        return FileUtil.findAvailableName(saves, name, "");
    }
}
