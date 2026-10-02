package io.github.auphantom.grafting.graft;

import org.bukkit.Color;

/**
 * Every ability the Thread of Grafting can perform. The player picks one (left-click to
 * cycle, sneak + left-click for the menu), then ties the thread to two ends. Each mode
 * says what kind of thing each end must be.
 */
public enum Mode {
    DISTANCE("Distance", 0x9b6bff, End.PLACE, End.PLACE, "distance", "ender_pearl",
            "Two places become one. Step on either, arrive on the other."),
    FATE("Fate", 0xc81e3c, End.BEING, End.BEING, "fate", "shield",
            "Harm meant for the first finds the second instead."),
    NATURE("Nature", 0x4fc95a, End.PLACE, End.BEING, "nature", "slime_ball",
            "The being takes on the nature of the block."),
    RETURN("Death & Return", 0x3cd2a0, End.BEING, End.PLACE, "return", "totem_of_undying",
            "The being's next death becomes a journey back to the block."),
    EXCHANGE("Exchange", 0x3fa9ff, End.BEING, End.BEING, "exchange", "chorus_fruit",
            "The two swap places, and swap again whenever the first is struck."),
    ENMITY("Enmity", 0xff8a1f, End.BEING, End.BEING, "enmity", "fermented_spider_eye",
            "Every hostility aimed at the first is aimed at the second."),
    GRAVITY("Gravity", 0x6a5cff, End.BEING, End.PLACE, "gravity", "heavy_core",
            "For the being, 'down' now points at the block."),
    PUPPET("Puppetry", 0xd6c8a8, End.BEING, End.BEING, "puppet", "lead",
            "The second is forced to move exactly as the first moves."),
    SUPERNOVA("Supernova", 0xffd23f, End.PLACE, End.ANY, "supernova", "nether_star",
            "The death of a star is grafted onto the target. It collapses, then detonates."),
    LIFE("Life", 0xff4f8b, End.BEING, End.BEING, "life", "golden_apple",
            "The first being's life now lives in the second. Kill the second and the first dies."),
    LOCATION("Location", 0x2fd6c3, End.PLACE, End.PLACE, "location", "recovery_compass",
            "Two whole areas trade places, blocks and chests included, until you let go."),
    ABILITY("Ability", 0xf2a7ff, End.BEING, End.BEING, "ability", "breeze_rod",
            "Your power (or a creature's) is grafted onto another, sustained by your spirit."),
    STORAGE("Storage", 0xc98b4a, End.PLACE, End.BEING, "storage", "ender_chest",
            "A container becomes part of the being's inventory: open it anywhere, overflow lands in it.");

    /** What an end of the thread may be tied to. */
    public enum End {
        PLACE("a block"), BEING("a being"), ANY("a block or a being");

        private final String article;

        End(String article) {
            this.article = article;
        }

        public String article() {
            return article;
        }
    }

    private final String display;
    private final Color color;
    private final End first;
    private final End second;
    private final String id;
    private final String model;
    private final String description;

    Mode(String display, int rgb, End first, End second, String id, String model, String description) {
        this.display = display;
        this.color = Color.fromRGB(rgb);
        this.first = first;
        this.second = second;
        this.id = id;
        this.model = model;
        this.description = description;
    }

    public String display() {
        return display;
    }

    public Color color() {
        return color;
    }

    /** Opening colour tag for MiniMessage, e.g. {@code <color:#9b6bff>}; close with {@code </color>}. */
    public String tag() {
        return String.format("<color:#%06x>", color.asRGB());
    }

    public End first() {
        return first;
    }

    public End second() {
        return second;
    }

    /** Lowercase id used in config keys, commands and the resource pack. */
    public String id() {
        return id;
    }

    /** The vanilla item the thread looks like in this mode, e.g. {@code ender_pearl}. */
    public String model() {
        return model;
    }

    public String description() {
        return description;
    }

    public String shape() {
        return name(first) + " ➜ " + name(second);
    }

    public Mode next() {
        Mode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public Mode previous() {
        Mode[] all = values();
        return all[(ordinal() + all.length - 1) % all.length];
    }

    public static Mode byId(String id) {
        for (Mode mode : values()) {
            if (mode.id.equalsIgnoreCase(id) || mode.name().equalsIgnoreCase(id)) return mode;
        }
        return null;
    }

    private static String name(End end) {
        return switch (end) {
            case PLACE -> "Place";
            case BEING -> "Being";
            case ANY -> "Anything";
        };
    }
}
