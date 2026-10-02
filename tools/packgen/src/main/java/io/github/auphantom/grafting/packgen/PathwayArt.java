package io.github.auphantom.grafting.packgen;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import static io.github.auphantom.grafting.packgen.Pixels.*;

/**
 * Art for the pathway book: the parchment book drawn behind a chest GUI, the Fool sigil,
 * stat-bar segments for tooltips, the cyan "mystery" tooltip frame, and the fonts.
 * <p>
 * Custom GUI art in vanilla works through fonts: the chest title is a text component, and
 * a bitmap-font glyph can be a whole picture. Negative-width "space" glyphs move the pen
 * around, and copies of the vanilla font with different ascents put text on any pixel row.
 * The plugin side of this is {@code io.github.auphantom.grafting.ui.GuiTitle}.
 */
final class PathwayArt {

    private PathwayArt() {
    }

    /** Book is drawn over the top part of a 6-row chest GUI (176 wide, the player inventory stays below). */
    static final int BOOK_W = 176;
    static final int BOOK_H = 128;
    /** Text rows we generate fonts for: any y in this range can carry a line of text. */
    static final int ROW_MIN = 5;
    static final int ROW_MAX = 125;

    static final Color LEATHER = new Color(0x5c3519);
    static final Color LEATHER_HI = new Color(0x7d4c25);
    static final Color LEATHER_DK = new Color(0x3d220e);
    static final Color OUTLINE = new Color(0x22130a);
    static final Color PAGE = new Color(0xf3deb3);
    static final Color PAGE_SHADE = new Color(0xe2c38f);
    static final Color PAGE_LINE = new Color(0xc49c62);
    static final Color GOLD = new Color(0xd9a646);
    static final Color GOLD_DK = new Color(0x8c6224);
    static final Color TAB = new Color(0xb85f2b);
    static final Color TAB_HI = new Color(0xdb8446);
    static final Color VOID = new Color(0x17102a);
    static final Color VOID_HI = new Color(0x2c1f4d);

    /** Vanilla ascii.png layout (16x16 cells); referenced by our row fonts, not copied. */
    static final String[] ASCII_ROWS = {
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000",
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000",
            " !\"#$%&'()*+,-./", "0123456789:;<=>?", "@ABCDEFGHIJKLMNO", "PQRSTUVWXYZ[\\]^_",
            "`abcdefghijklmno", "pqrstuvwxyz{|}~\u0000",
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000",
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u00a3\u0000\u0000\u0192",
            "\u0000\u0000\u0000\u0000\u0000\u0000\u00aa\u00ba\u0000\u0000\u00ac\u0000\u0000\u0000\u00ab\u00bb",
            "\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510",
            "\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567",
            "\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580",
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u2205\u2208\u0000",
            "\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u0000\u221a\u207f\u00b2\u25a0\u0000"};

