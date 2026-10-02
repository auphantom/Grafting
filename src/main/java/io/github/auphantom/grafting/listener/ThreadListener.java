package io.github.auphantom.grafting.listener;

import io.github.auphantom.grafting.GraftingPlugin;
import io.github.auphantom.grafting.item.ThreadItem;
import io.github.auphantom.grafting.ui.PathwayMenu;
import io.github.auphantom.grafting.util.Fx;
import io.github.auphantom.grafting.util.Text;

import io.github.auphantom.grafting.anchor.Anchor;
import io.github.auphantom.grafting.anchor.BlockAnchor;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import io.github.auphantom.grafting.beyonder.Beyonder;
import io.github.auphantom.grafting.beyonder.DistanceArt;
import io.github.auphantom.grafting.graft.GraftFactory;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;
import io.github.auphantom.grafting.graft.types.AbilityGraft;
import io.github.auphantom.grafting.graft.types.StorageGraft;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns clicks with the Thread of Grafting into mode switches, anchors, and grafts.
 * <pre>
 *   Left-click               next ability
 *   Sneak + left-click       ability menu
 *   Right-click              tie the thread to what you are looking at (up to tie-range blocks away)
 *                            ... or to yourself, if you are looking at nothing
 *   Sneak + right-click      tie the thread to yourself
 * </pre>
 * Also forwards damage and targeting events to the live grafts.
 */
public final class ThreadListener implements Listener {

    /** How long a half-tied thread waits for its second end (ticks). */
    private static final long PENDING_TIMEOUT = 30 * 20;

    private record Pending(Mode mode, Anchor anchor, long since) {
    }

    private final GraftingPlugin plugin;
    private final GraftManager manager;
    private final GraftFactory factory;
    private final Beyonder beyonder;
    private PathwayMenu menu;
    private final Map<UUID, Pending> pending = new HashMap<>();
    /** Last tick each player used the thread: one physical click can produce several events. */
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Map<UUID, Long> lastSwitch = new HashMap<>();
    /**
     * Left-clicks in the air, waiting to be confirmed. Clients send an arm swing together with
     * every right-click (some before the use packet, some after), and the server reports that
     * swing as a left-click in the air. Such a click only counts if no right-click lands within
     * a couple of ticks on either side of it.
     */
    private final Map<UUID, Long> pendingSwing = new HashMap<>();

    public ThreadListener(GraftingPlugin plugin, GraftManager manager, GraftFactory factory) {
        this.plugin = plugin;
        this.manager = manager;
        this.factory = factory;
        this.beyonder = manager.beyonder();
    }

    public void setMenu(PathwayMenu menu) {
        this.menu = menu;
    }

    // ------------------------------------------------------------------ switching abilities

