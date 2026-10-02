package io.github.auphantom.grafting.graft;

import io.github.auphantom.grafting.GraftingPlugin;
import io.github.auphantom.grafting.anchor.Anchor;
import io.github.auphantom.grafting.anchor.BlockAnchor;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import io.github.auphantom.grafting.beyonder.Beyonder;
import io.github.auphantom.grafting.beyonder.DistanceArt;
import io.github.auphantom.grafting.graft.types.AbilityGraft;
import io.github.auphantom.grafting.graft.types.DistanceGraft;
import io.github.auphantom.grafting.graft.types.EnemyStepGraft;
import io.github.auphantom.grafting.graft.types.EnmityGraft;
import io.github.auphantom.grafting.graft.types.ExchangeGraft;
import io.github.auphantom.grafting.graft.types.FateGraft;
import io.github.auphantom.grafting.graft.types.GravityGraft;
import io.github.auphantom.grafting.graft.types.LifeGraft;
import io.github.auphantom.grafting.graft.types.LocationGraft;
import io.github.auphantom.grafting.graft.types.Nature;
import io.github.auphantom.grafting.graft.types.NatureGraft;
import io.github.auphantom.grafting.graft.types.PuppetGraft;
import io.github.auphantom.grafting.graft.types.ReturnGraft;
import io.github.auphantom.grafting.graft.types.StorageGraft;
import io.github.auphantom.grafting.graft.types.SupernovaGraft;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The heart of the ability: turns "this mode, these two ends" into a live graft,
 * or explains why the thread refuses to connect them. It also charges the spirit the
 * graft costs, after every check has passed.
 * <p>
 * Order matters, exactly like in the novels: grafting A onto B is not grafting B onto A.
 */
public final class GraftFactory {

    /** Result of an attempt: a graft, or the reason the thread refused (null reason = already explained). */
    public record Result(Graft graft, String error) {
        static Result ok(Graft graft) {
            return new Result(graft, null);
        }

        static Result fail(String error) {
            return new Result(null, error);
        }
    }

    private final GraftingPlugin plugin;
    private final GraftManager manager;
    private final Beyonder beyonder;

    public GraftFactory(GraftingPlugin plugin, GraftManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.beyonder = manager.beyonder();
    }

    /** Whether an anchor is an acceptable end for a slot of the given kind. */
    public static boolean accepts(Mode.End end, Anchor anchor) {
        return switch (end) {
            case PLACE -> anchor instanceof BlockAnchor;
            case BEING -> anchor instanceof EntityAnchor;
            case ANY -> true;
        };
    }

    /** What the first end must be, given the player's chosen Distance art. */
    public Mode.End firstEnd(Player player, Mode mode) {
        if (mode == Mode.DISTANCE && beyonder.art(player) == DistanceArt.ENEMY) return Mode.End.BEING;
        return mode.first();
    }

    public Mode.End secondEnd(Player player, Mode mode) {
        return mode.second();
    }

