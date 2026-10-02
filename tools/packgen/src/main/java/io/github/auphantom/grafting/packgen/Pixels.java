package io.github.auphantom.grafting.packgen;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small pixel-art and zip helpers shared by the pack generators. */
final class Pixels {

    /** The sigil is 32x32. */
    static final int S = 32;

    private Pixels() {
    }

    /** Draws a 1px dark outline around every opaque pixel, the classic item-sprite look. */
    static void outline(BufferedImage img, Color color) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[][] solid = new boolean[w][h];
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) solid[x][y] = (img.getRGB(x, y) >>> 24) > 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (solid[x][y]) continue;
                boolean edge = (x > 0 && solid[x - 1][y]) || (x < w - 1 && solid[x + 1][y])
                        || (y > 0 && solid[x][y - 1]) || (y < h - 1 && solid[x][y + 1]);
                if (edge) img.setRGB(x, y, color.getRGB());
            }
        }
    }

    static Color shade(Color c, double f) {
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f));
    }

    static Color tint(Color c, double f) {
        return new Color(c.getRed() + (int) ((255 - c.getRed()) * f), c.getGreen() + (int) ((255 - c.getGreen()) * f),
                c.getBlue() + (int) ((255 - c.getBlue()) * f));
    }

    /** Nearest-neighbour upscale, for previews. */
    static BufferedImage scale(BufferedImage src, int factor) {
        BufferedImage out = new BufferedImage(src.getWidth() * factor, src.getHeight() * factor, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    static void png(ZipOutputStream zip, String name, BufferedImage img) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bytes);
        entry(zip, name, bytes.toByteArray());
    }

    static void text(ZipOutputStream zip, String name, String content) throws IOException {
        entry(zip, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void entry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0); // reproducible zip -> stable SHA-1 across builds
        zip.putNextEntry(e);
        zip.write(data);
        zip.closeEntry();
    }
}
