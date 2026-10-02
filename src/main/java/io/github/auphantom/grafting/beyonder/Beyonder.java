package io.github.auphantom.grafting.beyonder;

import io.github.auphantom.grafting.GraftingPlugin;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.util.Fx;
import io.github.auphantom.grafting.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Beyonder side of a Sequence 1 Attendant of Mysteries, kept deliberately small:
 * <ul>
 *   <li><b>Spirit</b>: every graft costs some, once or per second. It regenerates over time.</li>
 *   <li><b>Distance level</b> (1-4): rises with how far the player travels through their own
 *       distance grafts. Each level unlocks a new Distance art and lengthens the earlier ones.</li>
 *   <li><b>Spirit Body</b>: the spirit body and main body are one, so the Attendant can shift
 *       into it. Flight, no physical damage, but magic still hurts. Using any ability drops it.</li>
 *   <li><b>Sequence</b> of other players, for resistance rolls (Life graft, Enemy Step).</li>
 * </ul>
 * Shown live on the action bar above the hotbar.
 */
public final class Beyonder implements Listener {

    /** Damage types that still reach a Spirit Body: the supernatural ones. */
    private static final Set<DamageType> MAGIC = Set.of(DamageType.MAGIC, DamageType.INDIRECT_MAGIC,
            DamageType.WITHER, DamageType.WITHER_SKULL, DamageType.SONIC_BOOM, DamageType.DRAGON_BREATH,
            DamageType.THORNS, DamageType.OUT_OF_WORLD, DamageType.GENERIC_KILL);

    private static final TextColor SPIRIT_LO = TextColor.color(0x6fe6ee);
    private static final TextColor SPIRIT_HI = TextColor.color(0xb26bff);
    private static final TextColor DIST_LO = TextColor.color(0x7b5cff);
    private static final TextColor DIST_HI = TextColor.color(0xd9ccff);

    private final GraftingPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Profile> profiles = new HashMap<>();
    private long tick;

