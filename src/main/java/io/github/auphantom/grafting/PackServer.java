package io.github.auphantom.grafting;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Ships the bundled texture pack to players.
 * <p>
 * The pack is generated at build time and stored inside the plugin jar. On startup this
 * class serves it from a tiny built-in HTTP server (JDK {@code com.sun.net.httpserver},
 * no extra dependencies) and offers it to every player who joins. Server owners who
 * prefer their own CDN can set {@code resource-pack.url} instead.
 */
public final class PackServer implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("grafting-pack".getBytes());

    private final GraftingPlugin plugin;
    private byte[] pack;
    private byte[] sha1;
    private HttpServer http;
    private String url;

    public PackServer(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return whether the pack is ready to be offered */
    public boolean start() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("resource-pack.enabled", true)) return false;
        try (InputStream in = plugin.getResource("grafting-pack.zip")) {
            if (in == null) {
                plugin.getLogger().warning("No grafting-pack.zip in the jar; textures disabled.");
                return false;
            }
            pack = in.readAllBytes();
            sha1 = MessageDigest.getInstance("SHA-1").digest(pack);
        } catch (IOException | NoSuchAlgorithmException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read the texture pack", ex);
            return false;
        }

        String external = config.getString("resource-pack.url", "");
        if (external != null && !external.isBlank()) {
            url = external;
            return true;
        }
        int port = config.getInt("resource-pack.port", 8164);
        String host = config.getString("resource-pack.public-host", "");
        if (host == null || host.isBlank()) host = Bukkit.getIp().isBlank() ? "localhost" : Bukkit.getIp();
        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
            http.createContext("/grafting-pack.zip", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, pack.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(pack);
                }
            });
            http.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Grafting-PackServer");
                t.setDaemon(true);
                return t;
            }));
            http.start();
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not open the pack server on port " + port
                    + ". Set resource-pack.port or resource-pack.url in config.yml.", ex);
            return false;
        }
        url = "http://" + host + ":" + port + "/grafting-pack.zip";
        plugin.getLogger().info("Serving the texture pack at " + url);
        return true;
    }

    public void stop() {
        if (http != null) http.stop(0);
    }

    public void offer(Player player) {
        if (url == null) return;
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
        player.setResourcePack(PACK_ID, url, sha1,
                Text.mm("<gradient:#7b5cff:#d9ccff>Grafting</gradient> <gray>uses a small pack for the Thread of Grafting's textures."),
                required);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // A short delay so the client has finished loading the world first.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) offer(event.getPlayer());
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.INVALID_URL) {
            plugin.getLogger().warning(event.getPlayer().getName() + " could not download the texture pack ("
                    + event.getStatus() + "). Is resource-pack.public-host reachable?");
        }
    }
}
