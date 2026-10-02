package io.github.auphantom.grafting.graft.types;

import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;

import io.github.auphantom.grafting.util.Fx;
import io.github.auphantom.grafting.anchor.BlockAnchor;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * <b>Being + Place: Gravity.</b>
 * <p>
 * The being's sense of "down" is grafted onto the block. It falls toward the block
 * from any direction (sideways, upward, across a chasm) and never takes fall damage
 * while the thread holds, because it never falls the way gravity meant it to.
 */
public final class GravityGraft extends Graft {

    /** Acceleration toward the block, blocks/tick^2 (vanilla gravity is 0.08). */
    private static final double PULL = 0.08;
    private static final double DRAG = 0.96;
    private static final double MAX_SPEED = 1.2;

    private final EntityAnchor being;
    private final BlockAnchor well;
    /**
     * The graft keeps its own idea of the being's fall speed. Reading it back from the server
     * does not work for players (their physics run on the client), so the pull would stack up
     * every tick into a violent fling.
     */
    private Vector fall = new Vector();

    public GravityGraft(int id, UUID owner, EntityAnchor being, BlockAnchor well, long expiresAt) {
        super(id, Mode.GRAVITY, owner, being, well, expiresAt);
        this.being = being;
        this.well = well;
    }

    @Override
    public String summary() {
        return "For " + being.describe() + ", 'down' now points at " + well.describe() + ".";
    }

    @Override
    public void tick(long tick) {
        LivingEntity entity = being.entity();
        if (!entity.getWorld().equals(well.world())) return;
        Location center = well.center();
        Vector toWell = center.toVector().subtract(entity.getLocation().add(0, entity.getHeight() / 2, 0).toVector());
        double distance = toWell.length();
        if (distance < 1.3) {
            // Resting against the block: hold still there, like standing on the ground.
            fall = new Vector();
        } else {
            fall.multiply(DRAG).add(toWell.normalize().multiply(PULL));
            if (fall.length() > MAX_SPEED) fall.normalize().multiply(MAX_SPEED);
            // Never overshoot the block in a single tick.
            if (fall.length() > distance - 1.0) fall.normalize().multiply(Math.max(0.05, distance - 1.0));
        }
        entity.setVelocity(fall);
        entity.setFallDistance(0);

        if (tick % 2 == 0) {
            // A swirling vortex around the gravity well.
            double phase = tick * 0.25;
            for (int i = 0; i < 3; i++) {
                double a = phase + i * (2 * Math.PI / 3);
                double r = 1.2 + 0.3 * Math.sin(phase * 0.7 + i);
                Location at = center.clone().add(Math.cos(a) * r, Math.sin(phase + i) * 0.6, Math.sin(a) * r);
                Fx.spawn(at.getWorld(), Particle.DUST, at, Fx.scaled(2), 0.02, 0.02, 0.02, 0, Fx.dust(color(), 1f));
            }
            Fx.spawn(center.getWorld(), Particle.REVERSE_PORTAL, center, Fx.scaled(4), 0.6, 0.6, 0.6, 0.02, null);
        }
    }

    @Override
    public void onDamage(EntityDamageEvent event) {
        if (being.is(event.getEntity()) && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }
}
