package io.github.auphantom.grafting.graft;

import io.github.auphantom.grafting.anchor.Anchor;
import org.bukkit.Color;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.UUID;

/**
 * A live connection between two anchors that should never have been connected.
 * <p>
 * Subclasses only describe <em>what the connection means</em>. Lifetime, expiry,
 * validity checks and drawing the visible thread are handled by {@link GraftManager}.
 */
public abstract class Graft {

    private final int id;
    private final Mode mode;
    private final UUID owner;
    private final Anchor first;
    private final Anchor second;
    private final long expiresAt;
    private long createdAt;
    private boolean spent;

    protected Graft(int id, Mode mode, UUID owner, Anchor first, Anchor second, long expiresAt) {
        this.id = id;
        this.mode = mode;
        this.owner = owner;
        this.first = first;
        this.second = second;
        this.expiresAt = expiresAt;
    }

    /** Name shown to players. Defaults to the mode's name. */
    public String name() {
        return mode.display();
    }

    /** Key under {@code spirit.cost} / {@code spirit.upkeep} in the config. Defaults to the mode id. */
    public String costKey() {
        return mode.id();
    }

    /** One line explaining what this particular graft does, shown to the owner. */
    public abstract String summary();

    /** Colour of the thread drawn between the two ends. */
    public Color color() {
        return mode.color();
    }

    /** Whether the manager should draw the thread between the ends this tick. */
    public boolean drawsThread() {
        return true;
    }

    /** Called once, right after the graft is registered. */
    public void onStart() {
    }

    /** Called every server tick while the graft is alive and both ends are intact. */
    public void tick(long tick) {
    }

    /** Called once when the graft ends for any reason. */
    public void onEnd() {
    }

    /** Any damage dealt to any living entity while this graft lives (priority HIGH). */
    public void onDamage(EntityDamageEvent event) {
    }

    /** Same, but at priority HIGHEST, after every other graft had its say. */
    public void onLateDamage(EntityDamageEvent event) {
    }

    /** Any mob choosing a target while this graft lives. */
    public void onTarget(EntityTargetEvent event) {
    }

    /** One-shot grafts call this once their effect has been used up. */
    protected final void spend() {
        spent = true;
    }

    public final boolean isSpent() {
        return spent;
    }

    public final int id() {
        return id;
    }

    public final Mode mode() {
        return mode;
    }

    public final UUID owner() {
        return owner;
    }

    public final Anchor first() {
        return first;
    }

    public final Anchor second() {
        return second;
    }

    public final long expiresAt() {
        return expiresAt;
    }

    /** Tick the graft was registered on; set by the manager. */
    public final long createdAt() {
        return createdAt;
    }

    final void markCreated(long tick) {
        createdAt = tick;
    }

    /** Both ends still exist. Grafts that consume one of their ends on purpose override this. */
    public boolean isIntact() {
        return first.isIntact() && second.isIntact();
    }
}
