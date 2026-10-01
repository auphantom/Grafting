package io.github.auphantom.grafting;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The Thread of Grafting: the one item that lets a player use the ability.
 * It is a plain string, tagged through the persistent data container so it can
 * never be confused with ordinary string (renaming one in an anvil does nothing).
 */
public final class ThreadItem {

    private static NamespacedKey key;

    private ThreadItem() {
    }

    static void init(GraftingPlugin plugin) {
        key = new NamespacedKey(plugin, "thread_of_grafting");
    }

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.STRING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm("<gradient:#7b5cff:#d9ccff>Thread of Grafting</gradient>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                lore("<dark_gray><i>\"Everything can be connected,"),
                lore("<dark_gray><i> if you know where to make the cut.\""),
                Component.empty(),
                lore("<gray>Right-click a <white>block</white> or a <white>being</white> to tie the thread."),
                lore("<gray>Right-click the <white>air</white> to tie it to yourself."),
                lore("<gray>Tie a second end to <light_purple>graft</light_purple> the two together."),
                lore("<gray>Sneak + right-click the air to let go."),
                Component.empty(),
                lore("<dark_purple>Place + Place</dark_purple> <dark_gray>»</dark_gray> <gray>Distance"),
                lore("<dark_purple>Being + Being</dark_purple> <dark_gray>»</dark_gray> <gray>Fate"),
                lore("<dark_purple>Place + Being</dark_purple> <dark_gray>»</dark_gray> <gray>Nature"),
                lore("<dark_purple>Being + Place</dark_purple> <dark_gray>»</dark_gray> <gray>Death & Return")));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean is(ItemStack item) {
        if (item == null || item.getType() != Material.STRING || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static Component lore(String line) {
        return Text.mm(line).decoration(TextDecoration.ITALIC, false);
    }
}
