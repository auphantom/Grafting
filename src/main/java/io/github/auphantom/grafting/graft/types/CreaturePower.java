package io.github.auphantom.grafting.graft.types;

import io.github.auphantom.grafting.util.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.WindCharge;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * A creature's power, as it can be grafted onto a player by an {@link AbilityGraft}.
 * Used with sneak + swap-hands (F). Creatures without a signature move lend a passive trait.
 */
public enum CreaturePower {
    BLAZE("Blaze Fire", "hurl a burst of fire", 30, PotionEffectType.FIRE_RESISTANCE),
    GHAST("Ghast Fireball", "launch an exploding fireball", 60, null),
    ENDERMAN("Ender Blink", "blink up to 24 blocks ahead", 40, null),
    CREEPER("Creeper Blast", "detonate without harming yourself", 100, null),
    EVOKER("Evoker Fangs", "summon a line of fangs", 50, null),
    BREEZE("Breeze Gust", "fire a wind charge", 20, null),
    PASSIVE_HOSTILE("Predator's Strength", "a hunter's strength", 0, PotionEffectType.STRENGTH),
    PASSIVE_GENTLE("Gentle Vitality", "a gentle creature's regeneration", 0, PotionEffectType.REGENERATION);

    private final String display;
    private final String verb;
    private final int cooldownTicks;
    private final PotionEffectType passive;

    CreaturePower(String display, String verb, int cooldownTicks, PotionEffectType passive) {
        this.display = display;
        this.verb = verb;
        this.cooldownTicks = cooldownTicks;
        this.passive = passive;
    }

    public String display() {
        return display;
    }

    public String verb() {
        return verb;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public boolean active() {
        return cooldownTicks > 0;
    }

    public static CreaturePower of(EntityType type, boolean hostile) {
        return switch (type) {
            case BLAZE -> BLAZE;
            case GHAST -> GHAST;
            case ENDERMAN -> ENDERMAN;
            case CREEPER -> CREEPER;
            case EVOKER -> EVOKER;
            case BREEZE -> BREEZE;
            default -> hostile ? PASSIVE_HOSTILE : PASSIVE_GENTLE;
        };
    }

    /** Refreshes the passive trait (called every second while grafted). */
    public void sustain(Player player) {
        if (passive != null) player.addPotionEffect(new PotionEffect(passive, 50, 0, true, false, true));
    }

    public void clear(Player player) {
        if (passive == null) return;
        PotionEffect current = player.getPotionEffect(passive);
        if (current != null && current.isAmbient() && current.getDuration() <= 50) player.removePotionEffect(passive);
    }

    /** Performs the power. */
    public void use(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        switch (this) {
            case BLAZE -> {
                for (int i = 0; i < 3; i++) {
                    Vector spread = dir.clone().add(new Vector(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).multiply(0.15));
                    player.launchProjectile(SmallFireball.class, spread.multiply(1.2));
                }
                player.getWorld().playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
            }
            case GHAST -> {
                Fireball ball = player.launchProjectile(LargeFireball.class, dir.clone().multiply(1.5));
                ball.setYield(1.5f);
                ball.setIsIncendiary(false);
                player.getWorld().playSound(eye, Sound.ENTITY_GHAST_SHOOT, 1f, 1f);
            }
            case ENDERMAN -> {
                RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, dir, 24);
                Location to = hit != null ? hit.getHitPosition().toLocation(player.getWorld()).subtract(dir.clone().multiply(0.8))
                        : eye.clone().add(dir.clone().multiply(24));
                to.setYaw(eye.getYaw());
                to.setPitch(eye.getPitch());
                to.subtract(0, 1.5, 0);
                if (!to.getBlock().isPassable()) to.add(0, 1, 0);
                Location from = player.getLocation();
                player.teleport(to);
                player.setFallDistance(0);
                from.getWorld().spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 60, 0.3, 0.8, 0.3, 0.6);
                to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }
            case CREEPER -> {
                Location at = player.getLocation();
                at.getWorld().createExplosion(player, at, 3f, false, false);
            }
            case EVOKER -> {
                Vector flat = dir.clone().setY(0);
                if (flat.lengthSquared() < 0.01) flat = new Vector(1, 0, 0);
                flat.normalize();
                Location base = player.getLocation();
                for (int i = 1; i <= 12; i++) {
                    Location at = base.clone().add(flat.clone().multiply(i * 1.2));
                    at.setY(at.getWorld().getHighestBlockYAt(at) + 1);
                    if (Math.abs(at.getY() - base.getY()) > 4) at.setY(base.getY());
                    EvokerFangs fangs = at.getWorld().spawn(at, EvokerFangs.class);
                    fangs.setOwner(player);
                }
                player.getWorld().playSound(base, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1f);
            }
            case BREEZE -> {
                player.launchProjectile(WindCharge.class, dir.clone().multiply(1.5));
                player.getWorld().playSound(eye, Sound.ENTITY_BREEZE_SHOOT, 1f, 1f);
            }
            default -> {
            }
        }
        Fx.ring(player.getLocation().add(0, 0.1, 0), 0.8, Particle.DUST, Fx.dust(Color.fromRGB(0xf2a7ff), 0.9f), 24);
    }
}
