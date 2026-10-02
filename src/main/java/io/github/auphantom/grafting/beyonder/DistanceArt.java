package io.github.auphantom.grafting.beyonder;

/**
 * The four arts of Distance grafting. Each one unlocks at a distance level, and the level
 * rises with how far the Attendant has travelled through their own distance grafts.
 */
public enum DistanceArt {
    STEP(1, "Step", "step", "Your next step lands on a coordinate you name."),
    GATEWAY(2, "Gateway", "gateway", "Two places become one. Step on either, arrive on the other."),
    ENEMY(3, "Enemy Step", "enemy", "A nearby being's next step lands where you choose."),
    INFINITY(4, "Infinity", "infinity", "Infinite distance around you. Attacks never arrive.");

    private final int level;
    private final String display;
    private final String id;
    private final String description;

    DistanceArt(int level, String display, String id, String description) {
        this.level = level;
        this.display = display;
        this.id = id;
        this.description = description;
    }

    public int level() {
        return level;
    }

    public String display() {
        return display;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public static DistanceArt byId(String id) {
        for (DistanceArt art : values()) {
            if (art.id.equalsIgnoreCase(id) || art.name().equalsIgnoreCase(id)) return art;
        }
        return null;
    }
}
