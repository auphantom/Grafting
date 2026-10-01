package io.github.auphantom.grafting.anchor;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * One end of a thread: the "concept" a player has tied the Thread of Grafting to.
 * <p>
 * The whole ability is built on combining exactly two anchors. Since an anchor is
 * either a place ({@link BlockAnchor}) or a being ({@link EntityAnchor}), there are
 * four ordered combinations, and each one produces a different graft.
 */
public sealed interface Anchor permits BlockAnchor, EntityAnchor {

    /** Where the anchor currently is (centre of the block / middle of the body). */
    Location center();

    /** Whether the anchored concept still exists in the world in its original form. */
    boolean isIntact();

    /** Human readable name, used in chat messages. */
    String describe();

    /** True if both anchors refer to the same block / the same entity. */
    boolean sameAs(Anchor other);

    default World world() {
        return center().getWorld();
    }
}