    /** Switches the thread in {@code hand} to {@code mode}, with feedback. Lets go of a half-tied thread. */
    public void select(Player player, ItemStack hand, Mode mode) {
        if (ThreadItem.borrowedFrom(hand) >= 0) {
            Text.actionBar(player, "<color:#f2a7ff>A borrowed thread is locked to its ability.");
            return;
        }
        ThreadItem.setMode(hand, mode);
        // An in-place edit is not sent to the client while a menu is open, so set the slot again.
        if (hand.equals(player.getInventory().getItemInMainHand())) {
            player.getInventory().setItemInMainHand(hand);
        }
        player.updateInventory();
        if (pending.remove(player.getUniqueId()) != null) {
            Text.send(player, "<gray>You let go of the loose thread to change ability.");
        }
        Text.actionBar(player, "<dark_gray>« </dark_gray>" + mode.tag() + "<b>" + mode.display()
                + "</b><dark_gray> »  <gray>" + shape(player, mode));
        float pitch = 0.7f + 0.1f * mode.ordinal();
        player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, pitch);
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.2f);
        Location feet = player.getLocation().add(0, 0.1, 0);
        Fx.ring(feet, 0.9, Particle.DUST, Fx.dust(mode.color(), 1f), 30);
        Fx.ring(feet.clone().add(0, 0.9, 0), 0.6, Particle.DUST, Fx.dust(mode.color().mixColors(org.bukkit.Color.WHITE), 0.7f), 20);
        Fx.spawn(player.getWorld(), Particle.ENCHANT, player.getLocation().add(0, 1, 0), Fx.scaled(25), 0.4, 0.6, 0.4, 0.6, null);
    }

    /** "Place ➜ Place" etc, adjusted for the player's current Distance art. */
    private String shape(Player player, Mode mode) {
        if (mode != Mode.DISTANCE) return mode.shape();
        DistanceArt art = beyonder.art(player);
        return switch (art) {
            case STEP -> "Step: right-click a block, or type x y z in chat";
            case GATEWAY -> "Gateway: Place ➜ Place";
            case ENEMY -> "Enemy Step: Being ➜ Place";
            case INFINITY -> "Infinity: right-click to raise or lower";
        };
    }

    private boolean debounceSwitch(Player player) {
        long now = manager.currentTick();
        Long last = lastSwitch.put(player.getUniqueId(), now);
        return last != null && now - last < 3;
    }

    private void leftClick(Player player, ItemStack hand) {
        if (debounceSwitch(player)) return;
        if (player.isSneaking()) {
            menu.open(player);
        } else {
            select(player, hand, ThreadItem.mode(hand).next());
        }
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !ThreadItem.is(event.getItem())) return;
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action == Action.PHYSICAL) return;
        event.setCancelled(true); // never place tripwire, open chests, or start breaking blocks
        if (!player.hasPermission("grafting.use")) return;

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (action == Action.LEFT_CLICK_AIR) {
                Long used = lastUse.get(player.getUniqueId());
                if (used == null || manager.currentTick() - used > 3) {
                    pendingSwing.put(player.getUniqueId(), manager.currentTick());
                }
            } else {
                leftClick(player, event.getItem());
            }
            return;
        }
        pendingSwing.remove(player.getUniqueId());
        if (recentlyUsed(player)) return;
        Mode mode = ThreadItem.mode(event.getItem());
        if (player.isSneaking()) {
            tie(player, mode, new EntityAnchor(player));
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            tie(player, mode, BlockAnchor.of(event.getClickedBlock()));
            return;
        }
        tie(player, mode, lookTarget(player));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ThreadItem.is(hand)) return;
        event.setCancelled(true); // no leashing, trading, or feeding with the thread
        pendingSwing.remove(player.getUniqueId());
        if (!player.hasPermission("grafting.use") || recentlyUsed(player)) return;
        Mode mode = ThreadItem.mode(hand);
        if (player.isSneaking()) {
            tie(player, mode, new EntityAnchor(player));
        } else if (event.getRightClicked() instanceof LivingEntity target) {
            tie(player, mode, new EntityAnchor(target));
        }
    }

    /** Left-clicking a being with the thread switches ability instead of punching it. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPunch(EntityDamageByEntityEvent event) {
        if (GraftManager.isDealingDamage()) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ThreadItem.is(hand)) return;
        event.setCancelled(true);
        if (player.hasPermission("grafting.use")) leftClick(player, hand);
    }

    /** Creative players would otherwise break the block they left-click to switch ability. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (ThreadItem.is(event.getPlayer().getInventory().getItemInMainHand())) event.setCancelled(true);
    }

    /** Hint the controls whenever the thread is picked up in the hotbar. */
    @EventHandler
    public void onHold(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (!ThreadItem.is(item)) return;
        Mode mode = ThreadItem.mode(item);
        Text.actionBar(event.getPlayer(), mode.tag() + "<b>" + mode.display()
                + "</b> <dark_gray>·</dark_gray> <gray>left-click to switch, sneak + left-click for the menu");
    }

    private boolean recentlyUsed(Player player) {
        long now = manager.currentTick();
        Long last = lastUse.put(player.getUniqueId(), now);
        return last != null && now - last <= 2;
    }

    /** What the player is looking at, up to {@code tie-range} blocks away. Nothing -> themselves. */
    private Anchor lookTarget(Player player) {
        double range = plugin.getConfig().getDouble("tie-range", 48);
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER,
                true, 0.2, e -> e instanceof LivingEntity && !e.equals(player)
                        && !(e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR));
        if (hit != null) {
            Entity entity = hit.getHitEntity();
            if (entity instanceof LivingEntity living) return new EntityAnchor(living);
            Block block = hit.getHitBlock();
            if (block != null) return BlockAnchor.of(block);
        }
        return new EntityAnchor(player);
    }

    // ------------------------------------------------------------------ tying

    private void tie(Player player, Mode mode, Anchor anchor) {
        UUID id = player.getUniqueId();
        if (mode == Mode.DISTANCE && !pending.containsKey(id)) {
            DistanceArt art = beyonder.art(player);
            if (art == DistanceArt.STEP) {
                Location target = anchor instanceof BlockAnchor b ? b.standingSpot()
                        : anchor instanceof EntityAnchor e && !e.entity().equals(player) ? e.entity().getLocation() : null;
                if (target == null) {
                    Text.send(player, "<gray>Right-click a block (or type <white>x y z</white> in chat) to choose where your next step lands.");
                    return;
                }
                target.setYaw(player.getLocation().getYaw());
                beyonder.armStep(player, target);
                return;
            }
            if (art == DistanceArt.INFINITY) {
                beyonder.setInfinity(player, !beyonder.infinity(player));
                return;
            }
        }
        Pending first = pending.get(id);
        long now = manager.currentTick();

        if (first == null || first.mode() != mode || now - first.since() > PENDING_TIMEOUT || !first.anchor().isIntact()) {
            if (!GraftFactory.accepts(factory.firstEnd(player, mode), anchor)) {
                fail(player, mode.tag() + mode.display() + "</color> <gray>starts from " + factory.firstEnd(player, mode).article()
                        + ", not " + Text.esc(anchor.describe()) + ".");
                return;
            }
            pending.put(id, new Pending(mode, anchor, now));
            Text.send(player, mode.tag() + mode.display() + "<dark_gray>:</dark_gray> <gray>thread tied to <white>"
                    + Text.esc(anchor.describe()) + "</white>. Now choose " + factory.secondEnd(player, mode).article() + ".");
            Location at = anchor.center();
            Fx.spawn(at.getWorld(), Particle.ENCHANT, at, Fx.scaled(50), 0.5, 0.5, 0.5, 0.8, null);
            Fx.sphere(at, 0.7, Particle.DUST, Fx.dust(mode.color(), 0.8f), 30);
            Fx.line(player.getEyeLocation().subtract(0, 0.4, 0), at, Fx.dust(mode.color(), 0.6f), 0.25);
            player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
            return;
        }
        if (first.anchor().sameAs(anchor)) {
            Text.send(player, "<gray>The thread is already tied there. <dark_gray>(Left-click to switch ability and let go.)");
            return;
        }
        if (!GraftFactory.accepts(factory.secondEnd(player, mode), anchor)) {
            fail(player, mode.tag() + mode.display() + "</color> <gray>must end on " + factory.secondEnd(player, mode).article()
                    + ", not " + Text.esc(anchor.describe()) + ".");
            return;
        }

        pending.remove(id);
        GraftFactory.Result result = factory.create(player, mode, first.anchor(), anchor);
        if (result.graft() == null) {
            if (result.error() != null) fail(player, "<red>" + Text.esc(result.error()));
            return;
        }
        manager.add(result.graft());
        Text.send(player, result.graft().mode().tag() + result.graft().name() + "</color> <dark_gray>#"
                + result.graft().id() + "</dark_gray> <gray>"
                + Text.esc(first.anchor().describe()) + " " + mode.tag() + "⟶</color> " + Text.esc(anchor.describe()));
        Text.send(player, "<dark_gray><i>" + Text.esc(result.graft().summary()));
    }

    private static void fail(Player player, String message) {
        Text.send(player, message);
        player.playSound(player, Sound.BLOCK_WOOL_BREAK, 1f, 0.6f);
    }

    /** Called every tick: reminds players of a half-tied thread and lets it time out. */
    public void tickPending() {
        long now = manager.currentTick();
        pendingSwing.entrySet().removeIf(entry -> {
            if (now - entry.getValue() < 2) return false;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (ThreadItem.is(hand)) leftClick(player, hand);
            }
            return true;
        });
        pending.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            Pending p = entry.getValue();
            if (player == null) return true;
            if (now - p.since() > PENDING_TIMEOUT || !p.anchor().isIntact()) {
                Text.actionBar(player, "<gray>The loose thread slips away.");
                return true;
            }
            if (now % 10 == 0) {
                Text.actionBar(player, p.mode().tag() + "<b>" + p.mode().display() + "</b> <dark_gray>»</dark_gray> <gray>"
                        + Text.esc(p.anchor().describe()) + " " + p.mode().tag() + "⟶</color> <white>?");
            }
            if (now % 3 == 0) drawLoose(player, p);
            return false;
        });
    }

    /** A loose helix from the tied end to the player's hand. Only the holder sees it. */
    private static void drawLoose(Player player, Pending p) {
        Location from = p.anchor().center();
        Location to = player.getEyeLocation().subtract(0, 0.4, 0).add(player.getLocation().getDirection().multiply(0.5));
        if (!from.getWorld().equals(to.getWorld()) || from.distanceSquared(to) > 64 * 64) return;
        double length = from.distance(to);
        int points = (int) Math.max(2, Math.ceil(length / 0.3));
        org.bukkit.util.Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / points);
        Particle.DustOptions dust = Fx.dust(p.mode().color(), 0.45f);
        Location cursor = from.clone();
        for (int i = 0; i <= points; i++) {
            // A slight sag, like a real slack thread.
            double t = (double) i / points;
            Location at = cursor.clone().subtract(0, Math.sin(Math.PI * t) * Math.min(1.5, length * 0.06), 0);
            player.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
            cursor.add(step);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pending.remove(id);
        lastUse.remove(id);
        lastSwitch.remove(id);
        pendingSwing.remove(id);
        AbilityGraft.forget(id);
        StorageGraft.forget(id);
    }

    // ------------------------------------------------------------------ Step by chat, sneak + F, overflow

    /** Typing "x y z" (optionally a world) in chat while holding a Step thread arms the step. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ThreadItem.is(hand) || ThreadItem.mode(hand) != Mode.DISTANCE) return;
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (!text.matches("~?-?\\d+(\\.\\d+)?\\s+~?-?\\d+(\\.\\d+)?\\s+~?-?\\d+(\\.\\d+)?(\\s+\\S+)?")) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (beyonder.art(player) != DistanceArt.STEP) beyonder.setArt(player, DistanceArt.STEP);
            Location target = Beyonder.parseTarget(player, text.split("\\s+"));
            if (target == null) Text.send(player, "<red>Unknown place: " + Text.esc(text));
            else beyonder.armStep(player, target);
        });
    }

    /** Sneak + F: use a grafted creature power, else open grafted storage. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapKey(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (AbilityGraft.usePower(player, manager.currentTick())) {
            event.setCancelled(true);
            return;
        }
        StorageGraft storage = StorageGraft.of(player);
        if (storage != null) {
            event.setCancelled(true);
            storage.open(player);
        }
    }

    /** Items that do not fit in a full inventory flow into grafted storage. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        if (event.getRemaining() < event.getItem().getItemStack().getAmount()) return;
        StorageGraft storage = StorageGraft.of(event.getPlayer());
        if (storage != null && storage.absorb(event.getPlayer(), event.getItem())) event.setCancelled(true);
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        manager.dispatchTarget(event);
    }
}
