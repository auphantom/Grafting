package io.github.auphantom.grafting.graft;

import io.github.auphantom.grafting.GraftingPlugin;
import io.github.auphantom.grafting.anchor.Anchor;
import io.github.auphantom.grafting.anchor.BlockAnchor;
import io.github.auphantom.grafting.anchor.EntityAnchor;
import io.github.auphantom.grafting.graft.types.DistanceGraft;
import io.github.auphantom.grafting.graft.types.EnmityGraft;
import io.github.auphantom.grafting.graft.types.ExchangeGraft;
import io.github.auphantom.grafting.graft.types.FateGraft;
import io.github.auphantom.grafting.graft.types.GravityGraft;
import io.github.auphantom.grafting.graft.types.Nature;
import io.github.auphantom.grafting.graft.types.NatureGraft;
import io.github.auphantom.grafting.graft.types.PuppetGraft;
import io.github.auphantom.grafting.graft.types.ReturnGraft;
import io.github.auphantom.grafting.graft.types.SupernovaGraft;

import java.util.UUID;

/**
 * The heart of the ability: turns "this mode, these two ends" into a live graft,
 * or explains why the thread refuses to connect them.
 * <p>
 * Order matters, exactly like in the novels: grafting A onto B is not grafting B onto A.
 */
public final class GraftFactory {

    /** Result of an attempt: either a graft, or the reason the thread refused to connect. */
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

    public GraftFactory(GraftingPlugin plugin, GraftManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** Whether an anchor is an acceptable end for a slot of the given mode. */
    public static boolean accepts(Mode.End end, Anchor anchor) {
        return switch (end) {
            case PLACE -> anchor instanceof BlockAnchor;
            case BEING -> anchor instanceof EntityAnchor;
            case ANY -> true;
        };
    }

    public Result create(UUID owner, Mode mode, Anchor first, Anchor second) {
        if (first.sameAs(second)) return Result.fail("A thing cannot be grafted onto itself.");
        if (!first.isIntact() || !second.isIntact()) return Result.fail("One end of the thread is already gone.");
        if (!accepts(mode.first(), first) || !accepts(mode.second(), second)) {
            return Result.fail(mode.display() + " needs " + mode.first().article() + ", then "
                    + mode.second().article() + ".");
        }
        int id = manager.nextId();
        long expiry = expiry(mode);
        return switch (mode) {
            case DISTANCE -> distance(id, owner, (BlockAnchor) first, (BlockAnchor) second, expiry);
            case FATE -> Result.ok(new FateGraft(id, owner, (EntityAnchor) first, (EntityAnchor) second, expiry));
            case NATURE -> nature(id, owner, (BlockAnchor) first, (EntityAnchor) second, expiry);
            case RETURN -> Result.ok(new ReturnGraft(id, owner, (EntityAnchor) first, (BlockAnchor) second, expiry));
            case EXCHANGE -> sameWorld(first, second, () ->
                    new ExchangeGraft(id, owner, (EntityAnchor) first, (EntityAnchor) second, expiry, manager));
            case ENMITY -> Result.ok(new EnmityGraft(id, owner, (EntityAnchor) first, (EntityAnchor) second, expiry));
            case GRAVITY -> sameWorld(first, second, () ->
                    new GravityGraft(id, owner, (EntityAnchor) first, (BlockAnchor) second, expiry));
            case PUPPET -> sameWorld(first, second, () ->
                    new PuppetGraft(id, owner, (EntityAnchor) first, (EntityAnchor) second, expiry));
            case SUPERNOVA -> Result.ok(new SupernovaGraft(id, owner, (BlockAnchor) first, second,
                    manager.currentTick() + SupernovaGraft.IGNITION_TICKS + SupernovaGraft.COLLAPSE_TICKS
                            + SupernovaGraft.SHOCKWAVE_TICKS + 40,
                    manager,
                    plugin.getConfig().getDouble("supernova.radius", 10),
                    plugin.getConfig().getDouble("supernova.max-damage", 30),
                    plugin.getConfig().getBoolean("supernova.break-blocks", false)));
        };
    }

    private Result distance(int id, UUID owner, BlockAnchor a, BlockAnchor b, long expiry) {
        if (!a.world().equals(b.world())) return Result.fail("Distance can only be grafted within a single world.");
        int max = plugin.getConfig().getInt("max-distance", 256);
        if (max >= 0 && a.center().distance(b.center()) > max) {
            return Result.fail("Those places are too far apart (max " + max + " blocks).");
        }
        if (a.center().distance(b.center()) < 2) return Result.fail("Those places are already touching.");
        return Result.ok(new DistanceGraft(id, owner, a, b, expiry));
    }

    private Result nature(int id, UUID owner, BlockAnchor a, EntityAnchor b, long expiry) {
        Nature nature = Nature.of(a.material());
        if (nature == null) {
            return Result.fail("A " + a.describe().split(" at ")[0].toLowerCase()
                    + " has no nature strong enough to graft. Try slime, ice, TNT, a cobweb, glowstone...");
        }
        return Result.ok(new NatureGraft(id, owner, a, b, nature, expiry));
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
