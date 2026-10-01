package io.github.auphantom.grafting.graft;

import io.github.auphantom.grafting.Fx;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * <b>Being + Being: Exchange.</b>
 * <p>
 * The two beings' <em>positions</em> are grafted onto each other. They swap places
 * immediately, and every time something strikes the first one, the blow lands on
 * empty air: the two swap again, so the attacker is suddenly facing the second.
 */
public final class ExchangeGraft extends Graft {

    private static final long COOLDOWN_TICKS = 10;

    private final EntityAnchor a;
    private final EntityAnchor b;
    private final GraftManager manager;
    private long lastSwap = Long.MIN_VALUE;

    public ExchangeGraft(int id, UUID owner, EntityAnchor a, EntityAnchor b, long expiresAt, GraftManager manager) {
        super(id, Mode.EXCHANGE, owner, a, b, expiresAt);
        this.a = a;
        this.b = b;
        this.manager = manager;
    }

    @Override
    public String summary() {
        return a.describe() + " and " + b.describe() + " have traded places, and will again whenever "
                + a.describe() + " is struck.";
    }

    @Override
    public void onStart() {
        swap();
    }

    @Override
    public void onDamage(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent)) return;
        if (!a.is(event.getEntity())) return;
        if (manager.currentTick() - lastSwap < COOLDOWN_TICKS) return;
        event.setCancelled(true);
        swap();
    }

    private void swap() {
        LivingEntity first = a.entity();
        LivingEntity second = b.entity();
        Location la = first.getLocation();
        Location lb = second.getLocation();
        Vector va = first.getVelocity();
        Vector vb = second.getVelocity();
        Location toA = lb.clone();
        toA.setYaw(la.getYaw());
        toA.setPitch(la.getPitch());
        Location toB = la.clone();
        toB.setYaw(lb.getYaw());
        toB.setPitch(lb.getPitch());
        first.teleport(toA, PlayerTeleportEvent.TeleportCause.PLUGIN);
        second.teleport(toB, PlayerTeleportEvent.TeleportCause.PLUGIN);
        first.setVelocity(vb);
        second.setVelocity(va);
        first.setFallDistance(0);
        second.setFallDistance(0);
        lastSwap = manager.currentTick();

        for (Location at : new Location[]{la, lb}) {
            Location mid = at.clone().add(0, 1, 0);
            Fx.spawn(at.getWorld(), Particle.REVERSE_PORTAL, mid, Fx.scaled(60), 0.35, 0.8, 0.35, 0.05, null);
            Fx.spawn(at.getWorld(), Particle.DUST, mid, Fx.scaled(40), 0.4, 0.9, 0.4, 0, Fx.dust(color(), 1.1f));
            Fx.ring(at.clone().add(0, 0.1, 0), 0.9, Particle.DUST, Fx.dust(color(), 0.9f), 28);
            at.getWorld().playSound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
        }
    }
}
