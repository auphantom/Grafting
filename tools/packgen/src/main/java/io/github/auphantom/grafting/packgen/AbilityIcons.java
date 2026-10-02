package io.github.auphantom.grafting.packgen;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static io.github.auphantom.grafting.packgen.Pixels.*;

/**
 * The ability icons. Every icon shares one motif (a spool of thread, recoloured
 * per ability) with a symbol of the concept being tampered with stitched on top.
 */
final class AbilityIcons {

    private AbilityIcons() {
    }

    /** Icon id -> 32x32 image, in ability order. */
    static Map<String, BufferedImage> all() {
        Map<String, BufferedImage> icons = new LinkedHashMap<>();
        icons.put("distance", icon(0x9b6bff, AbilityIcons::distance));
        icons.put("fate", icon(0xc81e3c, AbilityIcons::fate));
        icons.put("nature", icon(0x4fc95a, AbilityIcons::nature));
        icons.put("return", icon(0x3cd2a0, AbilityIcons::returnHome));
        icons.put("exchange", icon(0x3fa9ff, AbilityIcons::exchange));
        icons.put("enmity", icon(0xff8a1f, AbilityIcons::enmity));
        icons.put("gravity", icon(0x6a5cff, AbilityIcons::gravity));
        icons.put("puppet", icon(0xd6c8a8, AbilityIcons::puppet));
        icons.put("supernova", icon(0xffd23f, AbilityIcons::supernova));
        icons.put("life", icon(0xff4f8b, AbilityIcons::life));
        icons.put("location", icon(0x2fd6c3, AbilityIcons::location));
        icons.put("ability", icon(0xf2a7ff, AbilityIcons::ability));
        icons.put("storage", icon(0xc98b4a, AbilityIcons::storage));
        return icons;
    }

    // ------------------------------------------------------------------ shared motif

    /** Common frame: a spool of thread in the ability colour, then the ability symbol. */
    static BufferedImage icon(int rgb, Consumer<Graphics2D> symbol) {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // Pixel art: no anti-aliasing, crisp edges.
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        Color base = new Color(rgb);

        // Spool (bottom left): wooden caps with wound thread between them.
        g.setColor(new Color(0x5a3a22));
        g.fillRect(2, 21, 11, 2);
        g.fillRect(2, 29, 11, 2);
        g.setColor(new Color(0x8a5a34));
        g.fillRect(3, 21, 9, 1);
        g.fillRect(3, 29, 9, 1);
        for (int y = 23; y < 29; y++) {
            g.setColor(y % 2 == 0 ? base : shade(base, 0.72));
            g.fillRect(3, y, 9, 1);
        }
        g.setColor(tint(base, 0.45));
        g.fillRect(4, 24, 2, 1);
        g.fillRect(4, 26, 1, 1);

        // A loose thread rising from the spool to the symbol.
        g.setColor(base);
        int[][] path = {{11, 23}, {12, 22}, {13, 21}, {14, 20}, {14, 19}, {15, 18}};
        for (int[] p : path) g.fillRect(p[0], p[1], 1, 1);

        symbol.accept(g);
        g.dispose();
        outline(img, new Color(0x16121f));
        return img;
    }

    // ------------------------------------------------------------------ symbols

    /** Two dots folded together by an arc: distance collapsing to zero. */
    static void distance(Graphics2D g) {
        Color c = new Color(0x9b6bff), l = tint(c, 0.5);
        g.setColor(c);
        g.setStroke(new BasicStroke(2));
        g.draw(new Arc2D.Double(12, 4, 16, 14, 0, 180, Arc2D.OPEN));
        g.setColor(l);
        g.fillRect(12, 10, 4, 4);
        g.fillRect(25, 10, 4, 4);
        g.setColor(Color.WHITE);
        g.fillRect(13, 11, 1, 1);
        g.fillRect(26, 11, 1, 1);
        // Little portal swirl in the middle.
        g.setColor(tint(c, 0.25));
        g.fillRect(19, 13, 3, 3);
        g.setColor(new Color(0x2a1452));
        g.fillRect(20, 14, 1, 1);
    }

