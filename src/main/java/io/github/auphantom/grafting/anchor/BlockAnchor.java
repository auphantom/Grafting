package io.github.auphantom.grafting.anchor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/**
 * A place. Remembers the material it was tied to, so breaking or replacing the
 * block cuts the thread.
 */
public record BlockAnchor(Block block, Material material) implements Anchor {

    public static BlockAnchor of(Block block) {
        return new BlockAnchor(block, block.getType());
    }

    @Override
    public Location center() {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }

    /**
     * The spot an entity would stand on when it is "on" this block. Works for full
     * blocks, slabs, carpets and passable blocks such as pressure plates.
     */
    public Location standingSpot() {
        BoundingBox box = block.getBoundingBox();
        double y = box.getVolume() > 0 ? box.getMaxY() : block.getY();
        return new Location(block.getWorld(), block.getX() + 0.5, y, block.getZ() + 0.5);
    }

    public boolean isLoaded() {
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    /**
     * Intact while the block keeps its material. A block in an unloaded chunk is
     * assumed intact (we refuse to load chunks just to check on a thread).
     */
    @Override
    public boolean isIntact() {
        return !isLoaded() || block.getType() == material;
    }

    @Override
    public String describe() {
        return pretty(material.name()) + " at " + block.getX() + ", " + block.getY() + ", " + block.getZ();
    }

    @Override
    public boolean sameAs(Anchor other) {
        return other instanceof BlockAnchor b && b.block.equals(block);
    }

    static String pretty(String enumName) {
        String lower = enumName.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
