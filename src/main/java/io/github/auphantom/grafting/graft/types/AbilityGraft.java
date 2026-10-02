package io.github.auphantom.grafting.graft.types;

import io.github.auphantom.grafting.anchor.EntityAnchor;
import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.Mode;
import io.github.auphantom.grafting.item.ThreadItem;
import io.github.auphantom.grafting.util.Fx;
import io.github.auphantom.grafting.util.Text;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Being + Being: Ability.</b> Sustained every second by the caster's spirit.
 * <ul>
 *   <li><b>Your power onto another</b> (first end = you): the receiver is handed a borrowed
 *       Thread of Grafting, locked to one of your abilities, and can graft with it while
 *       you keep paying for it. It vanishes the moment the graft ends.</li>
 *   <li><b>A creature's power onto a player</b> (first end = a creature): the player gains
 *       that creature's signature move (blaze fire, ender blink, ...) on sneak + F, or its
 *       passive trait.</li>
 * </ul>
 */
public final class AbilityGraft extends Graft {

    /** Players currently holding a grafted creature power, for the sneak + F listener. */
    private static final Map<UUID, AbilityGraft> POWERS = new HashMap<>();

    private final EntityAnchor source;
    private final EntityAnchor receiver;
    private final Mode lent;
    private final CreaturePower power;
    private long readyAt;

    public AbilityGraft(int id, UUID owner, EntityAnchor source, EntityAnchor receiver, long expiresAt, Mode lent) {
        super(id, Mode.ABILITY, owner, source, receiver, expiresAt);
        this.source = source;
        this.receiver = receiver;
        boolean ownPower = source.entity().getUniqueId().equals(owner) || source.entity() instanceof Player;
        this.lent = ownPower ? lent : null;
        this.power = ownPower ? null : CreaturePower.of(source.entity().getType(), source.entity() instanceof Enemy);
    }

    @Override
    public String name() {
        return lent != null ? "Ability: " + lent.display() : "Ability: " + power.display();
    }

    @Override
    public String summary() {
        if (lent != null) {
            return receiver.describe() + " can now use your " + lent.display() + " thread, sustained by your spirit.";
        }
        return receiver.describe() + " now holds the " + power.display() + " of " + source.describe()
                + (power.active() ? " (sneak + F to " + power.verb() + ")." : ".");
    }

    @Override
    public void onStart() {
        if (lent != null && receiver.entity() instanceof Player p) {
            ItemStack thread = ThreadItem.borrowed(lent, id());
            if (!p.getInventory().addItem(thread).isEmpty()) p.getWorld().dropItem(p.getLocation(), thread);
            Text.send(p, "<gray>A borrowed " + lent.tag() + "Thread of " + lent.display()
                    + "</color> is grafted into your hands. It lasts while its owner can sustain it.");
        } else if (power != null && receiver.entity() instanceof Player p) {
            POWERS.put(p.getUniqueId(), this);
            Text.send(p, "<gray>The <color:#f2a7ff>" + power.display() + "</color> of " + Text.esc(source.describe())
                    + " is grafted onto you." + (power.active() ? " <white>Sneak + F</white> to " + power.verb() + "." : ""));
        }
        Location at = receiver.center();
        at.getWorld().playSound(at, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.4f);
    }

    /** Only players can wield a power; other ends are refused by the factory. */
    @Override
    public void tick(long tick) {
        LivingEntity r = receiver.entity();
        if (tick % 20 == 0 && power != null && r instanceof Player p) power.sustain(p);
        if (tick % 5 == 0) {
            Location at = r.getLocation().add(0, r.getHeight() + 0.3, 0);
            Fx.ring(at, 0.4, Particle.DUST, Fx.dust(color(), 0.6f), 10);
        }
    }

    @Override
    public void onEnd() {
        if (receiver.entity() instanceof Player p) {
            if (lent != null) ThreadItem.removeBorrowed(p, id());
            if (power != null) {
                power.clear(p);
                POWERS.remove(p.getUniqueId(), this);
            }
            Text.send(p, "<gray>The grafted power fades from you.");
        }
    }

    /** Sneak + F: uses the grafted creature power, if the player has one. Returns true if handled. */
    public static boolean usePower(Player player, long now) {
        AbilityGraft g = POWERS.get(player.getUniqueId());
        if (g == null || g.isSpent() || !g.power.active()) return false;
        if (now < g.readyAt) {
            Text.actionBar(player, "<color:#f2a7ff>" + g.power.display() + "</color> <gray>recovering...");
            return true;
        }
        g.readyAt = now + g.power.cooldownTicks();
        g.power.use(player);
        return true;
    }

    public static void forget(UUID player) {
        POWERS.remove(player);
    }
}
