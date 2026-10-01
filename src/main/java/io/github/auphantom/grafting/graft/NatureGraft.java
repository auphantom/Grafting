package io.github.auphantom.grafting.graft;

import io.github.auphantom.grafting.GraftingPlugin;
import io.github.auphantom.grafting.anchor.BlockAnchor;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zoglin;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <b>Place + Being: Nature.</b>
 * <p>
 * The nature of a block is grafted onto a living being. Tie a slime block to a
 * zombie and the zombie bounces. Tie TNT to a creeper's victim and it explodes
 * when struck. Tie a cobweb to a fleeing player and they are stuck. See {@link Nature}.
 */
public final class NatureGraft extends Graft {

    /** Effects are re-applied every second with this duration, so they fade on their own if anything goes wrong. */
    private static final int EFFECT_TICKS = 60;

    private final EntityAnchor being;
    private final Nature nature;
    private final List<PotionEffectType> applied = new ArrayList<>();

    public NatureGraft(int id, UUID owner, BlockAnchor source, EntityAnchor being, Nature nature, long expiresAt) {
        super(id, owner, source, being, expiresAt);
        this.being = being;
        this.nature = nature;
    }

    public Nature nature() {
        return nature;
    }

    @Override
    public String name() {
        return "Nature of " + nature.display();
    }

    @Override
    public String summary() {
        return being.describe() + " " + nature.description() + ".";
    }

    @Override
    public Color color() {
        return switch (nature) {
            case BOUNCE -> Color.fromRGB(0x7fd35a);
            case FLAME -> Color.fromRGB(0xff7a1a);
            case FROST -> Color.fromRGB(0x9fe3ff);
            case STONE -> Color.fromRGB(0x8a8a8a);
            case FEATHER -> Color.fromRGB(0xf2f2f2);
            case WATER -> Color.fromRGB(0x2a6cff);
            case LIGHT -> Color.fromRGB(0xfff27a);
            case STICKY -> Color.fromRGB(0xe0a21c);
            case LIFE -> Color.fromRGB(0xff6fae);
            case VOLATILE -> Color.fromRGB(0xff2020);
        };
    }

    @Override
    public void onStart() {
        applyEffects();
    }

