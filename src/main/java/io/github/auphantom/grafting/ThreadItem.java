package io.github.auphantom.grafting;

import io.github.auphantom.grafting.graft.Mode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The Thread of Grafting: the one item that lets a player use the ability.
 * <p>
 * It is a plain string, tagged through the persistent data container so it can never
 * be confused with ordinary string. The selected mode is stored on the item itself,
 * and the item's model is swapped to {@code grafting:<mode>} so the resource pack can
 * give every ability its own texture. Without the pack it simply looks like string.
 */
public final class ThreadItem {

    public static final String NAMESPACE = "grafting";

    private static NamespacedKey key;
    private static NamespacedKey modeKey;
    private static boolean customModels = true;

    private ThreadItem() {
    }

    static void init(GraftingPlugin plugin) {
        key = new NamespacedKey(plugin, "thread_of_grafting");
        modeKey = new NamespacedKey(plugin, "mode");
        customModels = plugin.getConfig().getBoolean("resource-pack.custom-models", true);
    }

    public static ItemStack create(Mode mode) {
        ItemStack item = new ItemStack(Material.STRING);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        setMode(item, mode);
        return item;
    }

    public static boolean is(ItemStack item) {
        if (item == null || item.getType() != Material.STRING || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public static Mode mode(ItemStack item) {
        if (!is(item)) return Mode.DISTANCE;
        String id = item.getItemMeta().getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        Mode mode = id == null ? null : Mode.byId(id);
        return mode == null ? Mode.DISTANCE : mode;
    }

    /** Rewrites the item for a mode: stored id, model, name and lore. */
    public static void setMode(ItemStack item, Mode mode) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode.id());
        if (customModels) meta.setItemModel(new NamespacedKey(NAMESPACE, mode.id()));
        meta.displayName(Text.mm("<gradient:#7b5cff:#d9ccff>Thread of Grafting</gradient> <dark_gray>·</dark_gray> "
                + mode.tag() + mode.display()).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(lore("<dark_gray><i>\"Everything can be connected,"));
        lore.add(lore("<dark_gray><i> if you know where to make the cut.\""));
        lore.add(Component.empty());
        lore.add(lore(mode.tag() + "<b>" + mode.display() + "</b> <dark_gray>(" + mode.shape() + ")"));
        lore.add(lore("<gray>" + mode.description()));
        lore.add(Component.empty());
        lore.add(lore("<white>Right-click</white> <gray>a block or being to tie the thread"));
        lore.add(lore("<white>Right-click air</white> <gray>to tie it to yourself"));
        lore.add(lore("<white>Left-click</white> <gray>to switch ability"));
        lore.add(lore("<white>Sneak + left-click</white> <gray>to open the ability menu"));
        lore.add(lore("<white>Sneak + right-click air</white> <gray>to let go"));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    private static Component lore(String line) {
        return Text.mm(line).decoration(TextDecoration.ITALIC, false);
    }
}