    /** A cracked heart with a red thread pulled through it. */
    static void fate(Graphics2D g) {
        Color c = new Color(0xc81e3c);
        heart(g, 16, 4, c);
        g.setColor(new Color(0x3a0812));
        int[][] crack = {{21, 6}, {20, 8}, {22, 10}, {21, 12}, {22, 14}};
        for (int[] p : crack) g.fillRect(p[0], p[1], 1, 1);
        g.setColor(tint(c, 0.6));
        g.fillRect(18, 6, 2, 1);
        g.fillRect(17, 7, 1, 1);
    }

    /** A leaf and a crystal shard sharing one stem: a being taking on a block's nature. */
    static void nature(Graphics2D g) {
        Color green = new Color(0x4fc95a);
        g.setColor(shade(green, 0.7));
        Polygon leaf = new Polygon(new int[]{15, 21, 25, 21}, new int[]{14, 4, 10, 16}, 4);
        g.fillPolygon(leaf);
        g.setColor(green);
        g.fillPolygon(new Polygon(new int[]{17, 21, 23, 20}, new int[]{13, 6, 10, 14}, 4));
        g.setColor(tint(green, 0.5));
        g.drawLine(16, 14, 22, 7);
        // Ice-blue shard (block nature).
        Color ice = new Color(0x9fe3ff);
        g.setColor(shade(ice, 0.7));
        g.fillPolygon(new Polygon(new int[]{24, 29, 30, 25}, new int[]{12, 9, 17, 19}, 4));
        g.setColor(ice);
        g.fillPolygon(new Polygon(new int[]{25, 28, 29, 26}, new int[]{13, 11, 16, 17}, 4));
        g.setColor(Color.WHITE);
        g.fillRect(26, 13, 1, 2);
    }

    /** A skull with a curved arrow looping back home. */
    static void returnHome(Graphics2D g) {
        Color bone = new Color(0xe8e4d4);
        g.setColor(bone);
        g.fillRoundRect(16, 4, 10, 8, 3, 3);
        g.fillRect(18, 12, 6, 2);
        g.setColor(new Color(0x16121f));
        g.fillRect(18, 7, 2, 2);
        g.fillRect(22, 7, 2, 2);
        g.fillRect(20, 10, 2, 1);
        g.fillRect(19, 13, 1, 1);
        g.fillRect(21, 13, 1, 1);
        Color c = new Color(0x3cd2a0);
        g.setColor(c);
        g.setStroke(new BasicStroke(2));
        g.draw(new Arc2D.Double(14, 2, 16, 18, 200, 230, Arc2D.OPEN));
        g.fillPolygon(new Polygon(new int[]{26, 31, 28}, new int[]{15, 15, 19}, 3));
    }

    /** Two arrows chasing each other in a circle: positions traded. */
    static void exchange(Graphics2D g) {
        Color c = new Color(0x3fa9ff), l = tint(c, 0.45);
        g.setStroke(new BasicStroke(2));
        g.setColor(c);
        g.draw(new Arc2D.Double(14, 3, 14, 14, 20, 140, Arc2D.OPEN));
        g.fillPolygon(new Polygon(new int[]{13, 18, 14}, new int[]{8, 9, 13}, 3));
        g.setColor(l);
        g.draw(new Arc2D.Double(14, 3, 14, 14, 200, 140, Arc2D.OPEN));
        g.fillPolygon(new Polygon(new int[]{29, 24, 28}, new int[]{12, 11, 7}, 3));
        g.setColor(Color.WHITE);
        g.fillRect(20, 9, 2, 2);
    }

