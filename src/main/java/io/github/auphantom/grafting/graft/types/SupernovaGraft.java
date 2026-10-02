package io.github.auphantom.grafting.graft.types;

import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;

import io.github.auphantom.grafting.util.Fx;
import io.github.auphantom.grafting.anchor.Anchor;
import io.github.auphantom.grafting.anchor.BlockAnchor;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * <b>Place + Anything: Supernova.</b>
 * <p>
 * The first end is the "star": any block. Its <em>death</em> is grafted onto the second
 * end, which then lives out a star's final seconds in fast-forward:
 * <ol>
 *   <li><b>Ignition</b> - a sun kindles at the target, dragging matter (and nearby beings) into an accretion disk.</li>
 *   <li><b>Collapse</b> - the sun implodes to a single point, sucking all light in with it.</li>
 *   <li><b>Detonation</b> - a supernova: blinding flash, an expanding shockwave, damage and knockback.</li>
 * </ol>
 * Once ignited, a dying star cannot be stopped: the graft survives its ends being destroyed.
 * The owner is never harmed by their own supernova.
 */
public final class SupernovaGraft extends Graft {

    public static final int IGNITION_TICKS = 70;
    public static final int COLLAPSE_TICKS = 16;
    public static final int SHOCKWAVE_TICKS = 34;

    private static final Color CORE = Color.fromRGB(0xfff6d6);
    private static final Color HOT = Color.fromRGB(0xffb13b);
    private static final Color RED = Color.fromRGB(0xff4d2e);

    private final Anchor target;
    private final double radius;
    private final double maxDamage;
    private final boolean breakBlocks;
    private final GraftManager manager;
    private long startedAt;
    private Location core;
    private boolean detonated;

    public SupernovaGraft(int id, UUID owner, BlockAnchor star, Anchor target, long expiresAt, GraftManager manager,
                          double radius, double maxDamage, boolean breakBlocks) {
        super(id, Mode.SUPERNOVA, owner, star, target, expiresAt);
        this.target = target;
        this.manager = manager;
        this.radius = radius;
        this.maxDamage = maxDamage;
        this.breakBlocks = breakBlocks;
    }

    @Override
    public String summary() {
        return "The death of a star has been grafted onto " + target.describe() + ". Run.";
    }

    @Override
    public boolean drawsThread() {
        return !detonated && age() < IGNITION_TICKS;
    }

    /** A star that has begun dying cannot be saved by breaking the thread. */
    @Override
    public boolean isIntact() {
        return true;
    }

    @Override
    public void onStart() {
        startedAt = manager.currentTick();
        core = coreLocation();
        World world = core.getWorld();
        world.playSound(core, Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.5f);
        world.playSound(core, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 0.6f);
    }

    private long age() {
        return manager.currentTick() - startedAt;
    }

    private Location coreLocation() {
        if (target instanceof EntityAnchor e && e.isIntact()) return e.center();
        if (target instanceof BlockAnchor b) return b.center().add(0, 1.5, 0);
        return core != null ? core : target.center();
    }

    @Override
    public void tick(long tick) {
        long age = age();
        if (age < IGNITION_TICKS) {
            core = coreLocation();
            ignition(age);
        } else if (age < IGNITION_TICKS + COLLAPSE_TICKS) {
            collapse(age - IGNITION_TICKS);
        } else if (!detonated) {
            detonate();
        } else if (age < IGNITION_TICKS + COLLAPSE_TICKS + SHOCKWAVE_TICKS) {
            shockwave(age - IGNITION_TICKS - COLLAPSE_TICKS);
        } else {
            spend();
        }
    }

