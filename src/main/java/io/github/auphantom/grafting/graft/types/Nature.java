package io.github.auphantom.grafting.graft.types;

import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.Set;

/**
 * The "nature" a block lends to a being in a {@link NatureGraft}. Each block family
 * maps to one nature, so the effect is predictable once you have seen it once:
 * a slime block is bouncy, ice is cold, TNT is volatile, and so on.
 */
public enum Nature {
    BOUNCE("Bounce", "falls turn into bounces"),
    FLAME("Flame", "immune to fire, ignites whatever strikes it"),
    FROST("Frost", "freezes water underfoot and the flesh of whatever it strikes"),
    STONE("Stone", "takes far less damage, but moves like a statue"),
    FEATHER("Feather", "drifts instead of falling, and runs light-footed"),
    WATER("Water", "breathes and swims like a fish"),
    LIGHT("Light", "glows, sees in the dark, and burns the undead nearby"),
    STICKY("Stickiness", "can barely move"),
    LIFE("Life", "slowly regrows its wounds"),
    VOLATILE("Volatility", "explodes the next time it is struck");

    private static final Set<Material> BOUNCY = Set.of(Material.SLIME_BLOCK);
    private static final Set<Material> FIERY = Set.of(Material.MAGMA_BLOCK, Material.NETHERRACK,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FIRE, Material.SOUL_FIRE, Material.FURNACE,
            Material.BLAST_FURNACE, Material.SMOKER, Material.LAVA_CAULDRON);
    private static final Set<Material> COLD = Set.of(Material.SNOW_BLOCK, Material.POWDER_SNOW, Material.SNOW);
    private static final Set<Material> WATERY = Set.of(Material.PRISMARINE, Material.PRISMARINE_BRICKS,
            Material.DARK_PRISMARINE, Material.SPONGE, Material.WET_SPONGE, Material.WATER_CAULDRON,
            Material.KELP_PLANT, Material.DRIED_KELP_BLOCK, Material.CONDUIT, Material.SEAGRASS);
    private static final Set<Material> LUMINOUS = Set.of(Material.GLOWSTONE, Material.SEA_LANTERN,
            Material.SHROOMLIGHT, Material.LANTERN, Material.SOUL_LANTERN, Material.TORCH, Material.WALL_TORCH,
            Material.SOUL_TORCH, Material.SOUL_WALL_TORCH, Material.REDSTONE_LAMP, Material.BEACON,
            Material.JACK_O_LANTERN, Material.OCHRE_FROGLIGHT, Material.VERDANT_FROGLIGHT,
            Material.PEARLESCENT_FROGLIGHT, Material.END_ROD, Material.GLOW_LICHEN);
    private static final Set<Material> STICKY_BLOCKS = Set.of(Material.COBWEB, Material.HONEY_BLOCK,
            Material.SOUL_SAND, Material.SOUL_SOIL, Material.MUD);
    private static final Set<Material> FEATHERY = Set.of(Material.HAY_BLOCK, Material.SCAFFOLDING,
            Material.BAMBOO_BLOCK);
    private static final Set<Material> LIVING = Set.of(Material.MOSS_BLOCK, Material.MOSS_CARPET,
            Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN,
            Material.SWEET_BERRY_BUSH, Material.CACTUS, Material.SUGAR_CANE, Material.BEEHIVE, Material.BEE_NEST);

    private final String display;
    private final String description;

    Nature(String display, String description) {
        this.display = display;
        this.description = description;
    }

    public String display() {
        return display;
    }

    public String description() {
        return description;
    }

    /** Reads the nature of a block, or {@code null} if it has nothing worth grafting. */
    public static Nature of(Material m) {
        if (m == Material.TNT) return VOLATILE;
        if (BOUNCY.contains(m)) return BOUNCE;
        if (STICKY_BLOCKS.contains(m)) return STICKY;
        if (FIERY.contains(m)) return FLAME;
        if (Tag.ICE.isTagged(m) || COLD.contains(m)) return FROST;
        if (WATERY.contains(m)) return WATER;
        if (LUMINOUS.contains(m) || Tag.CANDLES.isTagged(m)) return LIGHT;
        if (Tag.LEAVES.isTagged(m) || Tag.WOOL.isTagged(m) || Tag.WOOL_CARPETS.isTagged(m) || FEATHERY.contains(m)) {
            return FEATHER;
        }
        if (Tag.FLOWERS.isTagged(m) || Tag.SAPLINGS.isTagged(m) || Tag.CROPS.isTagged(m) || LIVING.contains(m)) {
            return LIFE;
        }
        // Anything else that is a full, solid block (stone, ores, wood, metal...) lends its solidity.
        if (m.isBlock() && m.isSolid() && m.isOccluding()) return STONE;
        return null;
    }
}
