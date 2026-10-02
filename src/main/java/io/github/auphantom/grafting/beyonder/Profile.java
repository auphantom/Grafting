package io.github.auphantom.grafting.beyonder;

import org.bukkit.Location;

/** Everything the plugin remembers about one player's Beyonder side. */
public final class Profile {

    // Saved.
    double spirit;
    double travelled;
    int sequence;
    DistanceArt art = DistanceArt.STEP;
    io.github.auphantom.grafting.graft.Mode lend = io.github.auphantom.grafting.graft.Mode.FATE;

    // Live only.
    boolean spiritBody;
    boolean flewBefore;
    boolean landingGrace;
    boolean infinity;
    Location stepTarget;
    String flash;
    long flashUntil;

    public double spirit() {
        return spirit;
    }

    public double travelled() {
        return travelled;
    }

    public int sequence() {
        return sequence;
    }

    public DistanceArt art() {
        return art;
    }

    public boolean inSpiritBody() {
        return spiritBody;
    }

    public boolean infinity() {
        return infinity;
    }

    public Location stepTarget() {
        return stepTarget;
    }
}
