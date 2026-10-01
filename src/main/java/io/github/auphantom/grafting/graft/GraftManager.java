package io.github.auphantom.grafting.graft;

import io.github.auphantom.grafting.GraftingPlugin;
import io.github.auphantom.grafting.Text;
import io.github.auphantom.grafting.anchor.Anchor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns every live graft: ticks them, ends them when they expire or lose an end,
 * forwards damage events, and draws the visible thread between both ends.
 */
public final class GraftManager {

    private static final double FULL_THREAD_MAX_LENGTH = 48;
    private static final double STUB_LENGTH = 5;

    private final GraftingPlugin plugin;
    private final List<Graft> grafts = new ArrayList<>();
    private BukkitTask task;
    private int nextId = 1;
    private long tick;

    public GraftManager(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Graft graft : new ArrayList<>(grafts)) {
            end(graft, null);
        }
    }

    public long currentTick() {
        return tick;
    }

    public int nextId() {
        return nextId++;
    }

    /** Registers a new graft, unravelling the owner's oldest one if they are over the limit. */
    public void add(Graft graft) {
        int max = Math.max(1, plugin.getConfig().getInt("max-active-grafts", 5));
        List<Graft> owned = ofOwner(graft.owner());
        for (int i = 0; i <= owned.size() - max; i++) {
            end(owned.get(i), "was let go to make room for a new one");
        }
        grafts.add(graft);
        try {
            graft.onStart();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Graft #" + graft.id() + " failed to start", ex);
            grafts.remove(graft);
            return;
        }
        burst(graft.first().center(), graft.color());
        burst(graft.second().center(), graft.color());
        Location at = graft.second().center();
        at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.6f);
        at.getWorld().playSound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.4f);
    }

    public List<Graft> ofOwner(UUID owner) {
        List<Graft> owned = new ArrayList<>();
        for (Graft graft : grafts) {
            if (graft.owner().equals(owner)) owned.add(graft);
        }
        return owned;
    }

    public List<Graft> all() {
        return List.copyOf(grafts);
    }

    /**
     * Ends a graft. A {@code null} reason ends it silently (used on shutdown).
     *
     * @return whether the graft was still alive
     */
    public boolean end(Graft graft, String reason) {
        if (!grafts.remove(graft)) return false;
        try {
            graft.onEnd();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Graft #" + graft.id() + " failed to end cleanly", ex);
        }
        if (reason != null) {
            Player owner = Bukkit.getPlayer(graft.owner());
            if (owner != null) {
                Text.send(owner, "<gray>Your <light_purple>" + graft.name() + "</light_purple> graft <dark_gray>#"
                        + graft.id() + "</dark_gray> " + reason + ".");
            }
        }
        return true;
    }

    /** Called by the listener for every entity damage event. */
    public void dispatchDamage(EntityDamageEvent event, boolean late) {
        for (Graft graft : new ArrayList<>(grafts)) {
            if (graft.isSpent() || !graft.isIntact()) continue;
            if (event.isCancelled()) return;
            try {
                if (late) graft.onLateDamage(event);
                else graft.onDamage(event);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Graft #" + graft.id() + " failed to handle damage", ex);
            }
        }
    }

    private void tick() {
        tick++;
        for (Graft graft : new ArrayList<>(grafts)) {
            if (graft.isSpent()) {
                end(graft, "has fulfilled its purpose");
            } else if (!graft.isIntact()) {
                end(graft, "snapped: one of its ends is gone");
            } else if (tick >= graft.expiresAt()) {
                end(graft, "has unravelled");
            } else {
                try {
                    graft.tick(tick);
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Graft #" + graft.id() + " crashed, severing it", ex);
                    end(graft, "was torn apart by an error");
                    continue;
                }
                if (tick % 4 == 0) drawThread(graft);
            }
        }
    }

    private void drawThread(Graft graft) {
        Anchor a = graft.first();
        Anchor b = graft.second();
        Location from = a.center();
        Location to = b.center();
        Particle.DustOptions dust = new Particle.DustOptions(graft.color(), 0.6f);
        if (!from.getWorld().equals(to.getWorld())) {
            from.getWorld().spawnParticle(Particle.DUST, from, 6, 0.3, 0.3, 0.3, 0, dust);
            to.getWorld().spawnParticle(Particle.DUST, to, 6, 0.3, 0.3, 0.3, 0, dust);
            return;
        }
        double length = from.distance(to);
        if (length < 0.01) return;
        if (length <= FULL_THREAD_MAX_LENGTH) {
            line(from, to, length, dust);
            // A brighter mote travelling along the thread, so you can see which way it "flows".
            double phase = ((tick / 4) % 10) / 10.0;
            Location mote = from.clone().add(to.toVector().subtract(from.toVector()).multiply(phase));
            from.getWorld().spawnParticle(Particle.END_ROD, mote, 1, 0, 0, 0, 0);
        } else {
            // Too long to draw: show two short stubs pointing at each other.
            Vector dir = to.toVector().subtract(from.toVector()).normalize().multiply(STUB_LENGTH);
            line(from, from.clone().add(dir), STUB_LENGTH, dust);
            line(to, to.clone().subtract(dir), STUB_LENGTH, dust);
        }
    }

    private static void line(Location from, Location to, double length, Particle.DustOptions dust) {
        int points = (int) Math.min(120, Math.ceil(length / 0.4));
        Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / points);
        Location cursor = from.clone();
        for (int i = 0; i <= points; i++) {
            from.getWorld().spawnParticle(Particle.DUST, cursor, 1, 0, 0, 0, 0, dust);
            cursor.add(step);
        }
    }

    static void burst(Location at, Color color) {
        at.getWorld().spawnParticle(Particle.DUST, at, 30, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(color, 1.2f));
        at.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at, 25, 0.3, 0.3, 0.3, 0.05);
    }
}
