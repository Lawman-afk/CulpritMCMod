package dev.culprit.report;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import dev.culprit.scan.Kind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;

/**
 * What a measured type is called on screen: the game's own name for it in the player's language (an entity's, a
 * block's, a block entity's block, a fluid's), found in the registries by the type's id and its kind. A type this client
 * does not know (a mod only the server has) is named from its id. Kept until the next report, so a language changed in
 * between shows with it.
 */
public final class Names {
    private static final Map<String, String> CACHE = new HashMap<>();

    private Names() {}

    public static void clear() {
        CACHE.clear();
    }

    /** A type's name; {@code kind} ({@link Kind}) picks where to look, -1 when it is not known. */
    public static String of(String type, int kind) {
        String key = kind + "|" + type;
        String name = CACHE.get(key);
        if (name == null) {
            name = find(type, kind);
            CACHE.put(key, name);
        }
        return name;
    }

    /** A mod's name as it calls itself, when this client has the mod (else its id). */
    public static String mod(String id) {
        String key = "mod|" + id;
        String name = CACHE.get(key);
        if (name == null) {
            name = net.minecraftforge.fml.ModList.get().getModContainerById(id).map(c -> c.getModInfo().getDisplayName()).orElse(id);
            CACHE.put(key, name);
        }
        return name;
    }

    private static String find(String type, int kind) {
        if (type.equals("minecraft:chunk_upkeep")) {
            return capital(Component.translatableWithFallback("culprit.kind.one.chunk", "chunk upkeep").getString());
        }
        if (type.equals("minecraft:mob_spawning")) {
            return capital(Component.translatableWithFallback("culprit.kind.one.spawning", "mob spawning").getString());
        }
        ResourceLocation id = ResourceLocation.tryParse(type);
        if (id != null) {
            if ((kind == Kind.ENTITY || kind < 0) && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                return BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString();
            }
            if (kind == Kind.BLOCK_ENTITY && BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) {
                // Its own block's name when it has one by the same id (a hopper's), else the first block it sits in.
                BlockEntityType<?> blockEntity = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id);
                if (!BuiltInRegistries.BLOCK.containsKey(id) && blockEntity != null && !((dev.culprit.legacy.mixin.BlockEntityTypeAccessor) blockEntity).culprit$validBlocks().isEmpty()) {
                    return ((dev.culprit.legacy.mixin.BlockEntityTypeAccessor) blockEntity).culprit$validBlocks().stream().min(java.util.Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString()))
                            .map(b -> b.getName().getString()).orElse(pretty(type));
                }
            }
            if (kind != Kind.ENTITY && BuiltInRegistries.BLOCK.containsKey(id)) {
                Block block = BuiltInRegistries.BLOCK.get(id);
                if (block != null) {
                    return block.getName().getString();
                }
            }
            if (kind != Kind.ENTITY && BuiltInRegistries.FLUID.containsKey(id)) {
                Fluid fluid = BuiltInRegistries.FLUID.get(id);
                if (fluid != null) {
                    return fluid.getFluidType().getDescription().getString();
                }
            }
        }
        return pretty(type);
    }

    /** "somemod:big_machine" as "Big machine". */
    private static String pretty(String type) {
        int colon = type.indexOf(':');
        String path = colon < 0 ? type : type.substring(colon + 1);
        return capital(path.replace('_', ' ').replace('/', ' ').toLowerCase(Locale.ROOT));
    }

    static String capital(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