    /** A glaring eye with a crosshair: hostility redirected. */
    static void enmity(Graphics2D g) {
        Color c = new Color(0xff8a1f);
        g.setColor(c);
        g.fillOval(13, 5, 17, 10);
        g.setColor(new Color(0xfff1d6));
        g.fillOval(16, 7, 11, 6);
        g.setColor(new Color(0xc81e3c));
        g.fillRect(20, 7, 3, 6);
        g.setColor(new Color(0x16121f));
        g.fillRect(21, 8, 1, 4);
        // Angry brow.
        g.setColor(shade(c, 0.6));
        g.drawLine(14, 4, 19, 6);
        g.drawLine(29, 4, 24, 6);
        // Crosshair ticks.
        g.setColor(tint(c, 0.4));
        g.fillRect(21, 1, 1, 3);
        g.fillRect(21, 16, 1, 3);
    }

    /** A block with field lines curving into it: 'down' redirected. */
    static void gravity(Graphics2D g) {
        Color c = new Color(0x6a5cff);
        g.setColor(new Color(0x2a2340));
        g.fillRect(19, 7, 6, 6);
        g.setColor(tint(c, 0.25));
        g.fillRect(20, 8, 4, 4);
        g.setColor(tint(c, 0.6));
        g.fillRect(20, 8, 2, 1);
        g.setColor(c);
        g.setStroke(new BasicStroke(1));
        g.draw(new Arc2D.Double(13, 1, 18, 18, 0, 360, Arc2D.OPEN));
        // Arrows pointing inward from 4 sides.
        g.setColor(Color.WHITE);
        g.fillPolygon(new Polygon(new int[]{21, 23, 22}, new int[]{3, 3, 5}, 3));
        g.fillPolygon(new Polygon(new int[]{21, 23, 22}, new int[]{17, 17, 15}, 3));
        g.fillPolygon(new Polygon(new int[]{15, 15, 17}, new int[]{9, 11, 10}, 3));
        g.fillPolygon(new Polygon(new int[]{29, 29, 27}, new int[]{9, 11, 10}, 3));
    }

    /** A marionette cross bar with strings to a little figure. */
    static void puppet(Graphics2D g) {
        Color wood = new Color(0x8a5a34);
        g.setColor(wood);
        g.fillRect(14, 2, 15, 2);
        g.fillRect(20, 0, 3, 6);
        g.setColor(new Color(0xe8e4d4));
        g.drawLine(15, 4, 17, 12);
        g.drawLine(28, 4, 26, 12);
        g.drawLine(21, 6, 21, 8);
        // Figure.
        Color c = new Color(0xd6c8a8);
        g.setColor(c);
        g.fillRect(20, 8, 3, 3);
        g.setColor(new Color(0x7b5cff));
        g.fillRect(19, 11, 5, 5);
        g.setColor(c);
        g.fillRect(17, 12, 2, 1);
        g.fillRect(24, 12, 2, 1);
        g.fillRect(19, 16, 2, 3);
        g.fillRect(22, 16, 2, 3);
        g.setColor(new Color(0x16121f));
        g.fillRect(20, 9, 1, 1);
        g.fillRect(22, 9, 1, 1);
    }

