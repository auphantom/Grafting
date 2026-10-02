package io.github.auphantom.grafting.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Small particle toolkit. Every shape scales with {@code particles.density} from the config,
 * and every spawn goes through {@link #spawn} so a particle whose data type changed between
 * Minecraft versions can never throw.
 */
public final class Fx {

    private static double density = 1.0;

    private Fx() {
    }

    public static void setDensity(double value) {
        density = Math.max(0.1, Math.min(4.0, value));
    }

    public static int scaled(int count) {
        return Math.max(1, (int) Math.round(count * density));
    }

    public static Particle.DustOptions dust(Color color, float size) {
        return new Particle.DustOptions(color, size);
    }

    /** Spawns a particle, supplying data only when the particle accepts it. */
    public static void spawn(World world, Particle particle, Location at, int count,
                             double dx, double dy, double dz, double speed, Object data) {
        Class<?> type = particle.getDataType();
        if (type == Void.class) {
            world.spawnParticle(particle, at, count, dx, dy, dz, speed, null, true);
        } else if (data != null && type.isInstance(data)) {
            world.spawnParticle(particle, at, count, dx, dy, dz, speed, data, true);
        } else if (type == Color.class) {
            world.spawnParticle(particle, at, count, dx, dy, dz, speed, Color.WHITE, true);
        }
        // Any other data type we do not know how to fill: silently skip rather than crash.
    }

    public static void spawn(Location at, Particle particle, int count, double spread, double speed) {
        spawn(at.getWorld(), particle, at, scaled(count), spread, spread, spread, speed, null);
    }

    /** A straight dotted line. */
    public static void line(Location from, Location to, Particle.DustOptions dust, double spacing) {
        double length = from.distance(to);
        int points = (int) Math.min(240, Math.ceil(length / (spacing / density)));
        if (points < 1) return;
        Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / points);
        Location cursor = from.clone();
        for (int i = 0; i <= points; i++) {
            from.getWorld().spawnParticle(Particle.DUST, cursor, 1, 0, 0, 0, 0, dust, true);
            cursor.add(step);
        }
    }

    /**
     * Two strands twisting around the line from {@code from} to {@code to}, like a real
     * thread. {@code phase} rotates the twist so it appears to spin over time.
     */
    public static void helix(Location from, Location to, Color a, Color b, double radius, double phase) {
        Vector axis = to.toVector().subtract(from.toVector());
        double length = axis.length();
        if (length < 0.01) return;
        Vector dir = axis.clone().multiply(1 / length);
        Vector u = perpendicular(dir);
        Vector v = dir.getCrossProduct(u).normalize();
        int points = (int) Math.min(240, Math.ceil(length / (0.18 / density)));
        Particle.DustOptions da = dust(a, 0.55f);
        Particle.DustOptions db = dust(b, 0.45f);
        World world = from.getWorld();
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            double angle = phase + t * length * 2.2;
            Vector base = from.toVector().add(dir.clone().multiply(t * length));
            Vector off = u.clone().multiply(Math.cos(angle) * radius).add(v.clone().multiply(Math.sin(angle) * radius));
            world.spawnParticle(Particle.DUST, base.clone().add(off).toLocation(world), 1, 0, 0, 0, 0, da, true);
            world.spawnParticle(Particle.DUST, base.clone().subtract(off).toLocation(world), 1, 0, 0, 0, 0, db, true);
        }
    }

    /** A horizontal ring. */
    public static void ring(Location center, double radius, Particle particle, Object data, int points) {
        int n = scaled(points);
        World world = center.getWorld();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            Location at = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            spawn(world, particle, at, 1, 0, 0, 0, 0, data);
        }
    }

    /** Points spread evenly over a sphere (Fibonacci lattice). */
    public static void sphere(Location center, double radius, Particle particle, Object data, int points) {
        int n = scaled(points);
        World world = center.getWorld();
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - (i / (double) (n - 1 == 0 ? 1 : n - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double theta = golden * i;
            Location at = center.clone().add(Math.cos(theta) * r * radius, y * radius, Math.sin(theta) * r * radius);
            spawn(world, particle, at, 1, 0, 0, 0, 0, data);
        }
    }

    /** A big coloured puff plus some portal motes. Used whenever a thread is tied or snaps. */
    public static void burst(Location at, Color color) {
        World world = at.getWorld();
        spawn(world, Particle.DUST, at, scaled(60), 0.5, 0.5, 0.5, 0, dust(color, 1.3f));
        spawn(world, Particle.REVERSE_PORTAL, at, scaled(50), 0.4, 0.4, 0.4, 0.08, null);
        spawn(world, Particle.END_ROD, at, scaled(14), 0.2, 0.2, 0.2, 0.12, null);
        sphere(at, 0.9, Particle.DUST, dust(color.mixColors(Color.WHITE), 0.7f), 40);
    }

    private static Vector perpendicular(Vector dir) {
        Vector helper = Math.abs(dir.getY()) < 0.9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        return dir.getCrossProduct(helper).normalize();
    }
}