    @Override
    public void tick(long tick) {
        LivingEntity entity = being.entity();
        if (tick % 20 == 0) applyEffects();
        switch (nature) {
            case FROST -> freezeWaterBelow(entity);
            case LIGHT -> {
                if (tick % 20 == 0) burnUndead(entity);
            }
            case VOLATILE -> {
                if (tick % 4 == 0) {
                    entity.getWorld().spawnParticle(Particle.SMOKE, entity.getEyeLocation(), 3, 0.2, 0.2, 0.2, 0.01);
                }
                if (tick % 30 == 0) entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 0.4f, 1.6f);
            }
            default -> {
            }
        }
        if (tick % 10 == 0) {
            entity.getWorld().spawnParticle(Particle.DUST, being.center(), 3, 0.3, 0.5, 0.3, 0,
                    new Particle.DustOptions(color(), 0.8f));
        }
    }

    @Override
    public void onEnd() {
        LivingEntity entity = being.entity();
        if (!entity.isValid()) return;
        for (PotionEffectType type : applied) {
            PotionEffect current = entity.getPotionEffect(type);
            // Only strip effects that are still ours (short and refreshed by us), never a real potion.
            if (current != null && current.isAmbient() && current.getDuration() <= durationOf(type)) {
                entity.removePotionEffect(type);
            }
        }
    }

    private void applyEffects() {
        switch (nature) {
            case FLAME -> effect(PotionEffectType.FIRE_RESISTANCE, 0);
            case STONE -> {
                effect(PotionEffectType.RESISTANCE, 2);
                effect(PotionEffectType.SLOWNESS, 2);
                effect(PotionEffectType.MINING_FATIGUE, 1);
            }
            case FEATHER -> {
                effect(PotionEffectType.SLOW_FALLING, 0);
                effect(PotionEffectType.SPEED, 1);
            }
            case WATER -> {
                effect(PotionEffectType.WATER_BREATHING, 0);
                effect(PotionEffectType.DOLPHINS_GRACE, 0);
                effect(PotionEffectType.CONDUIT_POWER, 0);
            }
            case LIGHT -> {
                effect(PotionEffectType.GLOWING, 0);
                effect(PotionEffectType.NIGHT_VISION, 0);
            }
            case STICKY -> {
                effect(PotionEffectType.SLOWNESS, 4);
                effect(PotionEffectType.WEAKNESS, 0);
            }
            case LIFE -> effect(PotionEffectType.REGENERATION, 1);
            default -> {
            }
        }
    }

    private void effect(PotionEffectType type, int amplifier) {
        being.entity().addPotionEffect(new PotionEffect(type, durationOf(type), amplifier, true, false, true));
        if (!applied.contains(type)) applied.add(type);
    }

    /** Night vision flickers when it has under 10 seconds left, so it gets a longer pulse. */
    private static int durationOf(PotionEffectType type) {
        return type == PotionEffectType.NIGHT_VISION ? 260 : EFFECT_TICKS;
    }

    @Override
    public void onDamage(EntityDamageEvent event) {
        LivingEntity self = being.entity();
        if (being.is(event.getEntity())) {
            onHurt(event, self);
        } else if (event instanceof EntityDamageByEntityEvent byEntity && being.is(attacker(byEntity))
                && event.getEntity() instanceof LivingEntity victim) {
            onStrike(victim);
        }
    }

    /** Things that happen when the grafted being gets hurt. */
    private void onHurt(EntityDamageEvent event, LivingEntity self) {
        DamageCause cause = event.getCause();
        switch (nature) {
            case BOUNCE -> {
                if (cause != DamageCause.FALL) return;
                event.setCancelled(true);
                // Raw fall damage is (blocks fallen - 3). Bounce back to ~65% of that height,
                // so bounces lose energy and die out like a real slime block instead of climbing forever.
                double height = event.getDamage() + 3;
                double vy = Math.min(2.5, Math.sqrt(2 * 0.08 * height) * 0.8);
                Vector v = self.getVelocity();
                Bukkit.getScheduler().runTask(JavaPlugin.getPlugin(GraftingPlugin.class),
                        () -> self.setVelocity(new Vector(v.getX(), vy, v.getZ())));
                self.getWorld().playSound(self.getLocation(), Sound.BLOCK_SLIME_BLOCK_FALL, 1f, 1f);
                self.getWorld().spawnParticle(Particle.ITEM_SLIME, self.getLocation(), 20, 0.4, 0.1, 0.4, 0);
            }
            case FEATHER -> {
                if (cause == DamageCause.FALL) event.setCancelled(true);
            }
            case FROST -> {
                if (cause == DamageCause.FREEZE) event.setCancelled(true);
            }
            case FLAME -> {
                if (event instanceof EntityDamageByEntityEvent byEntity && cause == DamageCause.ENTITY_ATTACK
                        && byEntity.getDamager() instanceof LivingEntity attacker) {
                    attacker.setFireTicks(Math.max(attacker.getFireTicks(), 80));
                }
            }
            case VOLATILE -> {
                if (!(event instanceof EntityDamageByEntityEvent)) return;
                spend(); // spend first, so the blast below cannot trigger this graft again
                Location at = self.getLocation();
                Player owner = Bukkit.getPlayer(owner());
                at.getWorld().createExplosion(at, 2.5f, false, false, owner != null ? owner : self);
            }
            default -> {
            }
        }
    }

    /** Things that happen to whatever the grafted being hits. */
    private void onStrike(LivingEntity victim) {
        switch (nature) {
            case FLAME -> victim.setFireTicks(Math.max(victim.getFireTicks(), 100));
            case FROST -> {
                victim.setFreezeTicks(Math.min(victim.getMaxFreezeTicks() + 100, victim.getFreezeTicks() + 120));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getEyeLocation(), 15, 0.3, 0.4, 0.3, 0.02);
            }
            default -> {
            }
        }
    }

    private static Entity attacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    /** Frost Walker, but innate. */
    private static void freezeWaterBelow(LivingEntity entity) {
        if (!entity.isOnGround() && !entity.isInWater() && entity.getVelocity().getY() > 0) return;
        Block feet = entity.getLocation().getBlock();
        World world = entity.getWorld();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) continue;
                Block below = feet.getRelative(dx, -1, dz);
                Block above = below.getRelative(0, 1, 0);
                if (below.getType() != Material.WATER || !above.getType().isAir()) continue;
                BlockData data = below.getBlockData();
                if (data instanceof Levelled levelled && levelled.getLevel() != 0) continue; // source blocks only
                below.setType(Material.FROSTED_ICE);
            }
        }
        if (world.getGameTime() % 10 == 0) {
            world.spawnParticle(Particle.SNOWFLAKE, entity.getLocation(), 4, 0.5, 0.1, 0.5, 0);
        }
    }

    private static void burnUndead(LivingEntity source) {
        for (Entity nearby : source.getNearbyEntities(6, 4, 6)) {
            if (nearby instanceof Zombie || nearby instanceof AbstractSkeleton
                    || nearby instanceof Phantom || nearby instanceof Zoglin) {
                LivingEntity undead = (LivingEntity) nearby;
                undead.setFireTicks(Math.max(undead.getFireTicks(), 60));
                undead.getWorld().spawnParticle(Particle.END_ROD, undead.getEyeLocation(), 5, 0.2, 0.3, 0.2, 0.02);
            }
        }
    }
}
