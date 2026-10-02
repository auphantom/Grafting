package io.github.auphantom.grafting.graft.types;

import io.github.auphantom.grafting.anchor.BlockAnchor;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.Mode;
import io.github.auphantom.grafting.util.Fx;
import io.github.auphantom.grafting.util.Text;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Place + Being: Storage.</b>
 * <p>
 * The storage inside a container (a chest, double chest, barrel, shulker box...) is grafted
 * onto a player's inventory. It becomes extra space they carry everywhere:
 * <ul>
 *   <li><b>sneak + F</b> (or {@code /graft storage}) opens it from any distance or world,</li>
 *   <li>items picked up with a full inventory flow straight into it.</li>
 * </ul>
 * The real container is used, so whatever is stored stays in the chest when the graft ends.
 */
public final class StorageGraft extends Graft {

    private static final Map<UUID, StorageGraft> BY_PLAYER = new HashMap<>();

    private final BlockAnchor container;
    private final EntityAnchor holder;

    public StorageGraft(int id, UUID owner, BlockAnchor container, EntityAnchor holder, long expiresAt) {
        super(id, Mode.STORAGE, owner, container, holder, expiresAt);
        this.container = container;
        this.holder = holder;
    }

    @Override
    public String summary() {
        return "The " + container.describe().split(" at ")[0].toLowerCase() + " is now part of " + holder.describe()
                + "'s inventory: sneak + F to open it anywhere, and overflow lands in it.";
    }

    /** Thread is drawn only when both ends are close; across worlds it would be noise. */
    @Override
    public boolean drawsThread() {
        Location a = container.center(), b = holder.center();
        return a.getWorld().equals(b.getWorld()) && a.distanceSquared(b) < 24 * 24;
    }

    @Override
    public void onStart() {
        if (holder.entity() instanceof Player p) BY_PLAYER.put(p.getUniqueId(), this);
    }

    @Override
    public void onEnd() {
        if (holder.entity() instanceof Player p) {
            BY_PLAYER.remove(p.getUniqueId(), this);
            if (p.getOpenInventory().getTopInventory().equals(inventory())) p.closeInventory();
        }
    }

    /** The container's live inventory (the whole double chest if it is one), or null. */
    public Inventory inventory() {
        if (!container.block().getChunk().isLoaded()) container.block().getChunk().load();
        if (container.block().getState() instanceof Container c) return c.getInventory();
        return null;
    }

    public void open(Player player) {
        Inventory inv = inventory();
        if (inv == null) return;
        // Keep the container's chunk loaded while it is being used from afar.
        container.block().getChunk().addPluginChunkTicket(org.bukkit.Bukkit.getPluginManager().getPlugin("Grafting"));
        player.openInventory(inv);
        player.playSound(player, Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1.3f);
        Location at = player.getLocation().add(0, 1, 0);
        Fx.ring(at, 0.8, Particle.DUST, Fx.dust(color(), 0.9f), 20);
    }

    /** Puts what does not fit in the player's inventory into the grafted storage. */
    public boolean absorb(Player player, Item drop) {
        Inventory inv = inventory();
        if (inv == null) return false;
        ItemStack stack = drop.getItemStack();
        Map<Integer, ItemStack> left = inv.addItem(stack.clone());
        int stored = stack.getAmount() - left.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (stored <= 0) return false;
        if (left.isEmpty()) drop.remove();
        else drop.setItemStack(left.values().iterator().next());
        player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.4f, 0.6f);
        Text.actionBar(player, "<color:#c98b4a>+" + stored + "</color> <gray>into your grafted storage");
        return true;
    }

    public static StorageGraft of(Player player) {
        StorageGraft g = BY_PLAYER.get(player.getUniqueId());
        return g != null && !g.isSpent() && g.isIntact() ? g : null;
    }

    public static void forget(UUID player) {
        BY_PLAYER.remove(player);
    }
}
