package io.github.auphantom.grafting.item;

import io.github.auphantom.grafting.ui.Bars;

import io.github.auphantom.grafting.GraftingPlugin;
import io.github.auphantom.grafting.item.ThreadItem;
import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The pathway sigil: the Fool pathway's logo, kept in a fixed inventory slot. Clicking it (in
 * the inventory, or right-clicking with it in hand) opens the pathway book. Its tooltip is the
 * "Mystery Arts" readout: pathway, sequence and live stat bars.
 */
public final class Sigil {

    public static final NamespacedKey TOOLTIP_STYLE = new NamespacedKey("grafting", "mystery");
    private static NamespacedKey key;

    private Sigil() {
    }

    public static void init(GraftingPlugin plugin) {
        key = new NamespacedKey(plugin, "pathway_sigil");
    }

    public static boolean is(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public static ItemStack create(Player player, GraftManager grafts, int maxGrafts) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        meta.setItemModel(new NamespacedKey("grafting", "fool_sigil"));
        meta.setMaxStackSize(1);
        meta.setTooltipStyle(TOOLTIP_STYLE);
        item.setItemMeta(meta);
        refresh(item, player, grafts, maxGrafts);
        return item;
    }

    /** Rewrites the readout. Cheap enough to call every second. */
    public static void refresh(ItemStack item, Player player, GraftManager grafts, int maxGrafts) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Bars.line("Mystery Arts", Bars.MYSTERY));
        meta.lore(readout(player, grafts, maxGrafts, true));
        item.setItemMeta(meta);
    }

    /** The stat panel shared by the sigil and the portrait in the book. */
    public static List<Component> readout(Player player, GraftManager grafts, int maxGrafts, boolean hint) {
        List<Graft> owned = grafts.ofOwner(player.getUniqueId());
        Mode selected = selectedMode(player);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text().decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                .append(Component.text("Fool", Bars.FOOL))
                .append(Component.text(" \u00b7 ", Bars.GRAY))
                .append(Component.text("Sequence 1", Bars.WHITE)).build());
        lines.add(Bars.line("Attendant of Mysteries", Bars.GRAY));
        lines.add(Component.empty());

        lines.add(Bars.stat("Threads", owned.size() + "/" + maxGrafts, Bars.CYAN, Bars.WHITE));
        lines.add(Bars.bar(owned.size() / (double) Math.max(1, maxGrafts), 20, Bars.CYAN, Bars.MYSTERY));
        lines.add(Component.empty());

        int known = Mode.values().length;
        lines.add(Bars.stat("Abilities", known + "/" + known, Bars.GREEN_HI, Bars.WHITE));
        lines.add(Bars.bar(1, 20, Bars.GREEN_HI, Bars.GREEN_LO));
        lines.add(Component.empty());

        if (!owned.isEmpty()) {
            long now = grafts.currentTick();
            Graft soonest = owned.get(0);
            for (Graft g : owned) if (g.expiresAt() < soonest.expiresAt()) soonest = g;
            long total = Math.max(1, soonest.expiresAt() - soonest.createdAt());
            long left = Math.max(0, soonest.expiresAt() - now);
            lines.add(Bars.stat("Next to unravel", soonest.name() + " " + (left / 20) + "s", Bars.YELLOW, Bars.WHITE));
            lines.add(Bars.bar(left / (double) total, 20, Bars.YELLOW, Bars.ORANGE));
            lines.add(Component.empty());
        }

        lines.add(Component.text().decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                .append(Component.text("Thread: ", Bars.GRAY))
                .append(Component.text(selected == null ? "none in hand" : selected.display(),
                        selected == null ? Bars.GRAY : Bars.color(selected))).build());
        if (hint) lines.add(Bars.line("Click to open the pathway book", Bars.GRAY));
        return lines;
    }

    /** The ability of the thread the player holds, or the first one in their inventory. */
    public static Mode selectedMode(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (ThreadItem.is(hand)) return ThreadItem.mode(hand);
        for (ItemStack item : player.getInventory().getContents()) {
            if (ThreadItem.is(item)) return ThreadItem.mode(item);
        }
        return null;
    }
}
