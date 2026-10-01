package io.github.auphantom.grafting.graft;

import io.github.auphantom.grafting.GraftingPlugin;
import io.github.auphantom.grafting.anchor.Anchor;
import io.github.auphantom.grafting.anchor.BlockAnchor;
import io.github.auphantom.grafting.anchor.EntityAnchor;

import java.util.UUID;

/**
 * The heart of the ability: decides what connecting two anchors <em>means</em>.
 * <pre>
 *   first \ second |  Place               |  Being
 *   ---------------+----------------------+-----------------------
 *   Place          |  Distance            |  Nature (block -> being)
 *   Being          |  Death & Return      |  Fate (harm A -> B)
 * </pre>
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

    public Result create(UUID owner, Anchor first, Anchor second) {
        if (first.sameAs(second)) return Result.fail("A thing cannot be grafted onto itself.");
        if (!first.isIntact() || !second.isIntact()) return Result.fail("One end of the thread is already gone.");

        if (first instanceof BlockAnchor a && second instanceof BlockAnchor b) return distance(owner, a, b);
        if (first instanceof EntityAnchor a && second instanceof EntityAnchor b) {
            return Result.ok(new FateGraft(manager.nextId(), owner, a, b, expiry("fate")));
        }
        if (first instanceof BlockAnchor a && second instanceof EntityAnchor b) {
            Nature nature = Nature.of(a.material());
            if (nature == null) {
                return Result.fail("A " + a.describe().split(" at ")[0].toLowerCase()
                        + " has no nature strong enough to graft. Try slime, ice, TNT, a cobweb, glowstone...");
            }
            return Result.ok(new NatureGraft(manager.nextId(), owner, a, b, nature, expiry("nature")));
        }
        if (first instanceof EntityAnchor a && second instanceof BlockAnchor b) {
            return Result.ok(new ReturnGraft(manager.nextId(), owner, a, b, expiry("return")));
        }
        throw new IllegalStateException("Unknown anchor combination");
    }

    private Result distance(UUID owner, BlockAnchor a, BlockAnchor b) {
        if (!a.world().equals(b.world())) return Result.fail("Distance can only be grafted within a single world.");
        int max = plugin.getConfig().getInt("max-distance", 256);
        if (max >= 0 && a.center().distance(b.center()) > max) {
            return Result.fail("Those places are too far apart (max " + max + " blocks).");
        }
        if (a.center().distance(b.center()) < 2) return Result.fail("Those places are already touching.");
        return Result.ok(new DistanceGraft(manager.nextId(), owner, a, b, expiry("distance")));
    }

    private long expiry(String kind) {
        long seconds = Math.max(1, plugin.getConfig().getLong("durations." + kind, 60));
        return manager.currentTick() + seconds * 20;
    }
}