    public Beyonder(GraftingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    // ------------------------------------------------------------------ config

    public double maxSpirit() {
        return Math.max(1, plugin.getConfig().getDouble("spirit.max", 1000));
    }

    private double regen() {
        return plugin.getConfig().getDouble("spirit.regen-per-second", 8);
    }

    /** Spirit a graft costs to make (per use). */
    public double useCost(String key) {
        return plugin.getConfig().getDouble("spirit.cost." + key, 50);
    }

    /** Spirit a graft drains every second it is kept alive. */
    public double upkeep(String key) {
        return plugin.getConfig().getDouble("spirit.upkeep." + key, 0);
    }

    /** Blocks of distance-grafted travel needed to reach {@code level} (2, 3 or 4). */
    public double threshold(int level) {
        return switch (level) {
            case 2 -> plugin.getConfig().getDouble("distance.levels.2", 500);
            case 3 -> plugin.getConfig().getDouble("distance.levels.3", 2000);
            case 4 -> plugin.getConfig().getDouble("distance.levels.4", 6000);
            default -> 0;
        };
    }

    /** How far Step can carry the player at their level. */
    public double stepRange(Player player) {
        return plugin.getConfig().getDouble("distance.step-range", 500) * Math.pow(2, level(player) - 1);
    }

    /** How far Gateway can span at their level, -1 for unlimited. */
    public int gatewayRange(Player player) {
        int base = plugin.getConfig().getInt("max-distance", 256);
        return base < 0 ? -1 : base * (1 << (level(player) - 1));
    }

    /** How far away an enemy may be for Enemy Step at their level. */
    public double enemyRange(Player player) {
        return plugin.getConfig().getDouble("distance.enemy-range", 12) + 4 * (level(player) - 3);
    }

    // ------------------------------------------------------------------ profiles

    public Profile profile(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), id -> load(id));
    }

    private Profile load(UUID id) {
        Profile p = new Profile();
        String k = id.toString();
        p.spirit = data.getDouble(k + ".spirit", maxSpirit());
        p.travelled = data.getDouble(k + ".travelled", 0);
        p.sequence = data.getInt(k + ".sequence", -1);
        DistanceArt art = DistanceArt.byId(data.getString(k + ".art", "step"));
        p.art = art == null ? DistanceArt.STEP : art;
        io.github.auphantom.grafting.graft.Mode lend = io.github.auphantom.grafting.graft.Mode.byId(data.getString(k + ".lend", "fate"));
        if (lend != null) p.lend = lend;
        return p;
    }

    private void store(UUID id, Profile p) {
        String k = id.toString();
        data.set(k + ".spirit", Math.round(p.spirit * 10) / 10.0);
        data.set(k + ".travelled", Math.round(p.travelled * 10) / 10.0);
        data.set(k + ".sequence", p.sequence);
        data.set(k + ".art", p.art.id());
        data.set(k + ".lend", p.lend.id());
    }

    public void save() {
        profiles.forEach(this::store);
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save players.yml", ex);
        }
    }

    // ------------------------------------------------------------------ sequence

    /** The player's sequence: the Attendant themselves are 1, everyone else 10 unless set. */
    public int sequence(LivingEntity entity) {
        if (!(entity instanceof Player p)) return 10;
        int s = profile(p).sequence;
        if (s >= 0) return s;
        return p.hasPermission("grafting.use") ? 1 : 10;
    }

    public void setSequence(Player player, int sequence) {
        profile(player).sequence = sequence;
    }

    /**
     * Whether a target shakes off a graft cast by the caster. Ordinary beings never do. A Beyonder
     * of the same sequence or higher (lower number) usually does; one lower resists less.
     */
    public boolean resists(Player caster, LivingEntity target, String ability) {
        int mine = sequence(caster);
        int theirs = sequence(target);
        if (theirs >= 10 || target.equals(caster)) return false;
        double chance;
        if (theirs <= mine) {
            chance = plugin.getConfig().getDouble("resist." + ability + ".same-or-higher", 0.75);
        } else {
            chance = Math.max(0, plugin.getConfig().getDouble("resist." + ability + ".lower", 0.25)
                    - 0.05 * (theirs - mine - 1));
        }
        return Math.random() < chance;
    }

    // ------------------------------------------------------------------ spirit

    public double spirit(Player player) {
        return profile(player).spirit;
    }

    /**
     * Pays {@code cost} spirit, or says why not. Using any ability also drops the Spirit Body,
     * since the Attendant has to return to the main body to act.
     */
    public boolean spend(Player player, double cost, String what) {
        Profile p = profile(player);
        if (player.getGameMode() == GameMode.CREATIVE && plugin.getConfig().getBoolean("spirit.creative-free", false)) {
            cost = 0;
        }
        if (p.spirit < cost) {
            Text.send(player, "<red>Not enough spirit for " + Text.esc(what) + " <dark_gray>("
                    + (int) p.spirit + "/" + (int) cost + ")");
            player.playSound(player, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6f, 1.6f);
            flash(player, "<red>Not enough spirit");
            return false;
        }
        p.spirit -= cost;
        if (cost > 0) flash(player, "<color:#6fe6ee>-" + (int) Math.ceil(cost) + " spirit</color>");
        if (p.spiritBody) leaveSpiritBody(player, "You return to your main body to act.");
        return true;
    }

    /** Drains spirit without the checks; returns false once the pool is empty. */
    public boolean drain(Player player, double amount) {
        Profile p = profile(player);
        p.spirit = Math.max(0, p.spirit - amount);
        return p.spirit > 0;
    }

    public void setSpirit(Player player, double amount) {
        profile(player).spirit = Math.max(0, Math.min(maxSpirit(), amount));
    }

    /** A short note shown on the action bar for a couple of seconds. */
    public void flash(Player player, String miniMessage) {
        Profile p = profile(player);
        p.flash = miniMessage;
        p.flashUntil = tick + 40;
        render(player, p);
    }

    // ------------------------------------------------------------------ distance level

    public int level(Player player) {
        double t = profile(player).travelled;
        if (t >= threshold(4)) return 4;
        if (t >= threshold(3)) return 3;
        if (t >= threshold(2)) return 2;
        return 1;
    }

    public void setLevel(Player player, int level) {
        Profile p = profile(player);
        p.travelled = level <= 1 ? 0 : threshold(Math.min(4, level));
        if (p.art.level() > level) p.art = DistanceArt.STEP;
        if (level < 4) setInfinity(player, false);
    }

    /** Counts blocks travelled through a distance graft, levelling the player up as they go. */
    public void travelled(Player player, double blocks) {
        if (blocks <= 0) return;
        int before = level(player);
        profile(player).travelled += blocks;
        int after = level(player);
        if (after > before) levelUp(player, after);
    }

    private void levelUp(Player player, int level) {
        DistanceArt unlocked = DistanceArt.values()[level - 1];
        player.showTitle(Title.title(Text.mm("<gradient:#7b5cff:#d9ccff>Distance Level " + level),
                Text.mm("<gray>Unlocked <white>" + unlocked.display() + "</white>, and your earlier arts reach further"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
        player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        Fx.sphere(player.getLocation().add(0, 1, 0), 1.6, Particle.DUST, Fx.dust(Color.fromRGB(0x9b6bff), 1.2f), 60);
        Text.send(player, "<gray>Distance level <white>" + level + "</white>: <light_purple>" + unlocked.display()
                + "</light_purple> unlocked. <dark_gray>(" + unlocked.description() + ")");
    }

    public DistanceArt art(Player player) {
        return profile(player).art;
    }

    /** Which of the player's abilities an Ability graft lends to another player. */
    public io.github.auphantom.grafting.graft.Mode lend(Player player) {
        return profile(player).lend;
    }

    public void setLend(Player player, io.github.auphantom.grafting.graft.Mode mode) {
        profile(player).lend = mode;
    }

    public boolean setArt(Player player, DistanceArt art) {
        if (art.level() > level(player)) {
            Text.send(player, "<red>" + art.display() + " needs distance level " + art.level()
                    + ". <gray>Travel further through your distance grafts.");
            return false;
        }
        profile(player).art = art;
        if (art != DistanceArt.INFINITY) setInfinity(player, false);
        flash(player, "<color:#9b6bff>Distance: " + art.display() + "</color>");
        return true;
    }

    // ------------------------------------------------------------------ Distance: Step

    public void armStep(Player player, Location target) {
        Profile p = profile(player);
        p.stepTarget = target;
        Text.send(player, "<color:#9b6bff>Step</color> <gray>armed: your next step lands at <white>"
                + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ()
                + "</white> <dark_gray>(" + target.getWorld().getName() + ")");
        player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
    }

    /** Parses "x y z" (with ~ for relative) as a Step target, or returns null. */
    public static Location parseTarget(Player player, String[] parts) {
        if (parts.length < 3) return null;
        Location here = player.getLocation();
        try {
            double x = coord(parts[0], here.getX());
            double y = coord(parts[1], here.getY());
            double z = coord(parts[2], here.getZ());
            World world = parts.length >= 4 ? Bukkit.getWorld(parts[3]) : player.getWorld();
            if (world == null) return null;
            return new Location(world, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5, here.getYaw(), here.getPitch());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static double coord(String s, double base) {
        if (s.startsWith("~")) return base + (s.length() == 1 ? 0 : Double.parseDouble(s.substring(1)));
        return Double.parseDouble(s);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStep(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Profile p = profiles.get(player.getUniqueId());
        if (p == null || p.stepTarget == null) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        if (!player.isOnGround()) return;
        Location target = p.stepTarget;
        p.stepTarget = null;
        double distance = target.getWorld().equals(from.getWorld()) ? from.distance(target) : stepRange(player);
        if (distance > stepRange(player)) {
            Text.send(player, "<red>That place is beyond your reach (" + (int) stepRange(player)
                    + " blocks at distance level " + level(player) + ").");
            return;
        }
        if (!spend(player, useCost("step"), "Step")) return;
        Location dest = target.clone();
        dest.setYaw(to.getYaw());
        dest.setPitch(to.getPitch());
        if (dest.getY() < dest.getWorld().getMinHeight() || !dest.getBlock().isPassable()) {
            dest.setY(dest.getWorld().getHighestBlockYAt(dest) + 1);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Location old = player.getLocation();
            if (player.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                player.setFallDistance(0);
                old.getWorld().spawnParticle(Particle.REVERSE_PORTAL, old.add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0.05);
                dest.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 60, 0.3, 0.8, 0.3, 0.6);
                Fx.ring(dest.clone().add(0, 0.1, 0), 1.2, Particle.DUST, Fx.dust(Color.fromRGB(0x9b6bff), 1f), 40);
                dest.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.7f);
                travelled(player, distance);
                Text.send(player, "<gray>One step, <white>" + (int) distance + "</white> blocks.");
            }
        });
    }

    // ------------------------------------------------------------------ Distance: Infinity

    public boolean infinity(Player player) {
        Profile p = profiles.get(player.getUniqueId());
        return p != null && p.infinity;
    }

    public void setInfinity(Player player, boolean on) {
        Profile p = profile(player);
        if (p.infinity == on) return;
        if (on) {
            if (level(player) < 4) {
                Text.send(player, "<red>Infinity needs distance level 4.");
                return;
            }
            if (!spend(player, useCost("infinity"), "Infinity")) return;
            p.infinity = true;
            player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
            Text.send(player, "<color:#9b6bff>Infinity</color> <gray>surrounds you. Nothing can cross the distance. "
                    + "<dark_gray>(drains spirit every second, more for each blocked hit)");
        } else {
            p.infinity = false;
            player.playSound(player, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.4f);
            Text.send(player, "<gray>The infinite distance around you closes.");
        }
    }

    /** Projectiles stop dead at the edge of the infinite distance. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectile(ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof Player player) || !infinity(player)) return;
        Projectile proj = event.getEntity();
        if (player.equals(proj.getShooter())) return;
        event.setCancelled(true);
        absorb(player, 6, proj.getLocation());
        proj.setVelocity(new Vector());
        proj.remove();
    }

    /** Damage that would cross the infinite distance never arrives, but costs spirit to hold. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInfinityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !infinity(player)) return;
        if (GraftManager.isDealingDamage()) return;
        event.setCancelled(true);
        absorb(player, event.getDamage(), event.getDamager().getLocation());
        Entity attacker = event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity e ? e : event.getDamager();
        if (attacker instanceof LivingEntity && !attacker.equals(player)) {
            Vector away = attacker.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
            attacker.setVelocity(away.normalize().multiply(1.4).setY(0.35));
        }
    }

    private void absorb(Player player, double damage, Location at) {
        double cost = damage * plugin.getConfig().getDouble("spirit.upkeep.infinity-per-damage", 15);
        if (!drain(player, cost)) setInfinity(player, false);
        flash(player, "<color:#9b6bff>Infinity</color> <gray>held: <color:#6fe6ee>-" + (int) cost + " spirit");
        Location mid = player.getLocation().add(0, 1, 0);
        Vector dir = at.toVector().subtract(mid.toVector());
        if (dir.lengthSquared() > 0.01) mid.add(dir.normalize().multiply(1.3));
        Fx.ring(mid, 0.6, Particle.DUST, Fx.dust(Color.fromRGB(0xd9ccff), 0.8f), 20);
        mid.getWorld().spawnParticle(Particle.END_ROD, mid, 8, 0.1, 0.1, 0.1, 0.05);
        player.getWorld().playSound(mid, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 0.5f);
    }

    // ------------------------------------------------------------------ Spirit Body

    public boolean inSpiritBody(Player player) {
        Profile p = profiles.get(player.getUniqueId());
        return p != null && p.spiritBody;
    }

    public void toggleSpiritBody(Player player) {
        if (inSpiritBody(player)) leaveSpiritBody(player, "You return to your main body.");
        else enterSpiritBody(player);
    }

    public void enterSpiritBody(Player player) {
        Profile p = profile(player);
        if (p.spiritBody) return;
        double cost = useCost("spirit-body");
        if (p.spirit < cost) {
            Text.send(player, "<red>Not enough spirit to shift into your Spirit Body.");
            return;
        }
        p.spirit -= cost;
        p.spiritBody = true;
        p.flewBefore = player.getAllowFlight();
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        Location at = player.getLocation().add(0, 1, 0);
        at.getWorld().spawnParticle(Particle.SOUL, at, 30, 0.4, 0.8, 0.4, 0.05);
        Fx.sphere(at, 1.2, Particle.DUST, Fx.dust(Color.fromRGB(0x6fe6ee), 0.9f), 50);
        at.getWorld().playSound(at, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1f, 0.6f);
        at.getWorld().playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.8f);
        Text.send(player, "<color:#6fe6ee>Spirit Body.</color> <gray>Your bodies are one: fly freely, untouched by "
                + "physical harm. Magic still reaches you, and acting returns you to your main body.");
        flash(player, "<color:#6fe6ee>-" + (int) cost + " spirit</color>");
    }

    public void leaveSpiritBody(Player player, String why) {
        Profile p = profiles.get(player.getUniqueId());
        if (p == null || !p.spiritBody) return;
        p.spiritBody = false;
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        boolean keepsFlight = p.flewBefore || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR;
        if (!keepsFlight) {
            p.landingGrace = player.isFlying() || !player.isOnGround();
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        player.setFallDistance(0);
        Location at = player.getLocation().add(0, 1, 0);
        at.getWorld().spawnParticle(Particle.SOUL, at, 20, 0.3, 0.6, 0.3, 0.02);
        at.getWorld().playSound(at, Sound.ENTITY_ALLAY_ITEM_TAKEN, 1f, 0.6f);
        if (why != null) Text.send(player, "<gray>" + why);
    }

    /** In the Spirit Body, only supernatural harm reaches the Attendant. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBodyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Profile p = profiles.get(player.getUniqueId());
        if (p == null) return;
        if (p.landingGrace && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            p.landingGrace = false;
            return;
        }
        if (!p.spiritBody) return;
        if (isMagic(event)) return;
        event.setCancelled(true);
        player.setFireTicks(0);
        Location at = player.getLocation().add(0, 1, 0);
        at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 6, 0.3, 0.5, 0.3, 0.01);
    }

    public static boolean isMagic(EntityDamageEvent event) {
        if (GraftManager.isDealingDamage()) return true; // grafts are mysticism, not swords
        DamageType type = event.getDamageSource().getDamageType();
        return MAGIC.contains(type) || event.getCause() == EntityDamageEvent.DamageCause.MAGIC
                || event.getCause() == EntityDamageEvent.DamageCause.POISON
                || event.getCause() == EntityDamageEvent.DamageCause.WITHER;
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying() && inSpiritBody(event.getPlayer())) {
            event.getPlayer().setFallDistance(0);
        }
    }

    @EventHandler
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (inSpiritBody(event.getPlayer())) event.getPlayer().setAllowFlight(true);
        });
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        if (inSpiritBody(event.getPlayer())) event.getPlayer().setAllowFlight(true);
    }

    // ------------------------------------------------------------------ lifecycle

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        profile(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        leaveSpiritBody(player, null);
        Profile p = profiles.remove(player.getUniqueId());
        if (p != null) {
            store(player.getUniqueId(), p);
            save();
        }
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) leaveSpiritBody(player, null);
        save();
    }

    /** Called every tick. */
    public void tick() {
        tick++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("grafting.use")) continue;
            Profile p = profile(player);
            if (tick % 20 == 0) {
                p.spirit = Math.min(maxSpirit(), p.spirit + regen());
                if (p.infinity && !drain(player, upkeep("infinity"))) {
                    setInfinity(player, false);
                    Text.send(player, "<red>Your spirit runs dry and Infinity collapses.");
                }
            }
            if (p.spiritBody && tick % 4 == 0) {
                Location at = player.getLocation().add(0, 1, 0);
                at.getWorld().spawnParticle(Particle.DUST, at, Fx.scaled(4), 0.3, 0.6, 0.3, 0, Fx.dust(Color.fromRGB(0x6fe6ee), 0.7f));
                if (tick % 12 == 0) at.getWorld().spawnParticle(Particle.SOUL, at, 1, 0.3, 0.5, 0.3, 0.01);
            }
            if (p.infinity && tick % 3 == 0) {
                double a = tick * 0.15;
                Location c = player.getLocation().add(0, 1, 0);
                for (int i = 0; i < 3; i++) {
                    double ang = a + i * Math.PI * 2 / 3;
                    Location pt = c.clone().add(Math.cos(ang) * 1.6, Math.sin(ang * 0.7) * 0.8, Math.sin(ang) * 1.6);
                    pt.getWorld().spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, Fx.dust(Color.fromRGB(0xd9ccff), 0.9f));
                }
            }
            if (tick % 10 == 0 || (p.flash != null && tick <= p.flashUntil && tick % 2 == 0)) render(player, p);
        }
    }

    // ------------------------------------------------------------------ HUD

    private boolean showsHud(Player player) {
        if (!plugin.getConfig().getBoolean("hud.enabled", true)) return false;
        return plugin.getConfig().getBoolean("hud.always", false) || isHoldingThread(player)
                || inSpiritBody(player) || infinity(player)
                || profile(player).spirit < maxSpirit() - 0.5;
    }

    private static boolean isHoldingThread(Player player) {
        return io.github.auphantom.grafting.item.ThreadItem.is(player.getInventory().getItemInMainHand());
    }

    private void render(Player player, Profile p) {
        if (!showsHud(player)) return;
        double frac = p.spirit / maxSpirit();
        boolean low = frac < 0.2;
        TextColor lo = low && tick % 10 < 5 ? TextColor.color(0xff4d4d) : SPIRIT_LO;
        TextColor hi = low && tick % 10 < 5 ? TextColor.color(0xff9a3a) : SPIRIT_HI;

        Component bar = Component.text().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("✦ ", hi))
                .append(blocks(frac, 20, lo, hi))
                .append(Component.text(" " + (int) p.spirit, TextColor.color(0xffffff)))
                .append(Component.text("/" + (int) maxSpirit(), TextColor.color(0x8a8a8a))).build();

        int level = level(player);
        double from = threshold(level), to = level >= 4 ? from : threshold(level + 1);
        double lvlFrac = level >= 4 ? 1 : (p.travelled - from) / Math.max(1, to - from);
        Component dist = Component.text().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("   ⟷ ", DIST_HI))
                .append(Component.text("Lv" + level + " ", TextColor.color(0xffffff)))
                .append(blocks(lvlFrac, 8, DIST_LO, DIST_HI)).build();

        Component line = bar.append(dist);
        if (p.spiritBody) line = line.append(Text.mm("  <color:#6fe6ee><b>SPIRIT BODY</b>"));
        if (p.infinity) line = line.append(Text.mm("  <color:#d9ccff><b>∞</b>"));
        if (p.stepTarget != null) line = line.append(Text.mm("  <color:#9b6bff>step armed"));
        if (p.flash != null && tick <= p.flashUntil) line = line.append(Text.mm("  " + p.flash));
        player.sendActionBar(line);
    }

    /** A thin bar of block characters fading from {@code from} to {@code to}. */
    private static Component blocks(double fraction, int cells, TextColor from, TextColor to) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * cells);
        var b = Component.text();
        for (int i = 0; i < cells; i++) {
            if (i < filled) {
                float t = cells == 1 ? 0 : (float) i / (cells - 1);
                b.append(Component.text("▮", TextColor.lerp(t, from, to)));
            } else {
                b.append(Component.text("▯", TextColor.color(0x3a3a46)));
            }
        }
        return b.build();
    }
}
