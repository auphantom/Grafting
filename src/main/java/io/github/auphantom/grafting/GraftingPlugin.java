package io.github.auphantom.grafting;

import io.github.auphantom.grafting.graft.GraftFactory;
import io.github.auphantom.grafting.graft.GraftManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Grafting: a tiny take on the Attendant of Mysteries' "Reassembly".
 * <p>
 * Wiring only. The interesting parts live in {@link GraftFactory} (what a pair of
 * concepts means) and the {@code graft} package (what each connection does).
 */
public final class GraftingPlugin extends JavaPlugin {

    private GraftManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ThreadItem.init(this);

        manager = new GraftManager(this);
        GraftFactory factory = new GraftFactory(this, manager);
        ThreadListener listener = new ThreadListener(manager, factory);
        getServer().getPluginManager().registerEvents(listener, this);

        PluginCommand command = getCommand("graft");
        if (command != null) {
            GraftCommand executor = new GraftCommand(manager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        manager.start();
        getServer().getScheduler().runTaskTimer(this, listener::tickPending, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }
}
