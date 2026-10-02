package io.github.auphantom.grafting.graft.types;

import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;

import io.github.auphantom.grafting.util.Fx;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * <b>Being + Being: Puppetry.</b>
 * <p>
 * The first being's movements are grafted onto the second. Whatever the puppeteer does
 * (step, turn, jump) the puppet does at the same moment, keeping the same offset.
 * A nod to the Fool pathway's marionettes.
 */
public final class PuppetGraft extends Graft {

    private final EntityAnchor master;
    private final EntityAnchor puppet;
    private Location lastMaster;

    public PuppetGraft(int id, UUID owner, EntityAnchor master, EntityAnchor puppet, long expiresAt) {
        super(id, Mode.PUPPET, owner, master, puppet, expiresAt);
        this.master = master;
        this.puppet = puppet;
    }

    @Override
    public String summary() {
        return puppet.describe() + " now moves exactly as " + master.describe() + " moves.";
    }

    @Override
    public void onStart() {
        lastMaster = master.entity().getLocation();
        if (puppet.entity() instanceof Mob mob) mob.setAware(false);
    }

    @Override
    public void tick(long tick) {
        LivingEntity m = master.entity();
        LivingEntity p = puppet.entity();
        Location now = m.getLocation();
        if (!now.getWorld().equals(p.getWorld()) || !now.getWorld().equals(lastMaster.getWorld())) {
            lastMaster = now;
            return;
        }
        Vector delta = now.toVector().subtract(lastMaster.toVector());
        lastMaster = now;

        Vector velocity = delta.clone();
        if (p instanceof Player) {
            // Players cannot be teleported every tick without rubber-banding, so steer them instead.
            p.setVelocity(velocity.multiply(1.1));
        } else {
            Location target = p.getLocation().add(delta);
            target.setYaw(now.getYaw());
            target.setPitch(now.getPitch());
            if (target.getBlock().isPassable() && target.clone().add(0, 1, 0).getBlock().isPassable()) {
                p.teleport(target);
            } else {
                p.setRotation(now.getYaw(), now.getPitch());
            }
        }
        p.setFallDistance(0);

        if (tick % 3 == 0) {
            // Marionette strings: four thin lines from above the puppet's head.
            Location top = p.getLocation().add(0, p.getHeight() + 1.6, 0);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + tick * 0.02;
                Location limb = p.getLocation().add(Math.cos(a) * 0.35, p.getHeight() * 0.6, Math.sin(a) * 0.35);
                Fx.line(top, limb, Fx.dust(color(), 0.35f), 0.25);
            }
        }
    }

    @Override
    public void onEnd() {
        if (puppet.entity() instanceof Mob mob && mob.isValid()) mob.setAware(true);
        Location at = puppet.center();
        Fx.spawn(at.getWorld(), Particle.DUST, at, Fx.scaled(30), 0.4, 0.6, 0.4, 0, Fx.dust(color(), 1f));
    }
}
