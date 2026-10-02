package io.github.auphantom.grafting.ui;

import io.github.auphantom.grafting.graft.Mode;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Segmented stat bars and coloured labels for the "Mystery Arts" tooltips. */
public final class Bars {

    private static final Key BAR_FONT = Key.key("grafting", "bar");
    private static final String FULL = "\ue000";
    private static final String EMPTY = "\ue001";
    private static final String LEFT = "\ue002";
    private static final String RIGHT = "\ue003";
    private static final TextColor BRACKET = TextColor.color(0xcfcfcf);
    private static final TextColor EMPTY_COLOR = TextColor.color(0x5a5a5a);

    private Bars() {
    }

    /** A bar of {@code segments} cells, filled to {@code fraction}, in one colour. */
    public static Component bar(double fraction, int segments, TextColor color) {
        return bar(fraction, segments, color, color);
    }

    /** A bar whose filled cells fade from {@code from} (left) to {@code to} (right). */
    public static Component bar(double fraction, int segments, TextColor from, TextColor to) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * segments);
        TextComponent.Builder b = Component.text().font(BAR_FONT).decoration(TextDecoration.ITALIC, false);
        b.append(Component.text(LEFT, BRACKET));
        for (int i = 0; i < segments; i++) {
            if (i < filled) {
                double t = segments == 1 ? 0 : (double) i / (segments - 1);
                b.append(Component.text(FULL, TextColor.lerp((float) t, from, to)));
            } else {
                b.append(Component.text(EMPTY, EMPTY_COLOR));
            }
        }
        b.append(Component.text(RIGHT, BRACKET));
        return b.build();
    }

    /** "Label: value" in two colours, the way the reference tooltip writes its stats. */
    public static Component stat(String label, String value, TextColor labelColor, TextColor valueColor) {
        return Component.text().decoration(TextDecoration.ITALIC, false)
                .append(Component.text(label + ": ", labelColor))
                .append(Component.text(value, valueColor)).build();
    }

    public static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public static TextColor color(Mode mode) {
        return TextColor.color(mode.color().asRGB());
    }

    /** The mode's colour darkened enough to read on parchment. */
    public static TextColor ink(Mode mode) {
        TextColor c = color(mode);
        return TextColor.color((int) (c.red() * 0.62), (int) (c.green() * 0.62), (int) (c.blue() * 0.62));
    }

    public static final TextColor MYSTERY = TextColor.color(0xd84bff);
    public static final TextColor FOOL = TextColor.color(0xb26bff);
    public static final TextColor CYAN = TextColor.color(0x6fe6ee);
    public static final TextColor GREEN_HI = TextColor.color(0x3be05a);
    public static final TextColor GREEN_LO = TextColor.color(0x1f9e36);
    public static final TextColor YELLOW = TextColor.color(0xffd23f);
    public static final TextColor ORANGE = TextColor.color(0xff7a1a);
    public static final TextColor RED = TextColor.color(0xe02a2a);
    public static final TextColor WHITE = NamedTextColor.WHITE;
    public static final TextColor GRAY = NamedTextColor.GRAY;
}
