package io.github.auphantom.grafting.graft;

import io.github.auphantom.grafting.anchor.BlockAnchor;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * <b>Being + Place: Death and Return.</b>
 * <p>
 * The being's next death is grafted onto a journey home. When a blow would kill
 * it, the death simply never happens: it is pulled along the thread back to the
 * anchored block instead, alive. Works once, then the thread is used up.
 */
public final class ReturnGraft extends Graft {

    private final EntityAnchor being;
    private final BlockAnchor home;

    public ReturnGraft(int id, UUID owner, EntityAnchor being, BlockAnchor home, long expiresAt) {
        super(id, Mode.RETURN, owner, being, home, expiresAt);
        this.being = being;
        this.home = home;
    }

    @Override
    public String summary() {
        return "The next death of " + being.describe() + " becomes a journey back to " + home.describe() + ".";
    }

    @Override
    public void onLateDamage(EntityDamageEvent event) {
        if (!being.is(event.getEntity())) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.KILL) return;
        LivingEntity entity = being.entity();
        if (event.getFinalDamage() < entity.getHealth() + entity.getAbsorptionAmount()) return;

        event.setCancelled(true);
        Location deathPlace = entity.getLocation();
        deathPlace.getWorld().spawnParticle(Particle.SOUL, deathPlace.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.03);
        deathPlace.getWorld().playSound(deathPlace, Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 0.8f);

        var maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20 : maxHealth.getValue();
        entity.setHealth(Math.max(1, max / 2));
        entity.setFireTicks(0);
        entity.setFreezeTicks(0);
        entity.setFallDistance(0);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4));

        Location target = home.standingSpot();
        target.setYaw(deathPlace.getYaw());
        target.setPitch(deathPlace.getPitch());
        entity.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        entity.setVelocity(new org.bukkit.util.Vector());

        target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.4);
        target.getWorld().playSound(target, Sound.ITEM_TOTEM_USE, 0.7f, 1.3f);
        spend();
    }
}
