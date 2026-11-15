package dev.culprit.chunks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.UUID;

import dev.culprit.Culprit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

/**
 * The words for what keeps a chunk loaded (DESIGN §4.3): a mod by its name, the block or entity behind its chunk
 * loader, and an owner known only by id (a chunk loader a player carries, a team's claimed chunks) by the player or
 * team it is, as far as the server knows them.
 */
final class OwnerNames {
    private OwnerNames() {}

    /** A mod's name as it gives it ("FTB Chunks"), or its id when it is not installed. */
    static String mod(String id) {
        return ModList.get().getModContainerById(id).map(c -> c.getModInfo().getDisplayName()).orElse(id);
    }

    /** A mod's own kind of loading ticket: "Create: contraption". */
    static String ticketType(ResourceLocation id) {
        return mod(id.getNamespace()) + ": " + id.getPath().replace('_', ' ');
    }

    static String block(ServerLevel level, BlockPos pos) {
        String block = level.isLoaded(pos) ? level.getBlockState(pos).getBlock().getName().getString() : "a block";
        return block + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    static String entity(Entity entity) {
        if (entity instanceof Player player) {
            return player.getName().getString();
        }
        BlockPos at = entity.blockPosition();
        return entity.getName().getString() + " at " + at.getX() + " " + at.getY() + " " + at.getZ();
    }

    /**
     * An owner known by id: an entity in the level, a player online, a team of FTB Teams (FTB Chunks keeps a team's
     * force-loaded claims under the team's id), a player the server has seen, or else the id's first eight digits.
     */
    static String uuid(MinecraftServer server, ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        if (entity != null) {
            return entity(entity);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            return player.getName().getString();
        }
        String team = FtbTeams.name(id);
        if (team != null) {
            return "team " + team;
        }
        var cache = server.getProfileCache();
        var known = cache == null ? java.util.Optional.<com.mojang.authlib.GameProfile>empty() : cache.get(id);
        return known.isPresent() ? known.get().getName() : "owner " + id.toString().substring(0, 8);
    }

    /**
     * FTB Teams' name for a team, read through its public API when it is installed (looked up by name, so nothing
     * here needs it): {@code FTBTeamsAPI.api().getManager().getTeamByID(id)}, the same on every version it has.
     */
    private static final class FtbTeams {
        private static boolean tried;
        private static @Nullable MethodHandle api;
        private static @Nullable MethodHandle managerLoaded;
        private static @Nullable MethodHandle manager;
        private static @Nullable MethodHandle byId;
        private static @Nullable MethodHandle name;

        static @Nullable String name(UUID id) {
            if (!tried) {
                tried = true;
                find();
            }
            if (name == null) {
                return null;
            }
            try {
                Object teams = api.invoke();
                if (!(boolean) managerLoaded.invoke(teams)) {
                    return null;
                }
                Optional<?> team = (Optional<?>) byId.invoke(manager.invoke(teams), id);
                return team.isPresent() ? ((Component) name.invoke(team.get())).getString() : null;
            } catch (Throwable e) {
                Culprit.LOGGER.debug("Culprit: FTB Teams did not name a team", e);
                name = null;                                    // (not asked again)
                return null;
            }
        }

        private static void find() {
            if (!ModList.get().isLoaded("ftbteams")) {
                return;
            }
            try {
                ClassLoader loader = OwnerNames.class.getClassLoader();
                Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI", false, loader);
                Class<?> apiType = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API", false, loader);
                Class<?> managerType = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager", false, loader);
                Class<?> teamType = Class.forName("dev.ftb.mods.ftbteams.api.Team", false, loader);
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                api = lookup.findStatic(apiClass, "api", MethodType.methodType(apiType));
                managerLoaded = lookup.findVirtual(apiType, "isManagerLoaded", MethodType.methodType(boolean.class));
                manager = lookup.findVirtual(apiType, "getManager", MethodType.methodType(managerType));
                byId = lookup.findVirtual(managerType, "getTeamByID", MethodType.methodType(Optional.class, UUID.class));
                name = lookup.findVirtual(teamType, "getName", MethodType.methodType(Component.class));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                Culprit.LOGGER.debug("Culprit: FTB Teams' API was not found", e);
                name = null;
            }
        }
    }
}
