package io.github.auphantom.grafting;

import io.github.auphantom.grafting.ui.PathwayMenu;
import io.github.auphantom.grafting.command.GraftCommand;
import io.github.auphantom.grafting.item.Sigil;
import io.github.auphantom.grafting.item.ThreadItem;
import io.github.auphantom.grafting.listener.SigilListener;
import io.github.auphantom.grafting.listener.ThreadListener;
import io.github.auphantom.grafting.pack.PackServer;
import io.github.auphantom.grafting.util.Fx;
import io.github.auphantom.grafting.graft.GraftFactory;
import io.github.auphantom.grafting.graft.GraftManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Grafting: a take on the Attendant of Mysteries' "Reassembly".
 * <p>
 * Wiring only. The interesting parts live in {@link GraftFactory} (what a mode does with
 * two ends) and the {@code graft} package (what each connection does).
 */
public final class GraftingPlugin extends JavaPlugin {

    private GraftManager manager;
    private PackServer packServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Fx.setDensity(getConfig().getDouble("particles.density", 1.0));
        ThreadItem.init(this);
        Sigil.init(this);

        manager = new GraftManager(this);
        GraftFactory factory = new GraftFactory(this, manager);
        ThreadListener listener = new ThreadListener(this, manager, factory);
        PathwayMenu menu = new PathwayMenu(this, manager, listener);
        SigilListener sigils = new SigilListener(this, manager, menu);
        listener.setMenu(menu);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(sigils, this);

        packServer = new PackServer(this);
        if (packServer.start()) getServer().getPluginManager().registerEvents(packServer, this);

        PluginCommand command = getCommand("graft");
        if (command != null) {
            GraftCommand executor = new GraftCommand(manager, listener, menu, packServer);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        manager.start();
        getServer().getScheduler().runTaskTimer(this, listener::tickPending, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            sigils.tick();
            menu.tick();
        }, 20L, 20L);
        getServer().getOnlinePlayers().forEach(sigils::ensure); // after /reload
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
        if (packServer != null) packServer.stop();
    }
}