    private void ignition(long age) {
        World world = core.getWorld();
        double progress = age / (double) IGNITION_TICKS;
        double starRadius = 0.4 + progress * 1.6;

        // The star itself: a sphere of hot dust with a white core, flickering.
        Fx.sphere(core, starRadius, Particle.DUST, Fx.dust(HOT, 1.1f), 40 + (int) (progress * 40));
        Fx.sphere(core, starRadius * 0.55, Particle.DUST, Fx.dust(CORE, 1.4f), 20);
        Fx.spawn(world, Particle.FLAME, core, Fx.scaled(6), starRadius * 0.5, starRadius * 0.5, starRadius * 0.5, 0.01, null);

        // Two tilted accretion rings spinning around it.
        double spin = age * 0.22;
        accretionRing(starRadius + 1.4, spin, 0.35, Fx.dust(HOT, 0.8f));
        accretionRing(starRadius + 2.2, -spin * 0.8, -0.5, Fx.dust(RED, 0.7f));

        // Matter streaming inward from far away.
        for (int i = 0; i < Fx.scaled(6); i++) {
            Vector dir = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize();
            Location from = core.clone().add(dir.clone().multiply(radius * 0.9));
            Vector velocity = dir.multiply(-1);
            world.spawnParticle(Particle.END_ROD, from, 0, velocity.getX(), velocity.getY(), velocity.getZ(), 0.5, null, true);
        }

        // Nearby beings are dragged toward the star (the owner is not).
        if (age % 2 == 0) {
            for (Entity entity : world.getNearbyEntities(core, radius, radius, radius)) {
                if (!(entity instanceof LivingEntity living) || isOwner(living)) continue;
                Vector pull = core.toVector().subtract(living.getLocation().toVector());
                double d = pull.length();
                if (d < 0.5) continue;
                living.setVelocity(living.getVelocity().add(pull.normalize().multiply(0.06 + 0.12 * progress)));
                if (target instanceof EntityAnchor e && e.is(living)) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 3, true, false, false));
                }
            }
        }

        if (age % 10 == 0) {
            float pitch = (float) (0.5 + progress * 1.2);
            world.playSound(core, Sound.BLOCK_BEACON_AMBIENT, 2f, pitch);
            world.playSound(core, Sound.BLOCK_FIRE_AMBIENT, 1.5f, pitch);
        }
        if (age == IGNITION_TICKS - 20) world.playSound(core, Sound.ENTITY_WARDEN_SONIC_CHARGE, 2f, 1.4f);
    }

    private void accretionRing(double r, double spin, double tilt, Particle.DustOptions dust) {
        int points = Fx.scaled(28);
        World world = core.getWorld();
        for (int i = 0; i < points; i++) {
            double a = spin + 2 * Math.PI * i / points;
            double x = Math.cos(a) * r;
            double z = Math.sin(a) * r;
            double y = z * tilt;
            world.spawnParticle(Particle.DUST, core.clone().add(x, y, z), 1, 0, 0, 0, 0, dust, true);
        }
    }

    private void collapse(long t) {
        World world = core.getWorld();
        double r = 2.0 * (1 - t / (double) COLLAPSE_TICKS);
        Fx.sphere(core, Math.max(0.1, r), Particle.DUST, Fx.dust(t % 2 == 0 ? CORE : HOT, 1.2f), 60);
        Fx.spawn(world, Particle.REVERSE_PORTAL, core, Fx.scaled(30), r, r, r, 0.4, null);
        Fx.spawn(world, Particle.SQUID_INK, core, Fx.scaled(6), 0.1, 0.1, 0.1, 0.02, null);
        if (t == 0) {
            world.playSound(core, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.5f);
            world.playSound(core, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2f, 0.5f);
        }
    }

    private void detonate() {
        detonated = true;
        World world = core.getWorld();
        Player owner = Bukkit.getPlayer(owner());

        // Light and sound.
        Fx.spawn(world, Particle.FLASH, core, 3, 0.2, 0.2, 0.2, 0, null);
        Fx.spawn(world, Particle.EXPLOSION_EMITTER, core, 2, 0.5, 0.5, 0.5, 0, null);
        Fx.spawn(world, Particle.END_ROD, core, Fx.scaled(160), 0.2, 0.2, 0.2, 0.7, null);
        Fx.spawn(world, Particle.FLAME, core, Fx.scaled(160), 0.3, 0.3, 0.3, 0.45, null);
        Fx.spawn(world, Particle.LAVA, core, Fx.scaled(40), 1, 1, 1, 0, null);
        Fx.sphere(core, 1.5, Particle.DUST, Fx.dust(CORE, 2.5f), 120);
        world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
        world.playSound(core, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.6f);
        world.playSound(core, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 0.7f);

        // Damage falls off with distance; the owner is spared.
        for (Entity entity : world.getNearbyEntities(core, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || isOwner(living)) continue;
            double d = living.getLocation().add(0, living.getHeight() / 2, 0).distance(core);
            if (d > radius) continue;
            double falloff = 1 - d / radius;
            double amount = maxDamage * falloff;
            GraftManager.dealDamage(() -> {
                if (owner != null) living.damage(amount, owner);
                else living.damage(amount);
            });
            Vector away = living.getLocation().toVector().subtract(core.toVector());
            if (away.lengthSquared() < 0.01) away = new Vector(0, 1, 0);
            living.setVelocity(away.normalize().multiply(0.6 + 1.6 * falloff).setY(0.5 + 0.6 * falloff));
            living.setFireTicks(Math.max(living.getFireTicks(), (int) (100 * falloff)));
        }
        if (breakBlocks) world.createExplosion(core, 5f, false, true, owner);
    }

    private void shockwave(long t) {
        World world = core.getWorld();
        double r = 0.8 + t * (radius * 1.2 / SHOCKWAVE_TICKS);
        Location ground = core.clone();
        Fx.ring(ground, r, Particle.DUST, Fx.dust(t < SHOCKWAVE_TICKS / 2 ? CORE : HOT, 1.6f), (int) (24 + r * 10));
        Fx.ring(ground.clone().add(0, 0.4, 0), r * 0.92, Particle.FLAME, null, (int) (10 + r * 4));
        // A vertical ring as well, so the blast reads as a sphere, not a pancake.
        int points = Fx.scaled((int) (16 + r * 6));
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            Location at = core.clone().add(Math.cos(a) * r, Math.sin(a) * r, 0);
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, Fx.dust(RED, 1.2f), true);
            Location at2 = core.clone().add(0, Math.sin(a) * r, Math.cos(a) * r);
            world.spawnParticle(Particle.DUST, at2, 1, 0, 0, 0, 0, Fx.dust(RED, 1.2f), true);
        }
        if (t % 6 == 0) Fx.spawn(world, Particle.CAMPFIRE_COSY_SMOKE, core, Fx.scaled(8), 0.8, 0.8, 0.8, 0.03, null);
    }

    private boolean isOwner(Entity entity) {
        return entity.getUniqueId().equals(owner());
    }
}
