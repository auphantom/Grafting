package io.github.auphantom.grafting.ui;

import io.github.auphantom.grafting.GraftingPlugin;
import io.github.auphantom.grafting.util.Text;
import io.github.auphantom.grafting.item.ThreadItem;
import io.github.auphantom.grafting.listener.ThreadListener;
import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;
import io.github.auphantom.grafting.item.Sigil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
import java.util.UUID;

/**
 * The pathway book: an open book painted over a 6-row chest GUI by the resource pack.
 * <pre>
 *   left page                         right page
 *   +---------------------------+     +---------------------------+
 *   |      Attendant of         |     |        Reassembly         |
 *   |  [portrait]  Fool         |     |   [D] [F] [N]             |
 *   |              Seq. 1       |     |   [R] [E] [X]   (sigil)   |
 *   |  Threads 2/5              |     |   [G] [P] [S]             |
 *   |  Active threads           |     |   Selected: Supernova     |
 *   |  [#][#][#][#]             |     |   Click: take a thread    |
 *   |  [#]                      |     |   Shift: set your thread  |
 *   +---------------------------+     +---------------------------+
 * </pre>
 * Every slot is a real chest slot, so all items keep their normal vanilla tooltips; only the
 * background and the page text are painted. Without the pack the same menu still works, it
 * just looks like a chest.
 */
public final class PathwayBook implements Listener {

    /** Marks our inventories so clicks in any other chest are never touched. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, Integer> abilities = new HashMap<>();
        private final Map<Integer, Integer> threads = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Ability icons: rows 1-3, columns 5-7 of the chest (right page). */
    private static final int[] ABILITY_SLOTS = {14, 15, 16, 23, 24, 25, 32, 33, 34};
    /** Active threads: rows 4-5, columns 0-3 (bottom of the left page). */
    private static final int[] THREAD_SLOTS = {36, 37, 38, 39, 45, 46, 47, 48};
    private static final int PORTRAIT_SLOT = 10;

    private static final TextColor INK = TextColor.color(0x3b2412);
    private static final TextColor INK_SOFT = TextColor.color(0x7a5532);
    private static final TextColor TAB_TEXT = TextColor.color(0xfbe7c6);
    private static final TextColor FOOL_INK = TextColor.color(0x5b2ea6);

    private final GraftingPlugin plugin;
    private final GraftManager grafts;
    private final ThreadListener threads;
    private final Map<UUID, Holder> open = new HashMap<>();

    public PathwayBook(GraftingPlugin plugin, GraftManager grafts, ThreadListener threads) {
        this.plugin = plugin;
        this.grafts = grafts;
        this.threads = threads;
    }

    private int maxGrafts() {
        return Math.max(1, plugin.getConfig().getInt("max-active-grafts", 5));
    }