    /** A blazing star with rays and a white-hot core. */
    static void supernova(Graphics2D g) {
        Color gold = new Color(0xffd23f), orange = new Color(0xff8a1f), red = new Color(0xff4d2e);
        int cx = 21, cy = 10;
        g.setColor(red);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4;
            int len = i % 2 == 0 ? 10 : 7;
            g.setStroke(new BasicStroke(i % 2 == 0 ? 2 : 1));
            g.drawLine(cx, cy, cx + (int) Math.round(Math.cos(a) * len), cy + (int) Math.round(Math.sin(a) * len));
        }
        g.setColor(orange);
        g.fillOval(cx - 5, cy - 5, 11, 11);
        g.setColor(gold);
        g.fillOval(cx - 4, cy - 4, 8, 8);
        g.setColor(new Color(0xfff6d6));
        g.fillOval(cx - 2, cy - 2, 4, 4);
        g.setColor(Color.WHITE);
        g.fillRect(cx - 1, cy - 1, 2, 2);
        // Sparks.
        g.setColor(gold);
        g.fillRect(14, 2, 1, 1);
        g.fillRect(29, 17, 1, 1);
        g.fillRect(30, 3, 1, 1);
    }

    // ------------------------------------------------------------------ helpers

    /** Two hearts joined by a thread: one life living in another body. */
    static void life(Graphics2D g) {
        Color c = new Color(0xff4f8b);
        heart(g, 13, 2, c);
        Color pale = tint(c, 0.55);
        g.setColor(shade(pale, 0.8));
        g.fillOval(24, 11, 5, 5);
        g.fillOval(27, 11, 5, 5);
        g.fillPolygon(new Polygon(new int[]{24, 32, 28}, new int[]{14, 14, 19}, 3));
        g.setColor(Color.WHITE);
        g.drawLine(21, 12, 25, 15);
        g.fillRect(15, 4, 2, 1);
    }

    /** Two ground tiles with arrows trading places between them. */
    static void location(Graphics2D g) {
        Color c = new Color(0x2fd6c3);
        g.setColor(new Color(0x4a8c3a));
        g.fillRect(13, 3, 8, 3);
        g.setColor(new Color(0x6b4a2c));
        g.fillRect(13, 6, 8, 3);
        g.setColor(new Color(0xd8d6a8));
        g.fillRect(23, 11, 8, 3);
        g.setColor(new Color(0x1d1430));
        g.fillRect(23, 14, 8, 3);
        g.setColor(c);
        g.setStroke(new BasicStroke(1));
        g.drawLine(22, 4, 27, 4);
        g.fillPolygon(new Polygon(new int[]{27, 30, 27}, new int[]{2, 4, 7}, 3));
        g.drawLine(17, 15, 22, 15);
        g.fillPolygon(new Polygon(new int[]{17, 14, 17}, new int[]{12, 15, 18}, 3));
    }

    /** A glowing open hand with a spark passing into it: power handed on. */
    static void ability(Graphics2D g) {
        Color c = new Color(0xf2a7ff);
        g.setColor(shade(c, 0.7));
        g.fillRoundRect(17, 9, 9, 8, 3, 3);
        g.setColor(c);
        g.fillRect(17, 4, 2, 6);
        g.fillRect(19, 3, 2, 7);
        g.fillRect(21, 3, 2, 7);
        g.fillRect(23, 4, 2, 6);
        g.fillRect(25, 8, 3, 2);
        g.fillRoundRect(18, 10, 7, 6, 2, 2);
        g.setColor(Color.WHITE);
        g.fillRect(14, 2, 1, 3);
        g.fillRect(13, 3, 3, 1);
        g.fillRect(29, 12, 1, 3);
        g.fillRect(28, 13, 3, 1);
    }

    /** A small chest with a thread tied around its latch. */
    static void storage(Graphics2D g) {
        Color wood = new Color(0xc98b4a);
        g.setColor(shade(wood, 0.6));
        g.fillRect(15, 6, 14, 11);
        g.setColor(wood);
        g.fillRect(16, 7, 12, 4);
        g.fillRect(16, 12, 12, 4);
        g.setColor(shade(wood, 0.45));
        g.drawLine(15, 11, 28, 11);
        g.setColor(new Color(0xd9d9d9));
        g.fillRect(21, 10, 2, 3);
        g.setColor(new Color(0x9b6bff));
        g.drawLine(20, 12, 15, 17);
        g.drawLine(23, 12, 26, 15);
    }

    static void heart(Graphics2D g, int x, int y, Color c) {
        g.setColor(shade(c, 0.7));
        g.fillOval(x, y, 7, 7);
        g.fillOval(x + 6, y, 7, 7);
        g.fillPolygon(new Polygon(new int[]{x, x + 13, x + 6}, new int[]{y + 4, y + 4, y + 13}, 3));
        g.setColor(c);
        g.fillOval(x + 1, y + 1, 5, 5);
        g.fillOval(x + 7, y + 1, 5, 5);
        g.fillPolygon(new Polygon(new int[]{x + 1, x + 12, x + 6}, new int[]{y + 4, y + 4, y + 11}, 3));
    }
}
