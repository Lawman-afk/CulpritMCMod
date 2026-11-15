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
import dev.culprit.config.CulpritClientConfig;
import dev.culprit.net.CulpritNetwork;
import dev.culprit.net.ReportData;
import dev.culprit.scan.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.FileUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Development aid, left out of the released jar (build.gradle's jar task) and kept on main alone (the branches' port
 * tools leave it out): the mod page's pictures, from a normally generated world, as the rule for public pictures asks
 * (real worlds, structures as the world made them). With {@code -Dculprit.showcase=true} (the {@code showcase} Gradle
 * run; {@code -PshotSize=1920x1080} for the page, {@code -Pseed=<n>} for another world, {@code -Pvillage=<kind>} for
 * another kind of village) the client makes a fresh default world, "Culprit Showcase", flies to the plains village
 * nearest its spawn, scans the server for ten seconds as a player would from the scan menu, then the frames, and
 * photographs the results on the village from many sides and times of day, the thing under the crosshair read out, the
 * results screen's tabs, the loaded chunks, the scan menu, the chat, the HUD line and the settings
 * (run/screenshots/showcase_*.png), then quits. Nothing is built: the village, its villagers and animals are as the
 * world made them. Only the time of day is set for each picture, and one chunk beside the village is kept loaded by
 * /forceload, as a player keeps a farm loaded, for the loaded chunks' pictures.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = Culprit.MODID)
public final class Showcase {
    private static final boolean ENABLED = Boolean.getBoolean("culprit.showcase");
    private static final long SEED = Long.getLong("culprit.seed", 20261115L);
    /** The kind of village to go to: plains, taiga, snowy, savanna, desert, or any. */
    private static final String VILLAGE = System.getProperty("culprit.village", "plains");
    /** Ticks for the village's chunks to be made and drawn once there; between pictures; the scans' seconds. */
    private static final int SETTLE = 900, EACH = 40, SCAN_SECONDS = 10, FRAME_SECONDS = 6;
    /** What a view shows besides the world, when not a results tab (0 and up). */
    private static final int NO_SCREEN = -1, SCAN_MENU = -2, CHAT = -4, LOOK = -5, HUD = -6, SETTINGS = -7;
    /** A thing a view is placed by, found in the results: none, the costliest villager, block entity or entity (marked). */
    private static final int NONE = 0, VILLAGER = 1, BLOCK_ENTITY = 2, MARKED = 3;
    private static final long NOON = 6000, NIGHT = 18000;

    /**
     * A picture: its name; its eye and which way it looks (or, for one placed by a thing, the eye's offset from the
     * thing); the time of day; what is on screen; the results' section (0 ticks, 1 frames, 2 chunks); the thing it is
     * placed by; how many labels.
     */
    private record View(String name, Vec3 eye, float yaw, float pitch, long time, int screenTab, int section, int by, int labels) {}

    private static boolean worldMade, located, planned, scanned, framesStarted;
    private static int waited, shot, aimEntity = -1;
    private static long aimBlock = Long.MIN_VALUE, timeSet = -1;
    private static boolean hudWanted;
    private static volatile List<View> views = List.of();
    private static volatile Vec3 center;

