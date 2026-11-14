package dev.culprit.client;

import java.util.Optional;
import java.util.OptionalDouble;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.commands.RenderPass;

import dev.culprit.command.ReportText;
import dev.culprit.net.ReportData;
import dev.culprit.scan.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A scan's results drawn on the world (DESIGN §4.4): a box round each costly thing near the player, in the cost's
 * colour, fainter for cheap things; names and costs over the costliest close ones, never two over each other; each
 * costly chunk outlined on the ground with its total; and the thing picked in the Culprit screen marked by a beam and
 * a label seen through walls.
 * <p>
 * Drawn after the level, into the finished picture, depth tested against it, the way Ground Burst draws its effects:
 * a shader pack or Distant Horizons cannot draw over it or change its look. The boxes and outlines are gathered into
 * arrays each frame and drawn by two geometry renderers made once.
 */
public final class Overlay {
    /** How far from the camera things are boxed, and labelled; chunks within this many chunks get their outline. */
    private static final double RANGE = 64, LABEL_RANGE = 32;
    private static final int TILE_CHUNKS = 6;
    private static final int MAX_BOXES = 300, MAX_LABELS = 12, MAX_CHUNK_LABELS = 5, MAX_TILES = 169;
    /** Things cheaper than this a tick are not boxed (signs and other near-free tickers). */
    private static final long FLOOR_NS = 300;

    private static boolean shown = true;
    private static @Nullable ReportData report;
    private static int markedThing = -1, markedChunk = -1, markedDim = -1;
    private static @Nullable BlockPos markedPos;
    private static @Nullable String markedLabel;

    private static final SubmitNodeStorage STORAGE = new SubmitNodeStorage();
    private static @Nullable FeatureRenderDispatcher dispatcher;
    private static final PoseStack IDENTITY = new PoseStack();
    private static final PoseStack LABEL = new PoseStack();
    private static final Matrix4f FACING = new Matrix4f();

    // This frame's boxes, relative to the camera: corners and heat.
    private static final float[] BOX = new float[MAX_BOXES * 6];
    private static final float[] BOX_HEAT = new float[MAX_BOXES];
    private static int boxes;
    // This frame's chunk outlines: the four corners' x, y, z relative to the camera, and heat.
    private static final float[] TILE = new float[MAX_TILES * 12];
    private static final float[] TILE_HEAT = new float[MAX_TILES];
    private static int tiles;
    // The mark's beam: x, z (relative), y0, y1.
    private static boolean beam;
    private static float beamX, beamZ, beamY0, beamY1;
    // This frame's labels as seen from the camera (centre across and up, half width and height, over the distance),
    // to keep them apart; and the camera's right and up.
    private static final double[] PLACED = new double[(MAX_LABELS + MAX_CHUNK_LABELS + 1) * 4];
    private static int placed;
    private static final org.joml.Vector3f RIGHT = new org.joml.Vector3f(), UP = new org.joml.Vector3f();

    /** Per sent thing, its label lines, made on first use. */
    private static FormattedCharSequence @Nullable [] labelName, labelCost;

    private static final SubmitNodeCollector.CustomGeometryRenderer LINES = Overlay::lines;
    private static final SubmitNodeCollector.CustomGeometryRenderer FILLS = Overlay::fills;

