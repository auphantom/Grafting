package io.github.auphantom.grafting.packgen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import static io.github.auphantom.grafting.packgen.Pixels.*;

/**
 * Builds the Grafting resource pack: the Fool sigil, the stat-bar font and the cyan tooltip
 * frame. Every texture is drawn by code, so the repository holds no binary art. The ability
 * threads need no textures at all: they borrow vanilla item models.
 * <p>
 * Run by Gradle ({@code ./gradlew generatePack}, also part of {@code build}). The zip is
 * bundled into the plugin jar and served to players by the plugin.
 * <pre>
 *   args[0]  output zip  (build/pack/grafting-pack.zip)
 * </pre>
 */
public final class PackGenerator {

    private PackGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "build/pack/grafting-pack.zip");
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out))) {
            zip.setLevel(9);
            text(zip, "pack.mcmeta", """
                    {
                      "pack": {
                        "pack_format": 46,
                        "supported_formats": [46, 99],
                        "min_format": 46,
                        "max_format": 99,
                        "description": "§dGrafting§7 - the Fool sigil and Mystery Arts tooltips"
                      }
                    }
                    """);
            png(zip, "pack.png", scale(PathwayArt.sigil(), 4));
            PathwayArt.write(zip);
        }
        System.out.println("Wrote " + out);
    }

    /** Texture, model and 1.21.4+ item definition for {@code grafting:<id>}. */
    static void itemModel(ZipOutputStream zip, String id, java.awt.image.BufferedImage texture, String parent) throws IOException {
        png(zip, "assets/grafting/textures/item/" + id + ".png", texture);
        text(zip, "assets/grafting/models/item/" + id + ".json", """
                { "parent": "minecraft:item/%s", "textures": { "layer0": "grafting:item/%s" } }
                """.formatted(parent, id));
        text(zip, "assets/grafting/items/" + id + ".json", """
                { "model": { "type": "minecraft:model", "model": "grafting:item/%s" } }
                """.formatted(id));
    }
}