    /** Writes every pathway asset into the pack and returns the item icons for the preview sheet. */
    static Map<String, BufferedImage> write(ZipOutputStream zip) throws IOException {
        Map<String, BufferedImage> previews = new LinkedHashMap<>();

        BufferedImage sigil = sigil();
        PackGenerator.itemModel(zip, "fool_sigil", sigil, "generated");
        previews.put("fool_sigil", sigil);

        png(zip, "assets/grafting/textures/gui/book.png", book());
        png(zip, "assets/grafting/textures/font/seg_full.png", segment(true));
        png(zip, "assets/grafting/textures/font/seg_empty.png", segment(false));
        png(zip, "assets/grafting/textures/font/bracket_l.png", bracket(true));
        png(zip, "assets/grafting/textures/font/bracket_r.png", bracket(false));

        text(zip, "assets/grafting/font/gui.json", "{\"providers\":[" + spaceProvider() + ","
                + bitmap("grafting:gui/book.png", 13, BOOK_H, "\ue100") + "]}");

        text(zip, "assets/grafting/font/bar.json", "{\"providers\":["
                + bitmap("grafting:font/seg_full.png", 7, 8, "\ue000") + ","
                + bitmap("grafting:font/seg_empty.png", 7, 8, "\ue001") + ","
                + bitmap("grafting:font/bracket_l.png", 7, 8, "\ue002") + ","
                + bitmap("grafting:font/bracket_r.png", 7, 8, "\ue003") + "]}");

        // One copy of the vanilla font per pixel row, so the title can carry text at any height.
        // The title is drawn with its glyph tops at y = 6 + 7 - ascent, so ascent = 13 - y.
        for (int y = ROW_MIN; y <= ROW_MAX; y++) {
            text(zip, "assets/grafting/font/row_" + y + ".json", "{\"providers\":["
                    + "{\"type\":\"space\",\"advances\":{\" \":4}},"
                    + "{\"type\":\"bitmap\",\"file\":\"minecraft:font/ascii.png\",\"ascent\":" + (13 - y)
                    + ",\"chars\":" + jsonArray(ASCII_ROWS) + "}]}");
        }

        png(zip, "assets/grafting/textures/gui/sprites/tooltip/mystery_background.png", tooltipBackground());
        png(zip, "assets/grafting/textures/gui/sprites/tooltip/mystery_frame.png", tooltipFrame());
        text(zip, "assets/grafting/textures/gui/sprites/tooltip/mystery_background.png.mcmeta", """
                { "gui": { "scaling": { "type": "nine_slice", "width": 100, "height": 100, "border": 9 } } }
                """);
        text(zip, "assets/grafting/textures/gui/sprites/tooltip/mystery_frame.png.mcmeta", """
                { "gui": { "scaling": { "type": "nine_slice", "width": 100, "height": 100, "border": 10, "stretch_inner": true } } }
                """);
        return previews;
    }

    // -------------------------------------------------------------- the book

