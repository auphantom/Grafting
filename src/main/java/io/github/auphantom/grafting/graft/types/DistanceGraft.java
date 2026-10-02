package io.github.auphantom.grafting.graft.types;

import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;

import io.github.auphantom.grafting.anchor.BlockAnchor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Place + Place: Distance.</b>
 * <p>
 * The distance between two places is grafted onto "zero". The two blocks become
 * the same place: anything that steps onto one of them is already standing on the
 * other. Works both ways, for players, mobs, items, arrows... anything.
 */
public final class DistanceGraft extends Graft {

    private static final double PAD_HEIGHT = 0.7;

    private final BlockAnchor a;
    private final BlockAnchor b;
    private final java.util.function.BiConsumer<Player, Double> travelled;
    /** Entities that just arrived on a pad. They must step off before it can carry them again. */
    private final Map<UUID, BlockAnchor> arrived = new HashMap<>();

    public DistanceGraft(int id, UUID owner, BlockAnchor a, BlockAnchor b, long expiresAt,
                         java.util.function.BiConsumer<Player, Double> travelled) {
        super(id, Mode.DISTANCE, owner, a, b, expiresAt);
        this.a = a;
        this.b = b;
        this.travelled = travelled;
    }

    @Override
    public String name() {
        return "Gateway";
    }

    @Override
    public String costKey() {
        return "gateway";
    }

    @Override
    public String summary() {
        return "The distance between these two places is now zero. Step on one, arrive on the other.";
    }

    @Override
    public void tick(long tick) {
        if (tick % 2 != 0) return;
        arrived.entrySet().removeIf(e -> !isOnPad(e.getKey(), e.getValue()));
        carry(a, b);
        carry(b, a);
        if (tick % 10 == 0) {
            ambient(a);
            ambient(b);
        }
    }

    private void carry(BlockAnchor from, BlockAnchor to) {
        if (!from.isLoaded()) return;
        for (Entity entity : from.block().getWorld().getNearbyEntities(padBox(from))) {
            if (!onPad(entity, from) || arrived.containsKey(entity.getUniqueId())) continue;
            if (entity.isInsideVehicle() || !entity.getPassengers().isEmpty()) continue;
            if (entity instanceof Player p && p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (!hasHeadroom(to, entity)) continue;

            Location dest = to.standingSpot();
            Location old = entity.getLocation();
            dest.setYaw(old.getYaw());
            dest.setPitch(old.getPitch());
            // Keep the offset from the pad centre, so walking across feels seamless.
            dest.add(old.getX() - from.standingSpot().getX(), 0, old.getZ() - from.standingSpot().getZ());
            Vector velocity = entity.getVelocity();

            if (entity.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                double moved = old.getWorld().equals(dest.getWorld()) ? old.distance(dest) : 0;
                entity.setVelocity(velocity);
                entity.setFallDistance(0);
                arrived.put(entity.getUniqueId(), to);
                old.getWorld().spawnParticle(Particle.REVERSE_PORTAL, old.add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
                dest.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.3);
                dest.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.6f);
                if (entity instanceof Player p && p.getUniqueId().equals(owner())) {
                    travelled.accept(p, moved);
                }
            }
        }
    }

    private boolean isOnPad(UUID id, BlockAnchor pad) {
        Entity entity = org.bukkit.Bukkit.getEntity(id);
        return entity != null && entity.isValid() && onPad(entity, pad);
    }

    private static BoundingBox padBox(BlockAnchor pad) {
        Location spot = pad.standingSpot();
        Block block = pad.block();
        return new BoundingBox(block.getX(), spot.getY() - 0.1, block.getZ(),
                block.getX() + 1, spot.getY() + PAD_HEIGHT, block.getZ() + 1);
    }

    private static boolean onPad(Entity entity, BlockAnchor pad) {
        return entity.getWorld().equals(pad.block().getWorld())
                && padBox(pad).contains(entity.getLocation().toVector());
    }

    /** Refuse to stuff an entity into a wall on the other side. */
    private static boolean hasHeadroom(BlockAnchor pad, Entity entity) {
        if (!pad.isLoaded()) return false;
        Block base = pad.standingSpot().getBlock();
        int needed = (int) Math.ceil(entity.getHeight());
        for (int i = 0; i < needed; i++) {
            Block b = base.getRelative(0, i, 0);
            if (!b.equals(pad.block()) && !b.isPassable()) return false;
        }
        return true;
    }

    private void ambient(BlockAnchor pad) {
        if (!pad.isLoaded()) return;
        Location spot = pad.standingSpot().add(0, 0.05, 0);
        spot.getWorld().spawnParticle(Particle.PORTAL, spot, 6, 0.3, 0.05, 0.3, 0.2);
    }
}
