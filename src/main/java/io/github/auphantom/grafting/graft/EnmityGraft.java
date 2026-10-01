package io.github.auphantom.grafting.graft;

import io.github.auphantom.grafting.Fx;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.UUID;

/**
 * <b>Being + Being: Enmity.</b>
 * <p>
 * Hostility toward the first being is grafted onto the second. Every mob that wants
 * to attack the first one finds itself hating the second instead, including mobs that
 * were already chasing it. Graft yourself onto a zombie and the whole horde turns on it.
 */
public final class EnmityGraft extends Graft {

    private static final double RADIUS = 24;

    private final EntityAnchor hated;
    private final EntityAnchor scapegoat;

    public EnmityGraft(int id, UUID owner, EntityAnchor hated, EntityAnchor scapegoat, long expiresAt) {
        super(id, Mode.ENMITY, owner, hated, scapegoat, expiresAt);
        this.hated = hated;
        this.scapegoat = scapegoat;
    }

    @Override
    public String summary() {
        return "Every creature that hunts " + hated.describe() + " now hunts " + scapegoat.describe() + ".";
    }

    @Override
    public void onTarget(EntityTargetEvent event) {
        if (!hated.is(event.getTarget())) return;
        if (scapegoat.is(event.getEntity())) return; // the scapegoat cannot be made to hate itself
        event.setTarget(scapegoat.entity());
        mark(event.getEntity());
    }

    @Override
    public void tick(long tick) {
        // Redirect mobs that were already chasing before the graft existed.
        if (tick % 10 == 0) {
            LivingEntity target = scapegoat.entity();
            for (Entity nearby : hated.entity().getNearbyEntities(RADIUS, RADIUS / 2, RADIUS)) {
                if (nearby instanceof Mob mob && hated.is(mob.getTarget()) && !scapegoat.is(mob)) {
                    mob.setTarget(target);
                    mark(mob);
                }
            }
        }
        if (tick % 5 == 0) {
            Location head = scapegoat.entity().getEyeLocation().add(0, 0.6, 0);
            Fx.spawn(head.getWorld(), Particle.ANGRY_VILLAGER, head, Fx.scaled(2), 0.3, 0.1, 0.3, 0, null);
            Fx.ring(scapegoat.entity().getLocation().add(0, 0.1, 0), 0.8, Particle.DUST, Fx.dust(color(), 0.8f), 16);
        }
    }

    private void mark(Entity mob) {
        Location eye = mob.getLocation().add(0, mob.getHeight() + 0.3, 0);
        Fx.spawn(eye.getWorld(), Particle.DUST, eye, Fx.scaled(12), 0.2, 0.2, 0.2, 0, Fx.dust(color(), 1f));
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.25f, 1.8f);
    }
}
