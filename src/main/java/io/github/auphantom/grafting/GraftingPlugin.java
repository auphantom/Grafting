package io.github.auphantom.grafting;

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

        manager = new GraftManager(this);
        GraftFactory factory = new GraftFactory(this, manager);
        ThreadListener listener = new ThreadListener(this, manager, factory);
        AbilityMenu menu = new AbilityMenu(listener);
        listener.setMenu(menu);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(menu, this);

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
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
        if (packServer != null) packServer.stop();
    }
}
