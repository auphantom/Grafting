package io.github.auphantom.grafting.ui;

import io.github.auphantom.grafting.GraftingPlugin;
import io.github.auphantom.grafting.beyonder.Beyonder;
import io.github.auphantom.grafting.beyonder.DistanceArt;
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
 * The pathway menu: a plain 5-row chest.
 * <pre>
 *   rows 1-2   the thirteen abilities                 click: set your thread
 *   row 3      [you] . [spirit body] . [4 distance arts]
 *   row 4      .........                              (filler)
 *   row 5      your active threads                    click: sever
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
    private static final int SIZE = 45;
    /** Where each ability sits: two centred rows (9 + 4). */
    private static final int[] ABILITY_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 11, 12, 14, 15};
    private static final int PORTRAIT_SLOT = 18;
    private static final int BODY_SLOT = 20;
    private static final int[] ART_SLOTS = {22, 23, 24, 25};
    private static final int THREAD_ROW = 36;

    private final GraftingPlugin plugin;
    private final GraftManager grafts;
    private final Beyonder beyonder;
    private final ThreadListener threads;

    public PathwayMenu(GraftingPlugin plugin, GraftManager grafts, ThreadListener threads) {
        this.plugin = plugin;
        this.grafts = grafts;
        this.beyonder = grafts.beyonder();
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
        ItemStack pane = filler();
        for (int i = 9; i < THREAD_ROW; i++) inv.setItem(i, pane);
        for (int i = 0; i < modes.length && i < ABILITY_SLOTS.length; i++) {
            inv.setItem(ABILITY_SLOTS[i], abilityIcon(player, modes[i], modes[i] == selected));
        }
        inv.setItem(PORTRAIT_SLOT, portrait(player));
        inv.setItem(BODY_SLOT, spiritBodyIcon(player));
        for (int i = 0; i < ART_SLOTS.length; i++) inv.setItem(ART_SLOTS[i], artIcon(player, DistanceArt.values()[i]));

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

    private ItemStack spiritBodyIcon(Player player) {
        boolean on = beyonder.inSpiritBody(player);
        ItemStack item = new ItemStack(on ? Material.SOUL_LANTERN : Material.SOUL_TORCH);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Spirit Body", Bars.CYAN).decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Bars.line("Your spirit body and main body are one.", Bars.GRAY));
        lore.add(Bars.line("Fly freely. Physical harm cannot touch you,", Bars.WHITE));
        lore.add(Bars.line("but magic still does.", Bars.WHITE));
        lore.add(Bars.line("Using any ability returns you to your body.", Bars.GRAY));
        lore.add(Component.empty());
        lore.add(Bars.stat("Cost", (int) beyonder.useCost("spirit-body") + " spirit", Bars.CYAN, Bars.WHITE));
        lore.add(Component.empty());
        lore.add(Bars.line(on ? "\u25cf Active. Click to return" : "Click to shift into it", on ? Bars.GREEN_HI : Bars.YELLOW));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(on);
        meta.setTooltipStyle(Sigil.TOOLTIP_STYLE);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack artIcon(Player player, DistanceArt art) {
        int level = beyonder.level(player);
        boolean unlocked = art.level() <= level;
        boolean chosen = beyonder.art(player) == art;
        Material icon = !unlocked ? Material.GRAY_DYE : switch (art) {
            case STEP -> Material.LEATHER_BOOTS;
            case GATEWAY -> Material.ENDER_PEARL;
            case ENEMY -> Material.ENDER_EYE;
            case INFINITY -> Material.END_CRYSTAL;
        };
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Distance " + art.level() + ": " + art.display(),
                unlocked ? Bars.color(Mode.DISTANCE) : Bars.GRAY).decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        for (String line : wrap(art.description(), 34)) lore.add(Bars.line(line, Bars.WHITE));
        lore.add(Component.empty());
        String reach = switch (art) {
            case STEP -> (int) beyonder.stepRange(player) + " blocks";
            case GATEWAY -> beyonder.gatewayRange(player) < 0 ? "unlimited" : beyonder.gatewayRange(player) + " blocks";
            case ENEMY -> (int) beyonder.enemyRange(player) + " blocks from you";
            case INFINITY -> "drains " + (int) beyonder.upkeep("infinity") + " spirit/s";
        };
        if (unlocked) lore.add(Bars.stat("Reach", reach, Bars.CYAN, Bars.WHITE));
        lore.add(Bars.stat("Cost", (int) beyonder.useCost(art.id()) + " spirit", Bars.CYAN, Bars.WHITE));
        lore.add(Component.empty());
        if (!unlocked) {
            double need = beyonder.threshold(art.level());
            double have = beyonder.profile(player).travelled();
            lore.add(Bars.line("Locked: distance level " + art.level(), Bars.RED));
            lore.add(Bars.stat("Travelled", (int) have + "/" + (int) need + " blocks", Bars.GRAY, Bars.WHITE));
            lore.add(Bars.bar(have / need, 20, Bars.FOOL, Bars.CYAN));
        } else {
            lore.add(Bars.line(chosen ? "\u25cf Your Distance thread uses this" : "Click to use this art",
                    chosen ? Bars.GREEN_HI : Bars.YELLOW));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(chosen && unlocked);
        meta.setTooltipStyle(Sigil.TOOLTIP_STYLE);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack abilityIcon(Player player, Mode mode, boolean selected) {
        ItemStack icon = ThreadItem.icon(mode);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(mode.display(), Bars.color(mode)).decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Bars.line(mode.shape(), Bars.GRAY));
        if (mode == Mode.DISTANCE) lore.add(Bars.line("Art: " + beyonder.art(player).display(), Bars.FOOL));
        for (String line : wrap(mode.description(), 34)) lore.add(Bars.line(line, Bars.WHITE));
        lore.add(Component.empty());
        if (mode != Mode.DISTANCE) {
            long seconds = plugin.getConfig().getLong("durations." + mode.id(), 60);
            lore.add(Bars.stat("Lasts", mode == Mode.SUPERNOVA ? "until it detonates"
                    : (mode == Mode.RETURN ? seconds + "s or one death" : seconds + "s"), Bars.CYAN, Bars.WHITE));
            double upkeep = beyonder.upkeep(mode.id());
            lore.add(Bars.stat("Spirit", (int) beyonder.useCost(mode.id()) + (upkeep > 0 ? " + " + (int) upkeep + "/s" : "")
                    + " per use", Bars.CYAN, Bars.WHITE));
        }
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

        int ability = indexOf(ABILITY_SLOTS, slot);
        int art = indexOf(ART_SLOTS, slot);
        if (ability >= 0 && ability < Mode.values().length) {
            use(player, Mode.values()[ability]);
        } else if (slot == BODY_SLOT) {
            beyonder.toggleSpiritBody(player);
        } else if (art >= 0) {
            DistanceArt chosen = DistanceArt.values()[art];
            if (beyonder.setArt(player, chosen)) {
                ItemStack thread = findThread(player);
                if (thread != null && ThreadItem.mode(thread) != Mode.DISTANCE) threads.select(player, thread, Mode.DISTANCE);
                else if (thread == null) use(player, Mode.DISTANCE);
                player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f + 0.2f * art);
            }
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

    private static int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) return i;
        return -1;
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