    private Showcase() {}

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
                var settings = new LevelSettings("Culprit Showcase", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true,
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(fresh(mc, "culprit_showcase"), settings, new WorldOptions(SEED, true, false),
                        WorldPresets::createNormalWorldDimensions, mc.gui.screen());
            } catch (IOException e) {
                Culprit.LOGGER.error("Showcase: could not make a world", e);
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        if (!located) {
            located = true;
            mc.options.renderDistance().set(16);
            // The game holds a window nobody touches to 30 frames a second as if its player were away: as a player plays.
            mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            CulpritClientConfig.RANGE.set(112);                    // (a player's setting: the whole village boxed from afar)
            server.execute(() -> locate(server.overworld()));
            return;
        }
        if (center == null || ++waited < SETTLE) {
            return;
        }
        if (!planned) {
            planned = true;
            server.execute(() -> plan(server.overworld()));
            return;
        }
        if (views.isEmpty()) {
            return;
        }
        if (!scanned) {
            scanned = true;
            // As a player starts one from the scan menu (F8): the request, then the chat as it starts and ends.
            ClientPacketDistributor.sendToServer(new CulpritNetwork.ScanRequest(SCAN_SECONDS));
            Culprit.LOGGER.info("Showcase: scanning for {} seconds", SCAN_SECONDS);
            return;
        }
        ReportData report = ClientReport.data();
        if (report == null) {
            return;
        }
        if (!framesStarted) {
            framesStarted = true;
            ClientReport.startFrameScan(FRAME_SECONDS);            // as the scan menu's Frames row does: over the first views
        }
        if (mc.gui.hud.isHidden() == hudWanted) {
            mc.gui.hud.toggle();                                   // no hand or hotbar in the pictures but those of the HUD's own
        }
        aim(mc);
        int t = ++shot;
        if (t % EACH != 0) {
            return;
        }
        int step = t / EACH;
        if (step > 1 && step - 2 < views.size()) {
            View v = views.get(step - 2);
            Screenshot.grab(mc.gameDirectory, "showcase_" + v.name() + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> Culprit.LOGGER.info("Showcase: {}", msg.getString()));
        }
        if (step - 1 < views.size()) {
            show(mc, server, report, views.get(step - 1));
            return;
        }
        if (step - 1 > views.size()) {
            Culprit.LOGGER.info("Showcase done");
            mc.stop();
        }
    }

    /** Sets up a view: the time, the camera, what is on screen. */
    private static void show(Minecraft mc, MinecraftServer server, ReportData report, View v) {
        if (v.time() != timeSet) {
            timeSet = v.time();
            server.execute(() -> run(server.overworld(), "time set " + v.time()));
        }
        CulpritClientConfig.LABELS.set(v.labels());
        hudWanted = v.screenTab() == CHAT || v.screenTab() == LOOK || v.screenTab() == HUD;
        CulpritClientConfig.HUD_LINE.set(v.screenTab() == HUD);
        Overlay.setShown(true);                                    // as the results screen's button does
        int shownSection = ClientReport.chunksShown() ? 2 : ClientReport.framesShown() ? 1 : 0;
        if (shownSection != v.section()) {
            ClientReport.showSection(v.section());                 // as the results screen's section buttons do
        }
        if (v.section() == 2 && ClientReport.chunksData() == null) {
            ClientReport.requestChunks();                          // a first look, as the section's opening does
        }
        if (ClientReport.spikesShown()) {
            ClientReport.showSpikes(false);
        }
        Vec3 eye = v.eye();
        float yaw = v.yaw(), pitch = v.pitch();
        aimEntity = -1;
        aimBlock = Long.MIN_VALUE;
        Overlay.markThing(-1);
        if (v.by() != NONE) {
            int i = find(mc, report, v.by());
            if (i < 0) {
                Culprit.LOGGER.warn("Showcase: nothing found for {}", v.name());
            } else {
                Vec3 at = Vec3.atBottomCenterOf(BlockPos.of(report.pos[i]));
                Entity e = report.entity[i] >= 0 ? mc.level.getEntity(report.entity[i]) : null;
                if (e != null) {
                    at = e.position();
                    aimEntity = e.getId();
                } else {
                    aimBlock = report.pos[i];
                }
                Vec3 target = e != null ? e.getBoundingBox().getCenter() : Vec3.atCenterOf(BlockPos.of(report.pos[i]));
                // A close look (the readout's) from a side with nothing between, the eye in the open and away from chunk
                // borders (a chunk's outline passing by the eye would cut across the picture); its x picks the first side
                // tried, so each such view looks from another. A marked thing is seen from the view's own offset.
                eye = v.by() == MARKED ? at.add(v.eye()) : clearSide(mc, at, target, (int) v.eye().x, e != null ? 3.2 : 3.0,
                        e != null ? 1.5 : 1.3, BlockPos.of(report.pos[i]));
                yaw = yaw(eye, target);
                pitch = pitch(eye, target);
                if (v.by() == MARKED) {
                    Overlay.markThing(i);                          // as a click in the results' list does
                    aimEntity = -1;
                    aimBlock = Long.MIN_VALUE;
                }
            }
        }
        if (v.screenTab() >= 0) {
            CulpritScreen.showTab(v.screenTab());
            if (!(mc.gui.screen() instanceof CulpritScreen)) {
                mc.gui.setScreen(new CulpritScreen());
            }
        } else if (v.screenTab() == SCAN_MENU) {
            mc.gui.setScreen(new ScanScreen());
        } else if (v.screenTab() == CHAT) {
            mc.gui.setScreen(new ChatScreen("", false));
        } else if (v.screenTab() == SETTINGS) {
            mc.gui.setScreen(CulpritScreen.settings(new CulpritScreen()));
        } else if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        Vec3 feet = eye.subtract(0, 1.62, 0);
        float y = yaw, p = pitch;
        server.execute(() -> server.getPlayerList().getPlayers().getFirst()
                .teleportTo(server.overworld(), feet.x, feet.y, feet.z, Set.of(), y, p, true));
    }

