package io.github.auphantom.grafting.packgen;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import static io.github.auphantom.grafting.packgen.Pixels.*;

/**
 * Builds the Grafting resource pack: every texture is drawn by code, so the repository holds no
 * binary art and the pack can be regenerated at any time.
 * <p>
 * Run by Gradle ({@code ./gradlew generatePack}, also part of {@code build}). The zip is
 * bundled into the plugin jar and served to players by the plugin.
 * <pre>
 *   args[0]  output zip            (build/pack/grafting-pack.zip)
 *   args[1]  optional preview dir  (writes icons.png, used in the README)
 * </pre>
 */
public final class PackGenerator {

    private PackGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "build/pack/grafting-pack.zip");
        Path previewDir = args.length > 1 ? Path.of(args[1]) : null;
        Files.createDirectories(out.toAbsolutePath().getParent());

        Map<String, BufferedImage> icons = AbilityIcons.all();
        Map<String, BufferedImage> preview = new LinkedHashMap<>(icons);

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out))) {
            zip.setLevel(9);
            text(zip, "pack.mcmeta", """
                    {
                      "pack": {
                        "pack_format": 46,
                        "supported_formats": [46, 99],
                        "min_format": 46,
                        "max_format": 99,
                        "description": "§dGrafting§7 - textures for the Thread of Grafting"
                      }
                    }
                    """);
            png(zip, "pack.png", scale(icons.get("supernova"), 4));
            for (var e : icons.entrySet()) itemModel(zip, e.getKey(), e.getValue(), "handheld");
            preview.putAll(PathwayArt.write(zip));
        }

        if (previewDir != null) {
            Files.createDirectories(previewDir);
            ImageIO.write(sheet(preview), "png", previewDir.resolve("icons.png").toFile());
        }
        System.out.println("Wrote " + out + " (" + preview.size() + " item textures)");
    }

    /** Texture, model and 1.21.4+ item definition for {@code grafting:<id>}. */
    static void itemModel(ZipOutputStream zip, String id, BufferedImage texture, String parent) throws IOException {
        png(zip, "assets/grafting/textures/item/" + id + ".png", texture);
        text(zip, "assets/grafting/models/item/" + id + ".json", """
                { "parent": "minecraft:item/%s", "textures": { "layer0": "grafting:item/%s" } }
                """.formatted(parent, id));
        text(zip, "assets/grafting/items/" + id + ".json", """
                { "model": { "type": "minecraft:model", "model": "grafting:item/%s" } }
                """.formatted(id));
    }

    private static BufferedImage sheet(Map<String, BufferedImage> images) {
        int cell = S * 8 + 16;
        BufferedImage sheet = new BufferedImage(images.size() * cell + 16, S * 8 + 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x2b2b33));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        int x = 16;
        for (BufferedImage img : images.values()) {
            g.drawImage(scale(img, 8), x, 16, null);
            x += cell;
        }
        g.dispose();
        return sheet;
    }
}
