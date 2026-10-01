package io.github.auphantom.grafting;

import io.github.auphantom.grafting.anchor.Anchor;
import io.github.auphantom.grafting.anchor.BlockAnchor;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import io.github.auphantom.grafting.graft.GraftFactory;
import io.github.auphantom.grafting.graft.GraftManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns clicks with the Thread of Grafting into anchors, and pairs of anchors into grafts.
 * Also forwards damage events to the live grafts.
 */
public final class ThreadListener implements Listener {

    /** How long a half-tied thread waits for its second end (ticks). */
    private static final long PENDING_TIMEOUT = 30 * 20;

    private record Pending(Anchor anchor, long since) {
    }

    private final GraftManager manager;
    private final GraftFactory factory;
    private final Map<UUID, Pending> pending = new HashMap<>();
    /** Last tick each player clicked an entity: the client follows that with a stray "use item" click. */
    private final Map<UUID, Long> lastEntityClick = new HashMap<>();

    public ThreadListener(GraftManager manager, GraftFactory factory) {
        this.manager = manager;
        this.factory = factory;
    }

    // ------------------------------------------------------------------ selecting

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !ThreadItem.is(event.getItem())) return;
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true); // never place the string as tripwire, never open chests, etc.
        if (!player.hasPermission("grafting.use")) return;

        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null) tie(player, BlockAnchor.of(block));
            return;
        }
        Long last = lastEntityClick.get(player.getUniqueId());
        if (last != null && manager.currentTick() - last <= 2) return;
        if (player.isSneaking()) {
            if (pending.remove(player.getUniqueId()) != null) {
                Text.send(player, "<gray>You let go of the thread.");
                player.playSound(player, Sound.BLOCK_WOOL_BREAK, 1f, 0.8f);
            }
            return;
        }
        tie(player, new EntityAnchor(player));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!ThreadItem.is(player.getInventory().getItemInMainHand())) return;
        if (!(event.getRightClicked() instanceof LivingEntity target)) return;
        event.setCancelled(true); // no leashing, trading, or feeding with the thread
        if (!player.hasPermission("grafting.use")) return;
        lastEntityClick.put(player.getUniqueId(), manager.currentTick());
        tie(player, new EntityAnchor(target));
    }

    private void tie(Player player, Anchor anchor) {
        UUID id = player.getUniqueId();
        Pending first = pending.get(id);
        long now = manager.currentTick();

        if (first == null || now - first.since() > PENDING_TIMEOUT || !first.anchor().isIntact()) {
            pending.put(id, new Pending(anchor, now));
            Text.send(player, "<gray>Thread tied to <white>" + Text.esc(anchor.describe())
                    + "</white>. Now choose what to graft it onto.");
            sparkle(anchor.center());
            player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
            return;
        }
        if (first.anchor().sameAs(anchor)) {
            Text.send(player, "<gray>The thread is already tied there. <dark_gray>(Sneak + right-click the air to let go.)");
            return;
        }

        pending.remove(id);
        GraftFactory.Result result = factory.create(id, first.anchor(), anchor);
        if (result.graft() == null) {
            Text.send(player, "<red>" + Text.esc(result.error()));
            player.playSound(player, Sound.BLOCK_WOOL_BREAK, 1f, 0.6f);
            return;
        }
        manager.add(result.graft());
        Text.send(player, "<light_purple>" + result.graft().name() + "</light_purple> <dark_gray>#"
                + result.graft().id() + "</dark_gray> <gray>"
                + Text.esc(first.anchor().describe()) + " <dark_purple>⟶</dark_purple> " + Text.esc(anchor.describe()));
        Text.send(player, "<dark_gray><i>" + Text.esc(result.graft().summary()));
    }

    /** Called from the plugin's tick: reminds players of a half-tied thread and lets it time out. */
    void tickPending() {
        long now = manager.currentTick();
        pending.entrySet().removeIf(entry -> {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            Pending p = entry.getValue();
            if (player == null) return true;
            if (now - p.since() > PENDING_TIMEOUT || !p.anchor().isIntact()) {
                Text.actionBar(player, "<gray>The loose thread slips away.");
                return true;
            }
            if (now % 10 == 0) {
                Text.actionBar(player, "<light_purple>Thread of Grafting</light_purple> <dark_gray>»</dark_gray> <gray>"
                        + Text.esc(p.anchor().describe()) + " <dark_purple>⟶</dark_purple> <white>?");
                drawLoose(player, p.anchor());
            }
            return false;
        });
    }

    /** A faint thread from the tied end to the player's hand. */
    private static void drawLoose(Player player, Anchor anchor) {
        Location from = anchor.center();
        Location to = player.getEyeLocation().subtract(0, 0.4, 0);
        if (!from.getWorld().equals(to.getWorld()) || from.distanceSquared(to) > 48 * 48) return;
        Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(0xd9ccff), 0.4f);
        double length = from.distance(to);
        int points = (int) Math.max(1, Math.ceil(length / 0.6));
        org.bukkit.util.Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / points);
        Location cursor = from.clone();
        for (int i = 0; i <= points; i++) {
            player.spawnParticle(Particle.DUST, cursor, 1, 0, 0, 0, 0, dust);
            cursor.add(step);
        }
    }

    private static void sparkle(Location at) {
        at.getWorld().spawnParticle(Particle.ENCHANT, at, 30, 0.4, 0.4, 0.4, 0.5);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
        lastEntityClick.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ grafts reacting to the world

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        manager.dispatchDamage(event, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLateDamage(EntityDamageEvent event) {
        manager.dispatchDamage(event, true);
    }
}