    /** The eye for a close look at a thing: the first of eight sides, from {@code first} on, that sees it clearly. */
    private static Vec3 clearSide(Minecraft mc, Vec3 at, Vec3 target, int first, double far, double up, BlockPos block) {
        Vec3 fallback = null;
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(((first + k) % 8) * 45);
            Vec3 eye = new Vec3(at.x - Math.sin(a) * far, at.y + up, at.z + Math.cos(a) * far);
            BlockPos eyeBlock = BlockPos.containing(eye);
            if (!mc.level.getBlockState(eyeBlock).isAir() || !mc.level.getBlockState(eyeBlock.below()).isAir()) {
                continue;
            }
            var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, target, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            boolean clear = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(block);
            if (!clear) {
                continue;
            }
            double cx = Math.floorMod((int) Math.floor(eye.x), 16) + (eye.x - Math.floor(eye.x));
            double cz = Math.floorMod((int) Math.floor(eye.z), 16) + (eye.z - Math.floor(eye.z));
            if (cx >= 2.5 && cx <= 13.5 && cz >= 2.5 && cz <= 13.5) {
                return eye;
            }
            if (fallback == null) {
                fallback = eye;
            }
        }
        return fallback != null ? fallback : at.add(0, up, far);
    }

    /** Keeps the crosshair on the thing a view looks at (a villager walks about). */
    private static void aim(Minecraft mc) {
        Vec3 target = null;
        if (aimEntity >= 0) {
            Entity e = mc.level.getEntity(aimEntity);
            target = e == null ? null : e.getBoundingBox().getCenter();
        } else if (aimBlock != Long.MIN_VALUE) {
            target = Vec3.atCenterOf(BlockPos.of(aimBlock));
        }
        if (target == null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        float yaw = yaw(eye, target), pitch = pitch(eye, target);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.setYHeadRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    /** The costliest thing of the kind a view is placed by, within the village. */
    private static int find(Minecraft mc, ReportData r, int by) {
        Vec3 c = center;
        for (int i = 0; i < r.things; i++) {
            BlockPos p = BlockPos.of(r.pos[i]);
            double dx = p.getX() + 0.5 - c.x, dz = p.getZ() + 0.5 - c.z;
            if (Kind.isChunkWide(r.kind[i]) || dx * dx + dz * dz > 56 * 56) {
                continue;
            }
            boolean ok = switch (by) {
                case VILLAGER -> {
                    Entity e = r.entity[i] >= 0 ? mc.level.getEntity(r.entity[i]) : null;
                    yield r.kind[i] == Kind.ENTITY && e != null && e.getType() == EntityTypes.VILLAGER;
                }
                case BLOCK_ENTITY -> r.kind[i] == Kind.BLOCK_ENTITY;
                default -> r.kind[i] == Kind.ENTITY && r.entity[i] >= 0 && mc.level.getEntity(r.entity[i]) != null;
            };
            if (ok) {
                return i;
            }
        }
        return -1;
    }

    /** On the server: the rules for steady pictures, then the village nearest the spawn, flown to. */
    private static void locate(ServerLevel level) {
        for (String c : List.of("gamerule advance_time false", "gamerule advance_weather false", "weather clear", "time set " + NOON,
                "gamerule command_block_output false")) {
            run(level, c);
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        // The kind of village asked for (plains by default: the scale's warm colours stand out on green, not on sand).
        var structures = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        var kind = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE,
                net.minecraft.resources.Identifier.withDefaultNamespace("village_" + VILLAGE));
        BlockPos village = VILLAGE.equals("any")
                ? level.findNearestMapStructure(StructureTags.VILLAGE, player.blockPosition(), 100, false)
                : level.findNearestMapStructure(net.minecraft.core.HolderSet.direct(structures.getOrThrow(kind)), player.blockPosition(), 100, false);
        if (village == null) {
            Culprit.LOGGER.error("Showcase: no village within 1,600 blocks of the spawn (seed {})", SEED);
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
            return;
        }
        Culprit.LOGGER.info("Showcase: seed {}, village at {} {}, {} blocks from the spawn", SEED, village.getX(), village.getZ(),
                (int) Math.sqrt(village.distSqr(player.blockPosition())));
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;                       // (else the camera falls between the views)
        player.onUpdateAbilities();
        player.teleportTo(level, village.getX() + 0.5, 180, village.getZ() + 0.5, Set.of(), 180f, 60f, true);
        center = new Vec3(village.getX() + 0.5, 0, village.getZ() + 0.5);
    }

    /** On the server, the village's chunks made: the ground round its middle, a chunk kept loaded beside it, the views. */
    private static void plan(ServerLevel level) {
        Vec3 c = center;
        int ground = groundNear(level, (int) Math.floor(c.x), (int) Math.floor(c.z));
        Culprit.LOGGER.info("Showcase: village ground at y {} in {}", ground,
                level.getBiome(BlockPos.containing(c.x, ground, c.z)).unwrapKey().map(Object::toString).orElse("?"));
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        // As a player keeps a farm's chunk loaded: one chunk beside the village, forced by the player's own /forceload.
        level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(),
                "forceload add " + (int) (c.x + 72) + " " + (int) c.z);

        Vec3 target = new Vec3(c.x, ground + 3, c.z);
        List<View> list = new ArrayList<>();
        String[] sides = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
        // The banner's: across the village toward the setting sun (west) and to either side of it, low, looking past the
        // middle so the village fills the picture's middle band and the near ground its foot; a few times of sunset;
        // then the same at noon from each side. Then close.
        for (long time : new long[] {12200, 12500, 12800}) {
            for (int degrees : new int[] {270, 315, 225}) {
                list.add(past(level, "banner_" + degrees + "_" + time, target, degrees, 24, 9, 14, 5, time));
            }
        }
        for (int degrees : new int[] {0, 90, 180, 270}) {
            list.add(past(level, "banner_" + degrees + "_noon", target, degrees, 24, 9, 14, 5, NOON));
        }
        for (int d = 0; d < 8; d += 2) {
            list.add(around(level, "close_" + sides[d], target, d * 45, 16, 7, NOON, NO_SCREEN, 0, 12));
        }
        list.add(new View("marked", new Vec3(-12, 7, 16), 0, 0, NOON, NO_SCREEN, 0, MARKED, 12));
        list.add(new View("marked_far", new Vec3(-20, 12, 24), 0, 0, NOON, NO_SCREEN, 0, MARKED, 12));
        for (int side = 0; side < 8; side += 2) {                  // (the eye's x: the first side tried)
            list.add(new View("looking_villager_" + side, new Vec3(side, 0, 0), 0, 0, NOON, LOOK, 0, VILLAGER, 12));
        }
        for (int side = 0; side < 8; side += 4) {
            list.add(new View("looking_block_" + side, new Vec3(side, 0, 0), 0, 0, NOON, LOOK, 0, BLOCK_ENTITY, 12));
        }
        View close = around(level, "screen", target, 0, 18, 9, NOON, 0, 0, 12);
        String[] tabs = {"tick", "things", "types", "mods", "chunks"};
        for (int tab = 0; tab < tabs.length; tab++) {
            list.add(new View("screen_" + tabs[tab], close.eye(), close.yaw(), close.pitch(), NOON, tab, 0, NONE, 12));
        }
        list.add(new View("frames_frame", close.eye(), close.yaw(), close.pitch(), NOON, 0, 1, NONE, 12));
        list.add(new View("frames_things", close.eye(), close.yaw(), close.pitch(), NOON, 1, 1, NONE, 12));
        list.add(new View("frames_mods", close.eye(), close.yaw(), close.pitch(), NOON, 3, 1, NONE, 12));
        list.add(around(level, "frames_world", target, 0, 18, 9, NOON, NO_SCREEN, 1, 12));
        list.add(around(level, "frames_wide", target, 315, 44, 26, NOON, NO_SCREEN, 1, 12));
        list.add(new View("chunks_screen", close.eye(), close.yaw(), close.pitch(), NOON, 0, 2, NONE, 12));
        list.add(around(level, "chunks_world", target.add(28, 0, 0), 0, 70, 70, NOON, NO_SCREEN, 2, 12));
        list.add(around(level, "chunks_world_low", target.add(30, 0, 0), 315, 48, 34, NOON, NO_SCREEN, 2, 12));
        list.add(new View("scan_menu", close.eye(), close.yaw(), close.pitch(), NOON, SCAN_MENU, 0, NONE, 12));
        list.add(new View("chat", close.eye(), close.yaw(), close.pitch(), NOON, CHAT, 0, NONE, 12));
        list.add(new View("hud_line", close.eye(), close.yaw(), close.pitch(), NOON, HUD, 0, NONE, 12));
        list.add(new View("settings", close.eye(), close.yaw(), close.pitch(), NOON, SETTINGS, 0, NONE, 12));
        // Last, at night (the boxes stand out in the dark; monsters may come out, so nothing is taken after these).
        for (int d = 0; d < 8; d += 2) {
            list.add(around(level, "night_" + sides[d], target, d * 45, 26, 12, NIGHT, NO_SCREEN, 0, 12));
        }
        views = list;
        Culprit.LOGGER.info("Showcase: {} views planned", list.size());
    }

    /**
     * A view of a target from one side (degrees: 0 from the south, 90 from the west, 180 from the north), so far off and
     * so high over the target's ground, kept clear of the land under it.
     */
    private static View around(ServerLevel level, String name, Vec3 target, int degrees, double far, double high, long time,
                               int screenTab, int section, int labels) {
        double a = Math.toRadians(degrees);
        double x = target.x - Math.sin(a) * far, z = target.z + Math.cos(a) * far;
        double y = Math.max(target.y - 3 + high, level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)) + 4);
        Vec3 eye = new Vec3(x, y, z);
        return new View(name, eye, yaw(eye, target), pitch(eye, target), time, screenTab, section, NONE, labels);
    }

    /**
     * A view from one side of a target (as {@link #around}), looking past it: at a point {@code beyond} blocks further
     * on and {@code lift} over its ground.
     */
    private static View past(ServerLevel level, String name, Vec3 target, int degrees, double far, double high, double beyond, double lift,
                             long time) {
        View from = around(level, name, target, degrees, far, high, time, NO_SCREEN, 0, 12);
        double a = Math.toRadians(degrees);
        Vec3 look = new Vec3(target.x + Math.sin(a) * beyond, target.y - 3 + lift, target.z - Math.cos(a) * beyond);
        return new View(name, from.eye(), yaw(from.eye(), look), pitch(from.eye(), look), time, NO_SCREEN, 0, NONE, 12);
    }

    /** The middle height of the ground round a place (roofs and trees taken as they come, the middle of them). */
    private static int groundNear(ServerLevel level, int x, int z) {
        List<Integer> heights = new ArrayList<>();
        for (int dx = -12; dx <= 12; dx += 6) {
            for (int dz = -12; dz <= 12; dz += 6) {
                heights.add(level.getHeight(Heightmap.Types.OCEAN_FLOOR, x + dx, z + dz));
            }
        }
        heights.sort(Integer::compare);
        return heights.get(heights.size() / 2);
    }

    private static float yaw(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    private static float pitch(Vec3 from, Vec3 to) {
        return (float) -Math.toDegrees(Math.atan2(to.y - from.y, Math.hypot(to.x - from.x, to.z - from.z)));
    }

    private static void run(ServerLevel level, String command) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), command);
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
