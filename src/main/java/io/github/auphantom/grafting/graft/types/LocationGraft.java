package io.github.auphantom.grafting.graft.types;

import io.github.auphantom.grafting.anchor.BlockAnchor;
import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.Mode;
import io.github.auphantom.grafting.util.Fx;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Structure;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

/**
 * <b>Place + Place: Location.</b>
 * <p>
 * Two whole areas trade places. Graft your base onto a spot in the End and the End spot
 * replaces your base in the Overworld (and your base appears in the End) until the thread
 * is cut. Blocks, chests and their contents all move; anything changed while grafted moves
 * back with it, because ending the graft simply swaps the two areas again.
 * <p>
 * Every live swap is written to {@code locations.yml}, so a crash or a restart swaps the
 * areas back the next time the plugin starts.
 */
public final class LocationGraft extends Graft {

    private static final Random RANDOM = new Random();

    private final Plugin plugin;
    private final BlockAnchor a;
    private final BlockAnchor b;
    private final int half;
    private final int down;
    private final int up;
    private boolean swapped;

    public LocationGraft(int id, UUID owner, BlockAnchor a, BlockAnchor b, long expiresAt,
                         Plugin plugin, int half, int down, int up) {
        super(id, Mode.LOCATION, owner, a, b, expiresAt);
        this.plugin = plugin;
        this.a = a;
        this.b = b;
        this.half = half;
        this.down = down;
        this.up = up;
    }

    @Override
    public String name() {
        return "Location";
    }

    @Override
    public String summary() {
        int w = half * 2 + 1;
        return "A " + w + "x" + w + " area around each end has traded places with the other, until the thread is cut.";
    }

    /** The anchor blocks themselves move, so only a world unloading can break this graft. */
    @Override
    public boolean isIntact() {
        return Bukkit.getWorld(a.block().getWorld().getUID()) != null && Bukkit.getWorld(b.block().getWorld().getUID()) != null;
    }

    /** The lower corner of the area around an anchor. */
    private Location corner(BlockAnchor anchor) {
        return anchor.block().getLocation().add(-half, -down, -half);
    }

    @Override
    public void onStart() {
        swap(plugin, corner(a), corner(b), half, down, up);
        swapped = true;
        record(plugin, this);
        for (BlockAnchor end : new BlockAnchor[]{a, b}) {
            Location c = end.center();
            c.getWorld().playSound(c, Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.4f);
            c.getWorld().spawnParticle(Particle.REVERSE_PORTAL, c, Fx.scaled(200), half, 3, half, 0.1);
        }
    }

    @Override
    public void tick(long tick) {
        if (tick % 10 != 0) return;
        // The seam of the grafted area glows faintly along its edges.
        for (BlockAnchor end : new BlockAnchor[]{a, b}) {
            if (!end.isLoaded()) continue;
            Location base = end.block().getLocation().add(0.5, 1.1, 0.5);
            Particle.DustOptions dust = Fx.dust(color(), 0.9f);
            for (int i = -half; i <= half; i += 2) {
                for (int[] edge : new int[][]{{i, -half}, {i, half}, {-half, i}, {half, i}}) {
                    Location at = base.clone().add(edge[0], 0, edge[1]);
                    at.setY(at.getWorld().getHighestBlockYAt(at) + 1.1);
                    at.getWorld().spawnParticle(Particle.DUST, at, 1, 0.1, 0.1, 0.1, 0, dust);
                }
            }
        }
    }

    @Override
    public void onEnd() {
        if (!swapped) return;
        swap(plugin, corner(a), corner(b), half, down, up);
        swapped = false;
        forget(plugin, id());
        for (BlockAnchor end : new BlockAnchor[]{a, b}) {
            Location c = end.center();
            c.getWorld().playSound(c, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.6f);
            c.getWorld().spawnParticle(Particle.PORTAL, c, Fx.scaled(150), half, 3, half, 0.5);
        }
    }

    // ------------------------------------------------------------------ the swap