    public Result create(Player player, Mode mode, Anchor first, Anchor second) {
        if (first.sameAs(second)) return Result.fail("A thing cannot be grafted onto itself.");
        if (!first.isIntact() || !second.isIntact()) return Result.fail("One end of the thread is already gone.");
        if (!accepts(firstEnd(player, mode), first) || !accepts(secondEnd(player, mode), second)) {
            return Result.fail(mode.display() + " needs " + firstEnd(player, mode).article() + ", then "
                    + secondEnd(player, mode).article() + ".");
        }
        UUID owner = player.getUniqueId();
        int id = manager.nextId();
        Result built = switch (mode) {
            case DISTANCE -> distance(player, id, first, second);
            case FATE -> Result.ok(new FateGraft(id, owner, (EntityAnchor) first, (EntityAnchor) second, expiry(mode)));
            case NATURE -> nature(id, owner, (BlockAnchor) first, (EntityAnchor) second, expiry(mode));
            case RETURN -> Result.ok(new ReturnGraft(id, owner, (EntityAnchor) first, (BlockAnchor) second, expiry(mode)));
            case EXCHANGE -> sameWorld(first, second, () ->
                    new ExchangeGraft(id, owner, (EntityAnchor) first, (EntityAnchor) second, expiry(mode), manager));
            case ENMITY -> Result.ok(new EnmityGraft(id, owner, (EntityAnchor) first, (EntityAnchor) second, expiry(mode)));
            case GRAVITY -> sameWorld(first, second, () ->
                    new GravityGraft(id, owner, (EntityAnchor) first, (BlockAnchor) second, expiry(mode)));
            case PUPPET -> sameWorld(first, second, () ->
                    new PuppetGraft(id, owner, (EntityAnchor) first, (EntityAnchor) second, expiry(mode)));
            case SUPERNOVA -> Result.ok(new SupernovaGraft(id, owner, (BlockAnchor) first, second,
                    manager.currentTick() + SupernovaGraft.IGNITION_TICKS + SupernovaGraft.COLLAPSE_TICKS
                            + SupernovaGraft.SHOCKWAVE_TICKS + 40,
                    manager,
                    plugin.getConfig().getDouble("supernova.radius", 10),
                    plugin.getConfig().getDouble("supernova.max-damage", 30),
                    plugin.getConfig().getBoolean("supernova.break-blocks", false)));
            case LIFE -> life(player, id, (EntityAnchor) first, (EntityAnchor) second);
            case LOCATION -> location(id, owner, (BlockAnchor) first, (BlockAnchor) second);
            case ABILITY -> ability(player, id, (EntityAnchor) first, (EntityAnchor) second);
            case STORAGE -> storage(id, owner, (BlockAnchor) first, (EntityAnchor) second);
        };
        if (built.graft() == null) return built;
        if (!beyonder.spend(player, beyonder.useCost(built.graft().costKey()), built.graft().name())) {
            return Result.fail(null);
        }
        return built;
    }

    // ------------------------------------------------------------------ Distance arts

    private Result distance(Player player, int id, Anchor first, Anchor second) {
        DistanceArt art = beyonder.art(player);
        return switch (art) {
            case ENEMY -> enemyStep(player, id, (EntityAnchor) first, (BlockAnchor) second);
            default -> gateway(player, id, (BlockAnchor) first, (BlockAnchor) second);
        };
    }

    private Result gateway(Player player, int id, BlockAnchor a, BlockAnchor b) {
        if (beyonder.level(player) < DistanceArt.GATEWAY.level()) return Result.fail("Gateway needs distance level 2.");
        if (!a.world().equals(b.world())) return Result.fail("Distance can only be grafted within a single world.");
        int max = beyonder.gatewayRange(player);
        double d = a.center().distance(b.center());
        if (max >= 0 && d > max) {
            return Result.fail("Those places are too far apart (" + max + " blocks at distance level "
                    + beyonder.level(player) + ").");
        }
        if (d < 2) return Result.fail("Those places are already touching.");
        return Result.ok(new DistanceGraft(id, player.getUniqueId(), a, b, expiry(Mode.DISTANCE), beyonder::travelled));
    }

    private Result enemyStep(Player player, int id, EntityAnchor being, BlockAnchor destination) {
        if (beyonder.level(player) < DistanceArt.ENEMY.level()) return Result.fail("Enemy Step needs distance level 3.");
        double range = beyonder.enemyRange(player);
        if (!being.world().equals(player.getWorld()) || being.center().distance(player.getLocation()) > range) {
            return Result.fail("They must be in front of you to graft their step (within " + (int) range + " blocks).");
        }
        if (!destination.world().equals(being.world())) return Result.fail("The destination must be in their world.");
        if (beyonder.resists(player, being.entity(), "enemy")) {
            beyonder.spend(player, beyonder.useCost("enemy") / 2, "the failed graft");
            return Result.fail(being.describe() + "'s spirituality shakes the thread off.");
        }
        long expiry = manager.currentTick() + Math.max(1, plugin.getConfig().getLong("durations.enemy", 20)) * 20;
        return Result.ok(new EnemyStepGraft(id, player.getUniqueId(), being, destination, expiry));
    }

