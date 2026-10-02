package io.github.auphantom.grafting.graft.types;

import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;

import io.github.auphantom.grafting.anchor.EntityAnchor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Being + Being: Fate.</b>
 * <p>
 * The first being's fate of being harmed is grafted onto the second. Any damage
 * that should hit the first lands on the second instead, wherever it is, even if
 * the second being is the one who swung the sword.
 */
public final class FateGraft extends Graft {

    /** Entities currently receiving redirected harm. Stops A-to-B-to-A loops from recursing forever. */
    private static final Set<UUID> REDIRECTING = new HashSet<>();

    private final EntityAnchor from;
    private final EntityAnchor to;

    public FateGraft(int id, UUID owner, EntityAnchor from, EntityAnchor to, long expiresAt) {
        super(id, Mode.FATE, owner, from, to, expiresAt);
        this.from = from;
        this.to = to;
    }

    @Override
    public String summary() {
        return "Harm meant for " + from.describe() + " now finds " + to.describe() + " instead.";
    }

    @Override
    public void onDamage(EntityDamageEvent event) {
        if (!from.is(event.getEntity())) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.KILL) return; // /kill is not "harm", it is an order
        LivingEntity victim = to.entity();
        if (REDIRECTING.contains(victim.getUniqueId()) || REDIRECTING.contains(from.entity().getUniqueId())) return;

        double damage = event.getDamage();
        event.setCancelled(true);

        REDIRECTING.add(victim.getUniqueId());
        try {
            victim.setNoDamageTicks(0);
            GraftManager.dealDamage(() -> victim.damage(damage, event.getDamageSource()));
        } finally {
            REDIRECTING.remove(victim.getUniqueId());
        }

        Location saved = from.center();
        saved.getWorld().spawnParticle(Particle.ENCHANTED_HIT, saved, 12, 0.3, 0.4, 0.3, 0.2);
        saved.getWorld().playSound(saved, Sound.BLOCK_CHAIN_BREAK, 0.8f, 1.6f);
        Location hit = to.center();
        hit.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, hit, 6, 0.3, 0.4, 0.3, 0.1);
        hit.getWorld().spawnParticle(Particle.DUST, hit, 15, 0.3, 0.5, 0.3, 0,
                new Particle.DustOptions(color(), 1.0f));
    }
}