    /**
     * Swaps two equally sized boxes of blocks, with their block entities (chest contents,
     * signs, ...). Both boxes are captured before either is written, so the swap is exact.
     * Players caught inside are lifted clear if a block now fills the space they stand in.
     */
    static void swap(Plugin plugin, Location cornerA, Location cornerB, int half, int down, int up) {
        int w = half * 2, h = down + up;
        Structure sa = Bukkit.getStructureManager().createStructure();
        Structure sb = Bukkit.getStructureManager().createStructure();
        org.bukkit.util.BlockVector size = new org.bukkit.util.BlockVector(w + 1, h + 1, w + 1);
        sa.fill(cornerA, size, false);
        sb.fill(cornerB, size, false);
        sb.place(cornerA, false, StructureRotation.NONE, Mirror.NONE, 0, 1f, RANDOM);
        sa.place(cornerB, false, StructureRotation.NONE, Mirror.NONE, 0, 1f, RANDOM);
        for (Location corner : new Location[]{cornerA, cornerB}) {
            BoundingBox box = new BoundingBox(corner.getX(), corner.getY(), corner.getZ(),
                    corner.getX() + w + 1, corner.getY() + h + 1, corner.getZ() + w + 1);
            for (Entity e : corner.getWorld().getNearbyEntities(box)) {
                if (!(e instanceof Player p)) continue;
                Location at = p.getLocation();
                if (!at.getBlock().isPassable() || !at.clone().add(0, 1, 0).getBlock().isPassable()) {
                    at.setY(at.getWorld().getHighestBlockYAt(at) + 1);
                    p.teleport(at);
                }
                p.setFallDistance(0);
            }
        }
        plugin.getLogger().fine("Swapped " + (w + 1) + "x" + (h + 1) + "x" + (w + 1) + " areas");
    }

    // ------------------------------------------------------------------ crash safety

    private static File file(Plugin plugin) {
        return new File(plugin.getDataFolder(), "locations.yml");
    }

    private static void record(Plugin plugin, LocationGraft g) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file(plugin));
        String k = "swaps." + UUID.randomUUID().toString().substring(0, 8) + "-" + g.id();
        Location ca = g.corner(g.a), cb = g.corner(g.b);
        yml.set(k + ".graft", g.id());
        yml.set(k + ".a", List.of(ca.getWorld().getName(), ca.getBlockX(), ca.getBlockY(), ca.getBlockZ()));
        yml.set(k + ".b", List.of(cb.getWorld().getName(), cb.getBlockX(), cb.getBlockY(), cb.getBlockZ()));
        yml.set(k + ".size", List.of(g.half, g.down, g.up));
        save(plugin, yml);
    }

    private static void forget(Plugin plugin, int graftId) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file(plugin));
        var swaps = yml.getConfigurationSection("swaps");
        if (swaps == null) return;
        for (String key : new ArrayList<>(swaps.getKeys(false))) {
            if (swaps.getInt(key + ".graft") == graftId) swaps.set(key, null);
        }
        save(plugin, yml);
    }

    /** Swaps back every area left grafted by a crash. Call once on startup, before any graft exists. */
    public static void recover(Plugin plugin) {
        File f = file(plugin);
        if (!f.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        var swaps = yml.getConfigurationSection("swaps");
        if (swaps == null) return;
        for (String key : new ArrayList<>(swaps.getKeys(false))) {
            try {
                Location ca = loc(swaps.getList(key + ".a"));
                Location cb = loc(swaps.getList(key + ".b"));
                List<?> size = swaps.getList(key + ".size");
                if (ca != null && cb != null && size != null) {
                    swap(plugin, ca, cb, ((Number) size.get(0)).intValue(), ((Number) size.get(1)).intValue(),
                            ((Number) size.get(2)).intValue());
                    plugin.getLogger().info("Restored a Location graft left open by a restart (" + key + ").");
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not restore Location graft " + key, ex);
            }
            swaps.set(key, null);
        }
        save(plugin, yml);
    }

    private static Location loc(List<?> raw) {
        if (raw == null || raw.size() < 4) return null;
        World world = Bukkit.getWorld(String.valueOf(raw.get(0)));
        if (world == null) return null;
        return new Location(world, ((Number) raw.get(1)).intValue(), ((Number) raw.get(2)).intValue(),
                ((Number) raw.get(3)).intValue());
    }

    private static void save(Plugin plugin, YamlConfiguration yml) {
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file(plugin));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save locations.yml", ex);
        }
    }

    @Override
    public Color color() {
        return Mode.LOCATION.color();
    }
}
