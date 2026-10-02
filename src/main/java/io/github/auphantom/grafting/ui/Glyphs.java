package io.github.auphantom.grafting.ui;

/**
 * Pixel widths of the vanilla font, and pen-moving glyphs from the {@code grafting:gui} font.
 * Lets the plugin lay out text inside the book without the client's help.
 */
public final class Glyphs {

    /** Advance of every printable ASCII glyph (char 32..126) in the vanilla font, in pixels. */
    private static final int[] ADVANCE = new int[128];

    static {
        java.util.Arrays.fill(ADVANCE, 6);
        String narrow = " 4!2\"4'2(4)4*4,2.2:2;2<5>5@7I4[4]4`3f5i2k5l3t4{4|2}4~7";
        for (int i = 0; i < narrow.length(); i += 2) ADVANCE[narrow.charAt(i)] = narrow.charAt(i + 1) - '0';
    }

    private Glyphs() {
    }

    public static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            w += c < 128 ? ADVANCE[c] : 6;
        }
        return w;
    }

    /** A run of space glyphs that moves the pen by {@code px} pixels (negative = left). */
    public static String shift(int px) {
        StringBuilder sb = new StringBuilder();
        char base = px < 0 ? '\uF801' : '\uF821';
        int left = Math.abs(px);
        for (int bit = 8; bit >= 0; bit--) {
            int v = 1 << bit;
            while (left >= v) {
                sb.append((char) (base + bit));
                left -= v;
            }
        }
        return sb.toString();
    }

    /** Cuts text so it fits in {@code maxWidth} pixels, adding ".." if anything was removed. */
    public static String fit(String text, int maxWidth) {
        if (width(text) <= maxWidth) return text;
        String cut = text;
        while (!cut.isEmpty() && width(cut + "..") > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "..";
    }
}