    // ------------------------------------------------------------------ the rest

    private Result nature(int id, UUID owner, BlockAnchor a, EntityAnchor b, long expiry) {
        Nature nature = Nature.of(a.material());
        if (nature == null) {
            return Result.fail("A " + a.describe().split(" at ")[0].toLowerCase()
                    + " has no nature strong enough to graft. Try slime, ice, TNT, a cobweb, glowstone...");
        }
        return Result.ok(new NatureGraft(id, owner, a, b, nature, expiry));
    }

    private Result life(Player player, int id, EntityAnchor life, EntityAnchor vessel) {
        if (beyonder.resists(player, life.entity(), "life") || beyonder.resists(player, vessel.entity(), "life")) {
            beyonder.spend(player, beyonder.useCost("life") / 2, "the failed graft");
            return Result.fail("Their spirituality is too strong: the Life graft slips off.");
        }
        return Result.ok(new LifeGraft(id, player.getUniqueId(), life, vessel, expiry(Mode.LIFE)));
    }

    private Result location(int id, UUID owner, BlockAnchor a, BlockAnchor b) {
        int half = Math.max(1, plugin.getConfig().getInt("location.radius", 8));
        int down = Math.max(0, plugin.getConfig().getInt("location.depth", 4));
        int up = Math.max(1, plugin.getConfig().getInt("location.height", 12));
        if (a.world().equals(b.world())) {
            double dx = Math.abs(a.block().getX() - b.block().getX());
            double dz = Math.abs(a.block().getZ() - b.block().getZ());
            double dy = Math.abs(a.block().getY() - b.block().getY());
            if (dx <= half * 2 && dz <= half * 2 && dy <= down + up) {
                return Result.fail("Those areas overlap. Pick places further apart.");
            }
        }
        for (Graft g : manager.all()) {
            if (g instanceof LocationGraft) return Result.fail("Only one Location graft can hold at a time.");
        }
        return Result.ok(new LocationGraft(id, owner, a, b, expiry(Mode.LOCATION), plugin, half, down, up));
    }

    private Result ability(Player player, int id, EntityAnchor source, EntityAnchor receiver) {
        boolean self = source.entity().equals(player);
        if (source.entity() instanceof Player && !self) {
            return Result.fail("You can graft your own powers, or a creature's, but not another player's.");
        }
        if (!(receiver.entity() instanceof Player)) {
            return Result.fail("Only a player can wield a grafted ability.");
        }
        if (self && receiver.entity().equals(player)) return Result.fail("Your powers are already yours.");
        return Result.ok(new AbilityGraft(id, player.getUniqueId(), source, receiver, expiry(Mode.ABILITY),
                beyonder.lend(player)));
    }

    private Result storage(int id, UUID owner, BlockAnchor container, EntityAnchor holder) {
        if (!(container.block().getState() instanceof Container)) {
            return Result.fail("Storage needs a container: a chest, barrel, shulker box...");
        }
        if (!(holder.entity() instanceof Player)) return Result.fail("Only a player has an inventory to graft onto.");
        for (Graft g : manager.all()) {
            if (g instanceof StorageGraft && g.second().sameAs(holder)) {
                manager.end(g, "was replaced by a new storage");
            }
        }
        return Result.ok(new StorageGraft(id, owner, container, holder, expiry(Mode.STORAGE)));
    }

    private static Result sameWorld(Anchor a, Anchor b, java.util.function.Supplier<Graft> make) {
        if (!a.world().equals(b.world())) return Result.fail("Both ends must be in the same world.");
        return Result.ok(make.get());
    }

    private long expiry(Mode mode) {
        long seconds = Math.max(1, plugin.getConfig().getLong("durations." + mode.id(), 60));
        return manager.currentTick() + seconds * 20;
    }
}
