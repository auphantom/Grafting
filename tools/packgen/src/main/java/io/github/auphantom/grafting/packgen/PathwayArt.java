package io.github.auphantom.grafting.packgen;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

import static io.github.auphantom.grafting.packgen.Pixels.*;

/**
 * Art for the pathway menu: the Fool sigil, stat-bar segments for tooltips (a tiny bitmap font,
 * so one line of lore can draw a segmented bar), and the cyan "mystery" tooltip frame.
 */
final class PathwayArt {

    private PathwayArt() {
    }

    static final Color GOLD = new Color(0xd9a646);
    static final Color GOLD_DK = new Color(0x8c6224);

    /** Writes every pathway asset into the pack. */
    static void write(ZipOutputStream zip) throws IOException {
        PackGenerator.itemModel(zip, "fool_sigil", sigil(), "generated");

        png(zip, "assets/grafting/textures/font/seg_full.png", segment(true));
        png(zip, "assets/grafting/textures/font/seg_empty.png", segment(false));
        png(zip, "assets/grafting/textures/font/bracket_l.png", bracket(true));
        png(zip, "assets/grafting/textures/font/bracket_r.png", bracket(false));
        text(zip, "assets/grafting/font/bar.json", "{\"providers\":["
                + bitmap("grafting:font/seg_full.png", 7, 8, "\ue000") + ","
                + bitmap("grafting:font/seg_empty.png", 7, 8, "\ue001") + ","
                + bitmap("grafting:font/bracket_l.png", 7, 8, "\ue002") + ","
                + bitmap("grafting:font/bracket_r.png", 7, 8, "\ue003") + "]}");

        png(zip, "assets/grafting/textures/gui/sprites/tooltip/mystery_background.png", tooltipBackground());
        png(zip, "assets/grafting/textures/gui/sprites/tooltip/mystery_frame.png", tooltipFrame());
        text(zip, "assets/grafting/textures/gui/sprites/tooltip/mystery_background.png.mcmeta", """
                { "gui": { "scaling": { "type": "nine_slice", "width": 100, "height": 100, "border": 9 } } }
                """);
        text(zip, "assets/grafting/textures/gui/sprites/tooltip/mystery_frame.png.mcmeta", """
                { "gui": { "scaling": { "type": "nine_slice", "width": 100, "height": 100, "border": 10, "stretch_inner": true } } }
                """);
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

    private static String bitmap(String file, int ascent, int height, String ch) {
        return "{\"type\":\"bitmap\",\"file\":\"" + file + "\",\"ascent\":" + ascent + ",\"height\":" + height
                + ",\"chars\":[" + json(ch) + "]}";
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
}
