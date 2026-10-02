package io.github.auphantom.grafting.listener;

import io.github.auphantom.grafting.item.Sigil;
import io.github.auphantom.grafting.ui.PathwayBook;

import io.github.auphantom.grafting.GraftingPlugin;
import io.github.auphantom.grafting.graft.GraftManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Keeps every player's pathway sigil in its slot and turns clicks on it into opening the book.
 * <p>
 * The sigil lives in a fixed inventory slot (by default the top-left storage slot, which sits
 * right beside the crafting grid when the inventory is open). It cannot be dropped, moved,
 * swapped, stored or lost on death. Clicking it in the inventory, or right-clicking while
 * holding it, opens the pathway book.
 */
public final class SigilListener implements Listener {

    private final GraftingPlugin plugin;
    private final GraftManager grafts;
    private final PathwayBook book;

    public SigilListener(GraftingPlugin plugin, GraftManager grafts, PathwayBook book) {
        this.plugin = plugin;
        this.grafts = grafts;
        this.book = book;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("pathway-book.sigil", true);
    }

    private int slot() {
        int s = plugin.getConfig().getInt("pathway-book.sigil-slot", 9);
        return Math.max(0, Math.min(35, s));
    }

    private int maxGrafts() {
        return Math.max(1, plugin.getConfig().getInt("max-active-grafts", 5));
    }

    /** Puts the sigil in its slot (moving whatever was there), and removes any stray copies. */
    public void ensure(Player player) {
        if (!enabled() || !player.hasPermission("grafting.use")) return;
        PlayerInventory inv = player.getInventory();
        int target = slot();
        for (int i = 0; i < inv.getSize(); i++) {
            if (i != target && Sigil.is(inv.getItem(i))) inv.setItem(i, null);
        }
        if (Sigil.is(player.getItemOnCursor())) player.setItemOnCursor(null);
        ItemStack current = inv.getItem(target);
        if (Sigil.is(current)) return;
        inv.setItem(target, Sigil.create(player, grafts, maxGrafts()));
        if (current != null && !current.getType().isAir()) {
            // Whatever was in the sigil's slot goes elsewhere, or drops at the player's feet.
            inv.addItem(current).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    /** Called every second: refreshes the live readout in each sigil's tooltip. */
    public void tick() {
        if (!enabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack item = player.getInventory().getItem(slot());
            if (Sigil.is(item)) {
                Sigil.refresh(item, player, grafts, maxGrafts());
                player.getInventory().setItem(slot(), item);
            } else if (player.getOpenInventory().getType() == InventoryType.CRAFTING) {
                ensure(player);
            }
        }
    }

    // ------------------------------------------------------------------ opening the book

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        boolean touchesSigil = Sigil.is(event.getCurrentItem()) || Sigil.is(event.getCursor());
        if (event.getClick() == ClickType.NUMBER_KEY) {
            touchesSigil |= Sigil.is(player.getInventory().getItem(event.getHotbarButton()));
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            touchesSigil |= Sigil.is(player.getInventory().getItemInOffHand());
        }
        if (!touchesSigil) return;
        event.setCancelled(true);
        if (Sigil.is(event.getCurrentItem()) && event.getClick() != ClickType.DROP
                && event.getClick() != ClickType.CONTROL_DROP) {
            // Open on the next tick: opening a window from inside a click on another one is not safe.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) book.open(player);
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (Sigil.is(event.getOldCursor())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !Sigil.is(event.getItem())) return;
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            book.open(event.getPlayer());
        }
    }

    // ------------------------------------------------------------------ keeping it in place

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (Sigil.is(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (Sigil.is(event.getMainHandItem()) || Sigil.is(event.getOffHandItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(Sigil::is);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> ensure(event.getPlayer()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> ensure(event.getPlayer()));
    }
}
