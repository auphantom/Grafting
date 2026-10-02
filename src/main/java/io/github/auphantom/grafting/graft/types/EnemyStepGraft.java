package io.github.auphantom.grafting.graft.types;

import io.github.auphantom.grafting.anchor.BlockAnchor;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.Mode;
import io.github.auphantom.grafting.util.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

/**
 * <b>Distance level 3, Being + Place: Enemy Step.</b>
 * <p>
 * The distance of a nearby being's next step is grafted onto the distance to a chosen place:
 * the moment it moves to another block, it is standing on the destination instead.
 */
public final class EnemyStepGraft extends Graft {

    private final EntityAnchor being;
    private final BlockAnchor destination;
    private Block lastBlock;

    public EnemyStepGraft(int id, UUID owner, EntityAnchor being, BlockAnchor destination, long expiresAt) {
        super(id, Mode.DISTANCE, owner, being, destination, expiresAt);
        this.being = being;
        this.destination = destination;
    }

    @Override
    public String name() {
        return "Enemy Step";
    }

    @Override
    public String costKey() {
        return "enemy";
    }

    @Override
    public String summary() {
        return "The next step " + being.describe() + " takes will land on " + destination.describe() + ".";
    }

    @Override
    public void onStart() {
        lastBlock = being.entity().getLocation().getBlock();
    }

    @Override
    public void tick(long tick) {
        LivingEntity e = being.entity();
        Block now = e.getLocation().getBlock();
        if (tick % 4 == 0) {
            Location feet = e.getLocation().add(0, 0.05, 0);
            Fx.ring(feet, 0.6, Particle.DUST, Fx.dust(color(), 0.7f), 14);
            Location dest = destination.standingSpot().add(0, 0.05, 0);
            Fx.ring(dest, 0.6, Particle.DUST, Fx.dust(Color.WHITE, 0.6f), 14);
        }
        if (now.getX() == lastBlock.getX() && now.getZ() == lastBlock.getZ()) return;
        lastBlock = now;
        Location to = destination.standingSpot();
        Location from = e.getLocation();
        to.setYaw(from.getYaw());
        to.setPitch(from.getPitch());
        if (e.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            e.setFallDistance(0);
            from.getWorld().spawnParticle(Particle.REVERSE_PORTAL, from.add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0.05);
            to.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 60, 0.3, 0.8, 0.3, 0.6);
            to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
            spend();
        }
    }
}