    // ------------------------------------------------------------------ opening

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54, title(player));
        holder.inventory = inv;
        fill(player, holder);
        player.openInventory(inv);
        open.put(player.getUniqueId(), holder);
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.9f);
        player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.4f, 1.6f);
    }

    /**
     * Re-renders an open book (after taking a thread or severing a graft). The page text lives
     * in the window title, and a title can only be changed by opening a new window, so the book
     * is reopened. The client keeps the mouse where it was.
     */
    private void redraw(Player player, Holder holder) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            Holder next = new Holder();
            next.inventory = Bukkit.createInventory(next, 54, title(player));
            fill(player, next);
            open.put(player.getUniqueId(), next);
            player.openInventory(next.inventory);
        });
    }

    private Component title(Player player) {
        List<Graft> owned = grafts.ofOwner(player.getUniqueId());
        Mode selected = Sigil.selectedMode(player);
        GuiTitle t = new GuiTitle().book();
        t.centered(88, 4, "PATHWAY", TAB_TEXT, true);

        // Left page (inner width ~74px, x 9..83).
        t.centered(46, 17, "Fool Pathway", FOOL_INK);
        t.text(52, 36, "Seq.", INK_SOFT);
        t.text(52, 45, "One", INK);
        t.centered(46, 60, "Attendant of", INK);
        t.centered(46, 69, "Mysteries", INK);
        t.centered(46, 79, "Threads " + owned.size() + "/" + maxGrafts(), owned.isEmpty() ? INK_SOFT : INK);

        // Right page (inner width ~74px, x 93..167).
        t.centered(130, 17, "Reassembly", INK);
        String sel = selected == null ? "No thread" : selected.display();
        t.centered(130, 93, Glyphs.fit(sel, 72), selected == null ? INK_SOFT : Bars.ink(selected));
        t.centered(130, 103, "Click: take one", INK_SOFT);
        t.centered(130, 112, "Shift: switch", INK_SOFT);
        return t.build();
    }

    private void fill(Player player, Holder holder) {
        Inventory inv = holder.inventory;
        holder.abilities.clear();
        holder.threads.clear();

        inv.setItem(PORTRAIT_SLOT, portrait(player));

        Mode selected = Sigil.selectedMode(player);
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length && i < ABILITY_SLOTS.length; i++) {
            inv.setItem(ABILITY_SLOTS[i], abilityIcon(modes[i], modes[i] == selected));
            holder.abilities.put(ABILITY_SLOTS[i], modes[i].ordinal());
        }

        List<Graft> owned = grafts.ofOwner(player.getUniqueId());
        for (int i = 0; i < owned.size() && i < THREAD_SLOTS.length; i++) {
            inv.setItem(THREAD_SLOTS[i], threadIcon(owned.get(i)));
            holder.threads.put(THREAD_SLOTS[i], owned.get(i).id());
        }
    }

    // ------------------------------------------------------------------ icons

    private ItemStack portrait(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        // The player's own skin, read from their profile (offline servers have none: plain head).
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
        long seconds = plugin.getConfig().getLong("durations." + mode.id(), mode == Mode.SUPERNOVA ? 0 : 60);
        lore.add(Bars.stat("Lasts", mode == Mode.SUPERNOVA ? "until it detonates"
                : (mode == Mode.RETURN ? seconds + "s or one death" : seconds + "s"), Bars.CYAN, Bars.WHITE));
        lore.add(Component.empty());
        lore.add(Bars.line(selected ? "\u25cf Your thread is set to this" : "Click to take a thread of " + mode.display(),
                selected ? Bars.GREEN_HI : Bars.YELLOW));
        lore.add(Bars.line("Shift-click to switch your thread to it", Bars.GRAY));
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
        long now = grafts.currentTick();
        long total = Math.max(1, graft.expiresAt() - graft.createdAt());
        long left = Math.max(0, graft.expiresAt() - now);
        List<Component> lore = new ArrayList<>();
        lore.add(Bars.line(Glyphs.fit(graft.first().describe(), 180), Bars.WHITE));
        lore.add(Bars.line("  \u27f6 " + Glyphs.fit(graft.second().describe(), 170), Bars.WHITE));
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

        Integer ability = holder.abilities.get(slot);
        if (ability != null) {
            Mode mode = Mode.values()[ability];
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                setHeld(player, mode);
            } else {
                take(player, mode);
            }
            redraw(player, holder);
            return;
        }
        Integer graftId = holder.threads.get(slot);
        if (graftId != null) {
            for (Graft g : grafts.ofOwner(player.getUniqueId())) {
                if (g.id() == graftId) grafts.end(g, "was severed");
            }
            player.playSound(player, Sound.ENTITY_SHEEP_SHEAR, 1f, 1.2f);
            redraw(player, holder);
        }
    }

    /** Hands the player a thread already set to {@code mode} (or re-sets the one they have). */
    private void take(Player player, Mode mode) {
        if (!player.hasPermission("grafting.use")) return;
        ItemStack existing = findThread(player);
        if (existing != null) {
            threads.select(player, existing, mode);
            Text.send(player, "<gray>Your thread is now set to " + mode.tag() + mode.display() + "</color>.");
            return;
        }
        if (!player.hasPermission("grafting.take")) {
            Text.send(player, "<red>You do not have a Thread of Grafting.");
            return;
        }
        ItemStack thread = ThreadItem.create(mode);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(thread);
        if (!leftover.isEmpty()) {
            Text.send(player, "<red>Your inventory is full.");
            return;
        }
        player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.8f);
        player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        Text.send(player, "<gray>You draw a " + mode.tag() + "Thread of " + mode.display() + "</color> from the book.");
    }

    private void setHeld(Player player, Mode mode) {
        ItemStack thread = findThread(player);
        if (thread == null) {
            take(player, mode);
            return;
        }
        threads.select(player, thread, mode);
    }

    /** The thread in the main hand, else the first one in the inventory. Only one per player. */
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

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            open.remove(event.getPlayer().getUniqueId());
        }
    }

    /** Called every second: keeps the countdown bars in open books moving. */
    public void tick() {
        for (Map.Entry<UUID, Holder> e : new ArrayList<>(open.entrySet())) {
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null || !(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder)
                    || holder != e.getValue()) {
                open.remove(e.getKey());
                continue;
            }
            // Only the items: their tooltips carry the live bars. The title does not need to change.
            List<Graft> owned = grafts.ofOwner(player.getUniqueId());
            for (int i = 0; i < THREAD_SLOTS.length; i++) {
                if (i < owned.size()) {
                    holder.inventory.setItem(THREAD_SLOTS[i], threadIcon(owned.get(i)));
                    holder.threads.put(THREAD_SLOTS[i], owned.get(i).id());
                } else if (holder.threads.remove(THREAD_SLOTS[i]) != null) {
                    // A thread unravelled while the book was open: repaint the counts too.
                    redraw(player, holder);
                    break;
                }
            }
            holder.inventory.setItem(PORTRAIT_SLOT, portrait(player));
        }
    }
}
