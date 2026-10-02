package io.github.auphantom.grafting.graft.types;

import io.github.auphantom.grafting.anchor.EntityAnchor;
import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;
import io.github.auphantom.grafting.util.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;

/**
 * <b>Being + Being: Life.</b>
 * <p>
 * The first being's life is grafted onto the second. From now on the first only lives as
 * long as the second does: kill the second and the first dies with it, wherever it is.
 * (Graft a Fool's life onto a cow, kill the cow, and the Fool dies.)
 * <p>
 * Beyonders of the same or a higher sequence usually shake it off; that roll happens in
 * the factory, before the graft is ever made.
 */
public final class LifeGraft extends Graft {

    private static final Color PULSE = Color.fromRGB(0xff4f8b);

    private final EntityAnchor life;
    private final EntityAnchor vessel;
    private boolean claimed;

    public LifeGraft(int id, UUID owner, EntityAnchor life, EntityAnchor vessel, long expiresAt) {
        super(id, Mode.LIFE, owner, life, vessel, expiresAt);
        this.life = life;
        this.vessel = vessel;
    }

    @Override
    public String summary() {
        return life.describe() + "'s life now lives in " + vessel.describe() + ". If it dies, so does "
                + life.describe() + ".";
    }

    /** The vessel's death must still end the graft (and claim the life), so it may not "snap" first. */
    @Override
    public boolean isIntact() {
        return claimed || (life.isIntact() && vessel.isIntact());
    }

    @Override
    public void tick(long tick) {
        if (claimed) return;
        if (tick % 20 == 0) {
            // A heartbeat at both ends, in step.
            for (EntityAnchor end : new EntityAnchor[]{life, vessel}) {
                Location at = end.entity().getLocation().add(0, end.entity().getHeight() + 0.4, 0);
                at.getWorld().spawnParticle(Particle.HEART, at, 1, 0.1, 0.1, 0.1, 0);
                Fx.ring(end.entity().getLocation().add(0, 0.1, 0), 0.7, Particle.DUST, Fx.dust(PULSE, 0.7f), 18);
            }
        }
    }

    /** Runs late, so armour, totems and other grafts have had their say: this hit really is lethal. */
    @Override
    public void onLateDamage(EntityDamageEvent event) {
        if (claimed || !vessel.is(event.getEntity())) return;
        LivingEntity v = vessel.entity();
        if (event.getFinalDamage() < v.getHealth() + v.getAbsorptionAmount()) return;
        claimed = true;
        LivingEntity victim = life.entity();
        Location vAt = vessel.center();
        vAt.getWorld().playSound(vAt, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.6f);
        Fx.line(vAt, victim.getWorld().equals(vAt.getWorld()) ? life.center() : vAt, Fx.dust(PULSE, 1f), 0.3);
        // The death travels along the thread on the next tick, after the vessel itself has died.
        org.bukkit.Bukkit.getScheduler().runTask(org.bukkit.Bukkit.getPluginManager().getPlugin("Grafting"), () -> {
            if (!victim.isValid() || victim.isDead()) {
                spend();
                return;
            }
            Location at = victim.getLocation().add(0, 1, 0);
            at.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, at, 20, 0.3, 0.5, 0.3, 0.2);
            at.getWorld().spawnParticle(Particle.DUST, at, Fx.scaled(40), 0.4, 0.7, 0.4, 0, Fx.dust(PULSE, 1.2f));
            at.getWorld().playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.4f);
            at.getWorld().playSound(at, Sound.BLOCK_CHAIN_BREAK, 1f, 0.5f);
            if (victim instanceof Player p) p.setNoDamageTicks(0);
            DamageSource source = DamageSource.builder(DamageType.MAGIC).build();
            GraftManager.dealDamage(() -> {
                victim.setAbsorptionAmount(0);
                victim.damage(victim.getHealth() + 1000, source);
                if (!victim.isDead()) victim.setHealth(0);
            });
            spend();
        });
    }

    @Override
    public void onEnd() {
        if (!claimed) {
            Location at = life.center();
            at.getWorld().spawnParticle(Particle.HEART, at, 3, 0.3, 0.3, 0.3, 0);
        }
    }
}
