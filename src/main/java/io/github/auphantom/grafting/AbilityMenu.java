package io.github.auphantom.grafting;

import io.github.auphantom.grafting.graft.Mode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * The ability menu: one textured icon per mode. Click an ability to switch the thread to it.
 * Opened with sneak + left-click while holding the Thread of Grafting, or {@code /graft menu}.
 */
public final class AbilityMenu implements Listener {

    /** Marks our inventories, so clicks in any other chest are never touched. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 21, 23};

    private final ThreadListener threads;

    public AbilityMenu(ThreadListener threads) {
        this.threads = threads;
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 36,
                Text.mm("<gradient:#7b5cff:#d9ccff>Reassembly</gradient> <dark_gray>· choose a graft"));
        holder.inventory = inv;

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setHideTooltip(true);
        filler.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        Mode current = ThreadItem.mode(player.getInventory().getItemInMainHand());
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length && i < SLOTS.length; i++) {
            Mode mode = modes[i];
            ItemStack icon = ThreadItem.create(mode);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Text.mm(mode.tag() + "<b>" + mode.display()).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    line("<dark_gray>" + mode.shape()),
                    line("<gray>" + mode.description()),
                    Component.empty(),
                    line(mode == current ? "<green>● Selected" : "<yellow>Click to select")));
            meta.setEnchantmentGlintOverride(mode == current);
            icon.setItemMeta(meta);
            inv.setItem(SLOTS[i], icon);
        }
        player.openInventory(inv);
        player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 1.6f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length && i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (!ThreadItem.is(hand)) {
                    player.closeInventory();
                    return;
                }
                threads.select(player, hand, modes[i]);
                player.closeInventory();
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private static Component line(String s) {
        return Text.mm(s).decoration(TextDecoration.ITALIC, false);
    }
}
