package io.github.auphantom.grafting.anchor;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** A being. The thread breaks when it dies, despawns or logs out. */
public record EntityAnchor(LivingEntity entity) implements Anchor {

    @Override
    public Location center() {
        return entity.getLocation().add(0, entity.getHeight() / 2, 0);
    }

    @Override
    public boolean isIntact() {
        return entity.isValid() && !entity.isDead();
    }

    @Override
    public String describe() {
        if (entity instanceof Player p) return p.getName();
        return BlockAnchor.pretty(entity.getType().name());
    }

    @Override
    public boolean sameAs(Anchor other) {
        return other instanceof EntityAnchor e && e.entity.getUniqueId().equals(entity.getUniqueId());
    }

    public boolean is(Entity other) {
        return other != null && other.getUniqueId().equals(entity.getUniqueId());
    }
}