    private Overlay() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Overlay::draw);
    }

    public static boolean shown() {
        return shown;
    }

    public static void setShown(boolean on) {
        shown = on;
    }

    static void newReport(@Nullable ReportData r) {
        report = r;
        markedThing = markedChunk = markedDim = -1;
        markedPos = null;
        markedLabel = null;
        labelName = r == null ? null : new FormattedCharSequence[r.things];
        labelCost = r == null ? null : new FormattedCharSequence[r.things];
        if (r != null) {
            shown = true;
        }
    }

    /** Marks a sent thing (from the screen's list). */
    public static void markThing(int index) {
        markedThing = index;
        markedChunk = -1;
        markedPos = null;
        shown = true;
    }

    /** Marks a chunk (from the screen's chunk list). */
    public static void markChunk(int index) {
        markedChunk = index;
        markedThing = -1;
        markedPos = null;
        shown = true;
    }

    /** Marks a place that is not one of the sent things, with its own label. */
    public static void markPlace(int dim, long pos, String label) {
        markedThing = markedChunk = -1;
        markedDim = dim;
        markedPos = BlockPos.of(pos);
        markedLabel = label;
        shown = true;
    }

    private static void draw(RenderLevelStageEvent.AfterLevel event) {
        ReportData r = report;
        Minecraft mc = Minecraft.getInstance();
        if (!shown || r == null || mc.level == null || mc.player == null) {
            return;
        }
        int dim = dimIndex(r, mc.level.dimension().identifier().toString());
        if (dim < 0) {
            return;
        }
        var camera = event.getLevelRenderState().cameraRenderState;
        Vec3 cam = camera.pos;
        gather(r, dim, mc.level, cam);
        if (boxes == 0 && tiles == 0 && !beam) {
            return;
        }
        STORAGE.order(0).afterTerrain.submit(new CustomFeatureRenderer.Submit(IDENTITY.last(), RenderTypes.debugQuads(), FILLS));
        STORAGE.order(0).afterTerrain.submit(new CustomFeatureRenderer.Submit(IDENTITY.last(), RenderTypes.linesTranslucent(), LINES));
        labels(r, dim, mc, cam, camera.orientation);
        render(mc, event);
    }

    /** This frame's boxes, chunk outlines and beam, costliest first, near the camera. */
    private static void gather(ReportData r, int dim, ClientLevel level, Vec3 cam) {
        boxes = 0;
        double range2 = RANGE * RANGE;
        for (int i = 0; i < r.things && boxes < MAX_BOXES; i++) {
            if (r.dim[i] != dim || Kind.isChunkWide(r.kind[i])) {
                continue;
            }
            long perTick = r.perTick(r.total[i]);
            if (perTick < FLOOR_NS) {
                break;                                  // the rest are cheaper still (the list is costliest first)
            }
            if (isViewer(r, i)) {
                continue;                               // the player's own box would surround the camera
            }
            AABB box = boxOf(r, i, level);
            double cx = (box.minX + box.maxX) / 2 - cam.x, cy = (box.minY + box.maxY) / 2 - cam.y, cz = (box.minZ + box.maxZ) / 2 - cam.z;
            if (cx * cx + cy * cy + cz * cz > range2 || box.contains(cam)) {
                continue;
            }
            int o = boxes * 6;
            BOX[o] = (float) (box.minX - cam.x);
            BOX[o + 1] = (float) (box.minY - cam.y);
            BOX[o + 2] = (float) (box.minZ - cam.z);
            BOX[o + 3] = (float) (box.maxX - cam.x);
            BOX[o + 4] = (float) (box.maxY - cam.y);
            BOX[o + 5] = (float) (box.maxZ - cam.z);
            BOX_HEAT[boxes++] = Heat.of(perTick);
        }

        // Chunks: outlined on the ground (each corner at the ground's height there), the costly ones only.
        tiles = 0;
        int camCx = Mth.floor(cam.x) >> 4, camCz = Mth.floor(cam.z) >> 4;
        long top = 0;
        for (int i = 0; i < r.chunks; i++) {
            if (r.chunkDim[i] == dim) {
                top = r.chunkTotal[i];
                break;
            }
        }
        long floor = Math.max(r.ticks * 5_000L, top / 50);   // 5 µs a tick, or a fiftieth of the costliest chunk
        for (int i = 0; i < r.chunks && tiles < MAX_TILES; i++) {
            if (r.chunkDim[i] != dim || r.chunkTotal[i] < floor
                    || Math.abs(r.chunkX[i] - camCx) > TILE_CHUNKS || Math.abs(r.chunkZ[i] - camCz) > TILE_CHUNKS) {
                continue;
            }
            int x0 = r.chunkX[i] << 4, z0 = r.chunkZ[i] << 4;
            int o = tiles * 12;
            corner(level, cam, x0, z0, o);
            corner(level, cam, x0 + 16, z0, o + 3);
            corner(level, cam, x0 + 16, z0 + 16, o + 6);
            corner(level, cam, x0, z0 + 16, o + 9);
            TILE_HEAT[tiles++] = Heat.of(r.perTick(r.chunkTotal[i]));
        }

        beam = false;
        BlockPos mark = null;
        if (markedThing >= 0 && markedThing < r.things && r.dim[markedThing] == dim) {
            AABB box = boxOf(r, markedThing, level);
            mark = BlockPos.containing((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2);
        } else if (markedChunk >= 0 && markedChunk < r.chunks && r.chunkDim[markedChunk] == dim) {
            int x = (r.chunkX[markedChunk] << 4) + 8, z = (r.chunkZ[markedChunk] << 4) + 8;
            mark = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        } else if (markedPos != null && markedDim == dim) {
            mark = markedPos;
        }
        if (mark != null) {
            beam = true;
            beamX = (float) (mark.getX() + 0.5 - cam.x);
            beamZ = (float) (mark.getZ() + 0.5 - cam.z);
            beamY0 = (float) (mark.getY() - cam.y);
            beamY1 = beamY0 + 48f;
        }
    }

    /** A chunk outline's corner, relative to the camera: on the ground's top there (the block inside the chunk). */
    private static void corner(ClientLevel level, Vec3 cam, int x, int z, int o) {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        TILE[o] = (float) (x - cam.x);
        TILE[o + 1] = (float) (ground + 0.03 - cam.y);
        TILE[o + 2] = (float) (z - cam.z);
    }

    /** Whether a sent thing is the player looking (their own entity is measured like any other). */
    private static boolean isViewer(ReportData r, int i) {
        Minecraft mc = Minecraft.getInstance();
        return r.entity[i] >= 0 && mc.player != null && r.entity[i] == mc.player.getId();
    }

    /** A thing's box: an entity's own, followed while the client has it; else its block. */
    private static AABB boxOf(ReportData r, int i, ClientLevel level) {
        if (r.entity[i] >= 0) {
            Entity e = level.getEntity(r.entity[i]);
            if (e != null && e.isAlive()) {
                return e.getBoundingBox().inflate(0.04);
            }
        }
        return new AABB(BlockPos.of(r.pos[i])).inflate(0.015);
    }

    private static void lines(PoseStack.Pose pose, VertexConsumer buffer) {
        for (int b = 0; b < boxes; b++) {
            int o = b * 6;
            float heat = BOX_HEAT[b];
            box(buffer, pose, BOX[o], BOX[o + 1], BOX[o + 2], BOX[o + 3], BOX[o + 4], BOX[o + 5], Heat.argb(heat, (int) (140 + 115 * heat)),
                    1.6f + 2.4f * heat);
        }
        for (int t = 0; t < tiles; t++) {
            int o = t * 12;
            float heat = TILE_HEAT[t];
            int c = Heat.argb(heat, (int) (150 + 100 * heat));
            float w = 2f + 2f * heat;
            for (int k = 0; k < 4; k++) {
                int a = o + k * 3, b = o + ((k + 1) % 4) * 3;
                line(buffer, pose, TILE[a], TILE[a + 1], TILE[a + 2], TILE[b], TILE[b + 1], TILE[b + 2], c, w);
            }
        }
        if (beam) {
            box(buffer, pose, beamX - 0.15f, beamY0, beamZ - 0.15f, beamX + 0.15f, beamY1, beamZ + 0.15f, 0xFF5CE1E6, 3f);
        }
    }

    private static void fills(PoseStack.Pose pose, VertexConsumer buffer) {
        for (int b = 0; b < boxes; b++) {
            int o = b * 6;
            float heat = BOX_HEAT[b];
            cube(buffer, pose, BOX[o], BOX[o + 1], BOX[o + 2], BOX[o + 3], BOX[o + 4], BOX[o + 5], Heat.argb(heat, (int) (10 + 60 * heat)));
        }
        for (int t = 0; t < tiles; t++) {
            int o = t * 12;
            int c = Heat.argb(TILE_HEAT[t], (int) (20 + 50 * TILE_HEAT[t]));
            quad(buffer, pose, TILE[o], TILE[o + 1], TILE[o + 2], TILE[o + 3], TILE[o + 4], TILE[o + 5], TILE[o + 6], TILE[o + 7], TILE[o + 8],
                    TILE[o + 9], TILE[o + 10], TILE[o + 11], c);
        }
        if (beam) {
            cube(buffer, pose, beamX - 0.15f, beamY0, beamZ - 0.15f, beamX + 0.15f, beamY1, beamZ + 0.15f, 0x705CE1E6);
        }
    }

    /** The mark's label first, seen through walls; then the costliest close things' and chunks', never overlapping. */
    private static void labels(ReportData r, int dim, Minecraft mc, Vec3 cam, Quaternionf facing) {
        Font font = mc.font;
        ClientLevel level = mc.level;
        placed = 0;
        facing.transform(RIGHT.set(1, 0, 0));
        facing.transform(UP.set(0, 1, 0));
        if (markedThing >= 0 && markedThing < r.things && r.dim[markedThing] == dim) {
            AABB box = boxOf(r, markedThing, level);
            label(font, cam, facing, (box.minX + box.maxX) / 2, box.maxY + 0.7, (box.minZ + box.maxZ) / 2, name(r, markedThing),
                    cost(r, markedThing), Font.DisplayMode.SEE_THROUGH, true);
        } else if (markedChunk >= 0 && markedChunk < r.chunks && r.chunkDim[markedChunk] == dim) {
            int x = (r.chunkX[markedChunk] << 4) + 8, z = (r.chunkZ[markedChunk] << 4) + 8;
            label(font, cam, facing, x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 3.0, z, chunkName(r, markedChunk),
                    chunkCost(r, markedChunk), Font.DisplayMode.SEE_THROUGH, true);
        } else if (markedPos != null && markedDim == dim && markedLabel != null) {
            label(font, cam, facing, markedPos.getX() + 0.5, markedPos.getY() + 1.7, markedPos.getZ() + 0.5,
                    Component.literal(markedLabel).getVisualOrderText(), null, Font.DisplayMode.SEE_THROUGH, true);
        }
        int things = 0;
        double range2 = LABEL_RANGE * LABEL_RANGE;
        for (int i = 0; i < r.things && things < MAX_LABELS; i++) {
            if (r.dim[i] != dim || Kind.isChunkWide(r.kind[i]) || i == markedThing || isViewer(r, i)) {
                continue;
            }
            if (r.perTick(r.total[i]) < FLOOR_NS) {
                break;
            }
            AABB box = boxOf(r, i, level);
            double x = (box.minX + box.maxX) / 2, y = box.maxY + 0.45, z = (box.minZ + box.maxZ) / 2;
            double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
            if (dx * dx + dy * dy + dz * dz > range2) {
                continue;
            }
            if (label(font, cam, facing, x, y, z, name(r, i), cost(r, i), Font.DisplayMode.NORMAL, false)) {
                things++;
            }
        }
        int chunks = 0;
        int camCx = Mth.floor(cam.x) >> 4, camCz = Mth.floor(cam.z) >> 4;
        for (int i = 0; i < r.chunks && chunks < MAX_CHUNK_LABELS; i++) {
            if (r.chunkDim[i] != dim || i == markedChunk || Math.abs(r.chunkX[i] - camCx) > TILE_CHUNKS || Math.abs(r.chunkZ[i] - camCz) > TILE_CHUNKS) {
                continue;
            }
            int x = (r.chunkX[i] << 4) + 8, z = (r.chunkZ[i] << 4) + 8;
            double hx = x - cam.x, hz = z - cam.z;
            if (hx * hx + hz * hz < 14 * 14) {
                continue;                               // standing in or by the chunk, its things' labels say more
            }
            if (label(font, cam, facing, x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 2.5, z, chunkName(r, i), chunkCost(r, i),
                    Font.DisplayMode.NORMAL, false)) {
                chunks++;
            }
        }
    }

    private static FormattedCharSequence name(ReportData r, int i) {
        FormattedCharSequence[] cache = labelName;
        if (cache[i] == null) {
            String shortName = Names.of(r.names[r.type[i]], r.kind[i]);
            Component text = Component.literal(shortName);
            if (r.kind[i] != Kind.BLOCK_ENTITY && r.kind[i] != Kind.ENTITY) {
                text = Component.literal(shortName + " (").append(Component.translatableWithFallback("culprit.kind.one." + Kind.key(r.kind[i]),
                        Kind.englishOne(r.kind[i]))).append(")");
            }
            cache[i] = text.getVisualOrderText();
        }
        return cache[i];
    }

    private static FormattedCharSequence cost(ReportData r, int i) {
        FormattedCharSequence[] cache = labelCost;
        if (cache[i] == null) {
            long perTick = r.perTick(r.total[i]);
            cache[i] = Component.literal(ReportText.time(perTick) + "/t").withColor(Heat.rgb(Heat.of(perTick)))
                    .append(Component.literal("  " + ReportText.share(r.total[i], r.tickNsTotal)).withColor(0xC8C8C8)).getVisualOrderText();
        }
        return cache[i];
    }

    private static FormattedCharSequence chunkName(ReportData r, int i) {
        return Component.translatableWithFallback("culprit.overlay.chunk", "chunk %s, %s", r.chunkX[i], r.chunkZ[i]).getVisualOrderText();
    }

    private static FormattedCharSequence chunkCost(ReportData r, int i) {
        long perTick = r.perTick(r.chunkTotal[i]);
        return Component.literal(ReportText.time(perTick) + "/t").withColor(Heat.rgb(Heat.of(perTick)))
                .append(Component.literal("  " + ReportText.share(r.chunkTotal[i], r.tickNsTotal)).withColor(0xC8C8C8)).getVisualOrderText();
    }

    /** A label facing the camera at (x, y, z), unless another is already too close to it seen from the camera. */
    private static boolean label(Font font, Vec3 cam, Quaternionf facing, double x, double y, double z, FormattedCharSequence first,
                                 @Nullable FormattedCharSequence second, Font.DisplayMode mode, boolean always) {
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.5) {
            return false;
        }
        float scale = 0.025f * (float) Math.max(1.0, len / 16.0);   // far labels kept readable
        // Where it is seen, and how big: across and up over the distance, as its rectangle's half sizes.
        double sx = (dx * RIGHT.x + dy * RIGHT.y + dz * RIGHT.z) / len, sy = (dx * UP.x + dy * UP.y + dz * UP.z) / len;
        double hw = (Math.max(font.width(first), second == null ? 0 : font.width(second)) / 2.0 + 3) * scale / len;
        double hh = (second == null ? 6 : 11) * scale / len;
        if (!always) {
            for (int p = 0; p < placed; p++) {
                int o = p * 4;
                if (Math.abs(sx - PLACED[o]) < hw + PLACED[o + 2] && Math.abs(sy - PLACED[o + 1]) < hh + PLACED[o + 3]) {
                    return false;
                }
            }
        }
        if (placed * 4 + 3 < PLACED.length) {
            int o = placed * 4;
            PLACED[o] = sx;
            PLACED[o + 1] = sy;
            PLACED[o + 2] = hw;
            PLACED[o + 3] = hh;
            placed++;
        }
        LABEL.pushPose();
        LABEL.translate((float) dx, (float) dy, (float) dz);
        LABEL.mulPose(FACING.rotation(facing));
        LABEL.scale(scale, -scale, scale);
        int bg = 0x99101216;
        STORAGE.order(0).submitText(LABEL, -font.width(first) / 2f, second == null ? 0 : -10, first, false, mode, LightCoordsUtil.FULL_BRIGHT,
                0xFFFFFFFF, bg, 0);
        if (second != null) {
            STORAGE.order(0).submitText(LABEL, -font.width(second) / 2f, 0, second, false, mode, LightCoordsUtil.FULL_BRIGHT, 0xFFFFFFFF, bg, 0);
        }
        LABEL.popPose();
        return true;
    }

    /** Draws what was submitted into the finished picture (Ground Burst's way, DESIGN §6). */
    private static void render(Minecraft mc, RenderLevelStageEvent.AfterLevel event) {
        if (dispatcher == null) {
            dispatcher = new FeatureRenderDispatcher(mc.gameRenderer.renderBuffers(), mc.getModelManager(), mc.getAtlasManager(), mc.font,
                    mc.gameRenderer.gameRenderState());
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(event.getModelViewMatrix());
        try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(STORAGE)) {
            if (!frame.isEmpty()) {
                RenderTarget main = mc.gameRenderer.mainRenderTarget();
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Culprit overlay",
                        main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    FeatureRenderDispatcher.renderAllFeatures(pass, frame);
                }
            }
        } finally {
            modelView.popMatrix();
        }
    }

    private static int dimIndex(ReportData r, String dim) {
        for (int i = 0; i < r.dims.length; i++) {
            if (r.dims[i].equals(dim)) {
                return i;
            }
        }
        return -1;
    }

    // --- geometry ---------------------------------------------------------------------------------------------------

    private static void box(VertexConsumer b, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int c, float w) {
        line(b, p, x0, y0, z0, x1, y0, z0, c, w);
        line(b, p, x1, y0, z0, x1, y0, z1, c, w);
        line(b, p, x1, y0, z1, x0, y0, z1, c, w);
        line(b, p, x0, y0, z1, x0, y0, z0, c, w);
        line(b, p, x0, y1, z0, x1, y1, z0, c, w);
        line(b, p, x1, y1, z0, x1, y1, z1, c, w);
        line(b, p, x1, y1, z1, x0, y1, z1, c, w);
        line(b, p, x0, y1, z1, x0, y1, z0, c, w);
        line(b, p, x0, y0, z0, x0, y1, z0, c, w);
        line(b, p, x1, y0, z0, x1, y1, z0, c, w);
        line(b, p, x1, y0, z1, x1, y1, z1, c, w);
        line(b, p, x0, y0, z1, x0, y1, z1, c, w);
    }

    private static void line(VertexConsumer b, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int c, float w) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        b.addVertex(p, x0, y0, z0).setColor(c).setNormal(p, dx, dy, dz).setLineWidth(w);
        b.addVertex(p, x1, y1, z1).setColor(c).setNormal(p, dx, dy, dz).setLineWidth(w);
    }

    private static void cube(VertexConsumer b, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        quad(b, p, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, c);
        quad(b, p, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, c);
        quad(b, p, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, c);
        quad(b, p, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, c);
        quad(b, p, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, c);
        quad(b, p, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, c);
    }

    private static void quad(VertexConsumer b, PoseStack.Pose p, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int c) {
        b.addVertex(p, ax, ay, az).setColor(c);
        b.addVertex(p, bx, by, bz).setColor(c);
        b.addVertex(p, cx, cy, cz).setColor(c);
        b.addVertex(p, dx, dy, dz).setColor(c);
    }
}