    static BufferedImage book() {
        BufferedImage img = new BufferedImage(BOOK_W, BOOK_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // Leather cover.
        g.setColor(OUTLINE);
        g.fillRoundRect(0, 5, BOOK_W, BOOK_H - 5, 10, 10);
        g.setColor(LEATHER);
        g.fillRoundRect(1, 6, BOOK_W - 2, BOOK_H - 7, 8, 8);
        g.setColor(LEATHER_HI);
        g.drawLine(5, 7, BOOK_W - 6, 7);
        g.setColor(LEATHER_DK);
        g.drawLine(5, BOOK_H - 2, BOOK_W - 6, BOOK_H - 2);
        for (int[] c : new int[][]{{4, 9}, {BOOK_W - 6, 9}, {4, BOOK_H - 5}, {BOOK_W - 6, BOOK_H - 5}}) {
            g.setColor(GOLD_DK);
            g.fillRect(c[0], c[1], 2, 2);
            g.setColor(GOLD);
            g.fillRect(c[0], c[1], 1, 1);
        }

        // Stacked sheets under the open pages.
        for (int i = 0; i < 2; i++) {
            g.setColor(i % 2 == 0 ? PAGE_SHADE : PAGE_LINE);
            g.fillRect(6, 124 + i, 80, 1);
            g.fillRect(90, 124 + i, 80, 1);
        }
        page(g, 5, 11, 82, 113, true);
        page(g, 89, 11, 82, 113, false);
        // Spine.
        g.setColor(LEATHER_DK);
        g.fillRect(86, 10, 4, 115);
        g.setColor(GOLD_DK);
        for (int y = 14; y < 122; y += 6) g.fillRect(87, y, 2, 2);

        // Top tab (the plugin writes "PATHWAY" onto it).
        g.setColor(OUTLINE);
        g.fillRoundRect(55, 0, 66, 16, 6, 6);
        g.setColor(TAB);
        g.fillRoundRect(56, 1, 64, 14, 4, 4);
        g.setColor(TAB_HI);
        g.drawLine(58, 2, 117, 2);
        g.setColor(shade(TAB, 0.75));
        g.drawLine(58, 13, 117, 13);

        // Left page: portrait frame around the player's head (slot row 1, column 1).
        ornateFrame(g, 21, 31, 26, 26);
        // Active thread slots (rows 4-5, columns 0-3), under the "Active threads" heading.
        for (int r = 4; r <= 5; r++) for (int c = 0; c <= 3; c++) slot(g, 8 + 18 * c, 18 + 18 * r);

        // Right page: ability slots (rows 1-3, columns 5-7) between two dividers.
        divider(g, 94, 30, 72);
        for (int r = 1; r <= 3; r++) for (int c = 5; c <= 7; c++) slot(g, 8 + 18 * c, 18 + 18 * r);
        divider(g, 94, 89, 72);
        // Faint Fool sigil watermark in the empty column beside the abilities.
        BufferedImage mark = sigil();
        for (int x = 0; x < S; x += 2) {
            for (int y = 0; y < S; y += 2) {
                int argb = mark.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                int px = 150 + x / 2, py = 52 + y / 2;
                img.setRGB(px, py, blendColor(new Color(img.getRGB(px, py)), new Color(argb), 0.22).getRGB());
            }
        }
        g.dispose();
        return img;
    }

    private static void page(Graphics2D g, int x, int y, int w, int h, boolean left) {
        g.setColor(PAGE_LINE);
        g.fillRect(x, y, w, h);
        g.setColor(PAGE);
        g.fillRect(x + 1, y + 1, w - 2, h - 2);
        for (int i = 0; i < 6; i++) {
            g.setColor(blendColor(PAGE, PAGE_SHADE, 1 - i / 6.0));
            g.fillRect(left ? x + w - 2 - i : x + 1 + i, y + 1, 1, h - 2);
        }
        g.setColor(PAGE_SHADE);
        g.drawRect(x + 3, y + 3, w - 7, h - 7);
    }

    /** A recessed slot square: 18x18 including its border, interior 16x16 at (x, y). */
    private static void slot(Graphics2D g, int x, int y) {
        g.setColor(PAGE_LINE);
        g.fillRect(x - 1, y - 1, 18, 18);
        g.setColor(PAGE_SHADE);
        g.fillRect(x, y, 16, 16);
        g.setColor(new Color(0xd0ad73));
        g.drawLine(x, y, x + 15, y);
        g.drawLine(x, y, x, y + 15);
        g.setColor(new Color(0xf8e8c6));
        g.drawLine(x, y + 16, x + 16, y + 16);
        g.drawLine(x + 16, y, x + 16, y + 16);
    }

    private static void ornateFrame(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(GOLD_DK);
        g.fillRect(x, y, w, h);
        g.setColor(GOLD);
        g.fillRect(x + 1, y + 1, w - 2, h - 2);
        g.setColor(VOID);
        g.fillRect(x + 3, y + 3, w - 6, h - 6);
        g.setColor(VOID_HI);
        g.drawRect(x + 3, y + 3, w - 7, h - 7);
        g.setColor(GOLD);
        g.fillRect(x + w / 2 - 1, y - 2, 2, 2);
        g.fillRect(x + w / 2 - 1, y + h, 2, 2);
        g.fillRect(x - 2, y + h / 2 - 1, 2, 2);
        g.fillRect(x + w, y + h / 2 - 1, 2, 2);
        g.setColor(new Color(0xb9a6ff));
        g.fillRect(x + 5, y + 6, 1, 1);
        g.fillRect(x + w - 7, y + h - 8, 1, 1);
    }

    private static void divider(Graphics2D g, int x, int y, int w) {
        g.setColor(PAGE_LINE);
        g.drawLine(x, y, x + w - 1, y);
        int mid = x + w / 2;
        g.setColor(GOLD_DK);
        g.fillRect(mid - 2, y - 1, 4, 3);
        g.setColor(GOLD);
        g.fillRect(mid - 1, y - 2, 2, 5);
    }

    // -------------------------------------------------------------- the Fool sigil

    /** A theatre mask split light/dark in a gold-rimmed violet medallion, under a single star. */
    static BufferedImage sigil() {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        Color violet = new Color(0x4b2a8c), violetHi = new Color(0x7b5cff), deep = new Color(0x1c1036);
        g.setColor(GOLD_DK);
        g.fillOval(2, 2, 28, 28);
        g.setColor(GOLD);
        g.fillOval(3, 3, 26, 26);
        g.setColor(deep);
        g.fillOval(5, 5, 22, 22);
        g.setColor(violet);
        g.fillOval(6, 6, 20, 20);
        g.setColor(violetHi);
        g.drawArc(7, 7, 18, 18, 100, 80);
        Color pale = new Color(0xf4efff), dim = new Color(0xa79ccc);
        g.setColor(pale);
        g.fillRoundRect(10, 11, 7, 12, 5, 6);
        g.setColor(dim);
        g.fillRoundRect(15, 11, 7, 12, 5, 6);
        g.setColor(pale);
        g.fillRect(12, 11, 4, 12);
        g.setColor(deep);
        g.fillRect(12, 15, 2, 2);
        g.fillRect(18, 15, 2, 2);
        g.fillRect(13, 20, 1, 1);
        g.fillRect(14, 21, 4, 1);
        g.fillRect(18, 20, 1, 1);
        g.setColor(new Color(0x7fd8ff));
        g.fillRect(19, 18, 1, 1);
        g.setColor(GOLD);
        g.fillRect(15, 6, 2, 3);
        g.fillRect(14, 7, 4, 1);
        g.setColor(Color.WHITE);
        g.fillRect(15, 7, 1, 1);
        g.dispose();
        outline(img, new Color(0x0e0818));
        return img;
    }

    // -------------------------------------------------------------- bars and tooltip

    /** One stat-bar segment, white so the text colour tints it. 3x8 cell, 7 rows drawn. */
    static BufferedImage segment(boolean full) {
        BufferedImage img = new BufferedImage(3, 8, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 7; y++) {
                int argb = full ? (y == 0 ? 0xffffffff : y == 6 ? 0xff9a9a9a : 0xffdedede)
                        : (((x + y) % 2 == 0) ? 0xff4a4a4a : 0);
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    static BufferedImage bracket(boolean left) {
        BufferedImage img = new BufferedImage(2, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 7; y++) img.setRGB(left ? 0 : 1, y, 0xffcfcfcf);
        img.setRGB(left ? 1 : 0, 0, 0xffcfcfcf);
        img.setRGB(left ? 1 : 0, 6, 0xffcfcfcf);
        return img;
    }

    static BufferedImage tooltipBackground() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        int fill = 0xf2050810;
        for (int x = 9; x <= 90; x++) for (int y = 9; y <= 90; y++) img.setRGB(x, y, fill);
        for (int x = 10; x <= 89; x++) { img.setRGB(x, 8, fill); img.setRGB(x, 91, fill); }
        for (int y = 10; y <= 89; y++) { img.setRGB(8, y, fill); img.setRGB(91, y, fill); }
        return img;
    }

    static BufferedImage tooltipFrame() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        int outer = 0xff6fe6ee, inner = 0x9033a7b0;
        for (int i = 9; i <= 90; i++) {
            img.setRGB(i, 9, outer); img.setRGB(i, 90, outer); img.setRGB(9, i, outer); img.setRGB(90, i, outer);
        }
        for (int i = 10; i <= 89; i++) {
            img.setRGB(i, 10, inner); img.setRGB(i, 89, inner); img.setRGB(10, i, inner); img.setRGB(89, i, inner);
        }
        return img;
    }

    // -------------------------------------------------------------- font json

    /** Pen-moving glyphs: U+F801..F809 move left 1,2,4..256 px, U+F821..F829 move right. */
    private static String spaceProvider() {
        StringBuilder sb = new StringBuilder("{\"type\":\"space\",\"advances\":{");
        for (int i = 0; i <= 8; i++) {
            int v = 1 << i;
            sb.append(String.format("\"\\u%04x\":%d,\"\\u%04x\":%d%s", 0xF801 + i, -v, 0xF821 + i, v, i < 8 ? "," : ""));
        }
        return sb.append("}}").toString();
    }

    private static String bitmap(String file, int ascent, int height, String ch) {
        return "{\"type\":\"bitmap\",\"file\":\"" + file + "\",\"ascent\":" + ascent + ",\"height\":" + height
                + ",\"chars\":[" + json(ch) + "]}";
    }

    private static String jsonArray(String[] rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.length; i++) sb.append(i == 0 ? "" : ",").append(json(rows[i]));
        return sb.append(']').toString();
    }

    private static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    private static Color blendColor(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
