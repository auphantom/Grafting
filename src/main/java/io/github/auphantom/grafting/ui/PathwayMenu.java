package io.github.auphantom.grafting.ui;

import io.github.auphantom.grafting.GraftingPlugin;
import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;
import io.github.auphantom.grafting.item.Sigil;
import io.github.auphantom.grafting.item.ThreadItem;
import io.github.auphantom.grafting.listener.ThreadListener;
import io.github.auphantom.grafting.util.Text;
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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pathway menu: a plain 3-row chest.
 * <pre>
 *   row 1   the nine abilities          click: take / set your thread
 *   row 2   ......... [you] .........   your stats
 *   row 3   your active threads         click: sever
 * </pre>
 */
public final class PathwayMenu implements Listener {

    /** Marks our inventories so clicks in any other chest are never touched. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, Integer> threads = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static final String TITLE = "Fool Pathway - Reassembly";
    private static final int SIZE = 27;
    private static final int PORTRAIT_SLOT = 13;
    private static final int THREAD_ROW = 18;

    private final GraftingPlugin plugin;
    private final GraftManager grafts;
    private final ThreadListener threads;

    public PathwayMenu(GraftingPlugin plugin, GraftManager grafts, ThreadListener threads) {
        this.plugin = plugin;
        this.grafts = grafts;
        this.threads = threads;
    }

    private int maxGrafts() {
        return Math.max(1, plugin.getConfig().getInt("max-active-grafts", 5));
    }

    public void open(Player player) {
        Holder holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, SIZE, Component.text(TITLE));
        fill(player, holder);
        player.openInventory(holder.inventory);
        player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.4f, 1.6f);
    }

    /** Redraws every slot in place; a chest's items can change while it stays open. */
    private void fill(Player player, Holder holder) {
        Inventory inv = holder.inventory;
        inv.clear();
        holder.threads.clear();

        Mode selected = Sigil.selectedMode(player);
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length && i < 9; i++) {
            inv.setItem(i, abilityIcon(modes[i], modes[i] == selected));
        }

        ItemStack pane = filler();
        for (int i = 9; i < THREAD_ROW; i++) inv.setItem(i, pane);
        inv.setItem(PORTRAIT_SLOT, portrait(player));

        List<Graft> owned = grafts.ofOwner(player.getUniqueId());
        for (int i = 0; i < owned.size() && i < 9; i++) {
            inv.setItem(THREAD_ROW + i, threadIcon(owned.get(i)));
            holder.threads.put(THREAD_ROW + i, owned.get(i).id());
        }
    }

    // ------------------------------------------------------------------ icons

    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setHideTooltip(true);
        pane.setItemMeta(meta);
        return pane;
    }

    private ItemStack portrait(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setPlayerProfile(player.getPlayerProfile());
        meta.displayName(Bars.line("Mystery Arts", Bars.MYSTERY));
        meta.lore(Sigil.readout(player, grafts, maxGrafts(), false));
        meta.setTooltipStyle(Sigil.TOOLTIP_STYLE);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack abilityIcon(Mode mode, boolean selected) {
        ItemStack icon = ThreadItem.icon(mode);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(mode.display(), Bars.color(mode)).decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Bars.line(mode.shape(), Bars.GRAY));
        for (String line : wrap(mode.description(), 34)) lore.add(Bars.line(line, Bars.WHITE));
        lore.add(Component.empty());
        long seconds = plugin.getConfig().getLong("durations." + mode.id(), 60);
        lore.add(Bars.stat("Lasts", mode == Mode.SUPERNOVA ? "until it detonates"
                : (mode == Mode.RETURN ? seconds + "s or one death" : seconds + "s"), Bars.CYAN, Bars.WHITE));
        lore.add(Component.empty());
        lore.add(Bars.line(selected ? "\u25cf Your thread is set to this" : "Click to use " + mode.display(),
                selected ? Bars.GREEN_HI : Bars.YELLOW));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(selected);
        meta.setTooltipStyle(Sigil.TOOLTIP_STYLE);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack threadIcon(Graft graft) {
        ItemStack icon = ThreadItem.icon(graft.mode());
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(graft.name() + " #" + graft.id(), Bars.color(graft.mode()))
                .decoration(TextDecoration.ITALIC, false));
        long total = Math.max(1, graft.expiresAt() - graft.createdAt());
        long left = Math.max(0, graft.expiresAt() - grafts.currentTick());
        List<Component> lore = new ArrayList<>();
        lore.add(Bars.line(graft.first().describe(), Bars.WHITE));
        lore.add(Bars.line("  \u27f6 " + graft.second().describe(), Bars.WHITE));
        lore.add(Component.empty());
        lore.add(Bars.stat("Unravels in", (left / 20) + "s", Bars.YELLOW, Bars.WHITE));
        lore.add(Bars.bar(left / (double) total, 20, Bars.YELLOW, Bars.ORANGE));
        lore.add(Component.empty());
        lore.add(Bars.line("Click to sever this thread", Bars.RED));
        meta.lore(lore);
        meta.setTooltipStyle(Sigil.TOOLTIP_STYLE);
        icon.setItemMeta(meta);
        return icon;
    }

    private static List<String> wrap(String text, int max) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > max && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();

        if (slot < Mode.values().length) {
            use(player, Mode.values()[slot]);
        } else {
            Integer graftId = holder.threads.get(slot);
            if (graftId == null) return;
            for (Graft g : grafts.ofOwner(player.getUniqueId())) {
                if (g.id() == graftId) grafts.end(g, "was severed");
            }
            player.playSound(player, Sound.ENTITY_SHEEP_SHEAR, 1f, 1.2f);
        }
        Bukkit.getScheduler().runTask(plugin, () -> fill(player, holder));
    }

    /** Sets the player's thread to {@code mode}, or hands them one if they have none. */
    private void use(Player player, Mode mode) {
        ItemStack existing = findThread(player);
        if (existing != null) {
            threads.select(player, existing, mode);
            return;
        }
        if (!player.hasPermission("grafting.take")) {
            Text.send(player, "<red>You do not have a Thread of Grafting.");
            return;
        }
        if (!player.getInventory().addItem(ThreadItem.create(mode)).isEmpty()) {
            Text.send(player, "<red>Your inventory is full.");
            return;
        }
        player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.8f);
        Text.send(player, "<gray>You draw a " + mode.tag() + "Thread of " + mode.display() + "</color>.");
    }

    /** The thread in the main hand, else the first one in the inventory. */
    private static ItemStack findThread(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (ThreadItem.is(hand)) return hand;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (ThreadItem.is(item)) return item;
        }
        return null;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    /** Called every second: keeps the countdowns in open menus moving. */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) {
                fill(player, holder);
            }
        }
    }
}
