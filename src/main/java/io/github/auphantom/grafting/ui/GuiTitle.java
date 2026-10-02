package io.github.auphantom.grafting.ui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Builds chest-GUI titles that paint a picture and text at exact pixel positions.
 * <p>
 * Vanilla only lets a server send a container <em>title</em>. With the resource pack, that title
 * becomes a canvas: one glyph of the {@code grafting:gui} font is the whole book image, the
 * private-use "space" glyphs move the pen left or right by any number of pixels, and the
 * {@code grafting:row_<y>} fonts are copies of the vanilla font that sit at pixel row y.
 * Coordinates here are relative to the top-left corner of the chest GUI.
 */
public final class GuiTitle {

    public static final Key GUI_FONT = Key.key("grafting", "gui");
    /** The vanilla title starts 8px in from the left edge of the GUI. */
    private static final int ORIGIN_X = 8;
    private static final String BOOK = "\ue100";
    public static final int BOOK_WIDTH = 176;

    private final TextComponent.Builder out = Component.text();
    private int pen = 0;

    /** Draws the book at the top-left corner of the GUI. */
    public GuiTitle book() {
        moveTo(0);
        out.append(Component.text(BOOK).font(GUI_FONT).color(NamedTextColor.WHITE));
        pen += BOOK_WIDTH + 1; // bitmap glyphs advance by width + 1
        return this;
    }

    /** Text with its top-left pixel at (x, y). */
    public GuiTitle text(int x, int y, String text, TextColor color) {
        return text(x, y, text, color, false);
    }

    public GuiTitle text(int x, int y, String text, TextColor color, boolean shadow) {
        moveTo(x);
        Component c = Component.text(text).font(Key.key("grafting", "row_" + clampRow(y))).color(color)
                .decoration(TextDecoration.ITALIC, false);
        if (shadow) {
            // A one-pixel darker copy underneath, like vanilla text shadow (titles have none).
            out.append(Component.text(text).font(Key.key("grafting", "row_" + clampRow(y + 1)))
                    .color(darker(color)));
            pen += Glyphs.width(text);
            moveTo(x + 1);
        }
        out.append(c);
        pen += Glyphs.width(text);
        return this;
    }

    /** Text horizontally centred on {@code centerX}. */
    public GuiTitle centered(int centerX, int y, String text, TextColor color) {
        return centered(centerX, y, text, color, false);
    }

    public GuiTitle centered(int centerX, int y, String text, TextColor color, boolean shadow) {
        return text(centerX - Glyphs.width(text) / 2, y, text, color, shadow);
    }

    /** Leaves the pen where vanilla expects nothing more, by moving it back to the start. */
    public Component build() {
        moveTo(0);
        return out.build();
    }

    private void moveTo(int x) {
        int delta = (x - ORIGIN_X) - pen;
        if (delta != 0) out.append(Component.text(Glyphs.shift(delta)).font(GUI_FONT));
        pen += delta;
    }

    private static int clampRow(int y) {
        return Math.max(5, Math.min(125, y));
    }

    private static TextColor darker(TextColor c) {
        return TextColor.color((int) (c.red() * 0.25), (int) (c.green() * 0.25), (int) (c.blue() * 0.25));
    }
}
