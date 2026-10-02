package io.github.auphantom.grafting.command;

import io.github.auphantom.grafting.item.ThreadItem;
import io.github.auphantom.grafting.listener.ThreadListener;
import io.github.auphantom.grafting.pack.PackServer;
import io.github.auphantom.grafting.ui.PathwayBook;
import io.github.auphantom.grafting.util.Text;

import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@code /graft give|mode|menu|list|sever|pack|help} */
public final class GraftCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("book", "give", "mode", "list", "sever", "pack", "help");

    private final GraftManager manager;
    private final ThreadListener threads;
    private final PathwayBook menu;
    private final PackServer pack;

    public GraftCommand(GraftManager manager, ThreadListener threads, PathwayBook menu, PackServer pack) {
        this.manager = manager;
        this.threads = threads;
        this.menu = menu;
        this.pack = pack;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();
        switch (sub) {
            case "give" -> give(sender, args);
            case "mode" -> mode(sender, args);
            case "book", "menu" -> {
                if (sender instanceof Player p) menu.open(p);
            }
            case "list" -> list(sender);
            case "sever" -> sever(sender, args);
            case "pack" -> {
                if (sender instanceof Player p) pack.offer(p);
            }
            default -> help(sender, label);
        }
        return true;
    }

    private boolean holding(Player player) {
        if (ThreadItem.is(player.getInventory().getItemInMainHand())) return true;
        Text.send(player, "<red>Hold the Thread of Grafting first.");
        return false;
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("grafting.give")) {
            Text.send(sender, "<red>You do not have permission to conjure the thread.");
            return;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Text.send(sender, "<red>No player named " + Text.esc(args[1]) + " is online.");
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            Text.send(sender, "<red>Usage: /graft give <player>");
            return;
        }
        ItemStack thread = ThreadItem.create(Mode.DISTANCE);
        Map<Integer, ?> leftover = target.getInventory().addItem(thread);
        if (!leftover.isEmpty()) target.getWorld().dropItem(target.getLocation(), thread);
        Text.send(target, "<gray>A <light_purple>Thread of Grafting</light_purple> appears in your hand. "
                + "<dark_gray>(Left-click to switch ability.)");
        if (sender != target) Text.send(sender, "<gray>Gave the thread to " + Text.esc(target.getName()) + ".");
    }

    private void mode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || !holding(player)) return;
        if (args.length < 2) {
            Text.send(sender, "<gray>Abilities: " + String.join(", ", modeIds()));
            return;
        }
        Mode mode = Mode.byId(args[1]);
        if (mode == null) {
            Text.send(sender, "<red>Unknown ability. Try: " + String.join(", ", modeIds()));
            return;
        }
        threads.select(player, player.getInventory().getItemInMainHand(), mode);
    }

    private void list(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<gray>" + manager.all().size() + " graft(s) alive on the server.");
            return;
        }
        List<Graft> owned = manager.ofOwner(player.getUniqueId());
        if (owned.isEmpty()) {
            Text.send(sender, "<gray>You hold no grafts.");
            return;
        }
        Text.send(sender, "<gray>Your grafts:");
        for (Graft graft : owned) {
            long secondsLeft = Math.max(0, (graft.expiresAt() - manager.currentTick()) / 20);
            sender.sendMessage(Text.mm(" <dark_gray>#" + graft.id() + "</dark_gray> " + graft.mode().tag() + graft.name()
                    + "</color> <gray>" + Text.esc(graft.first().describe()) + " " + graft.mode().tag() + "⟶</color> "
                    + Text.esc(graft.second().describe()) + " <dark_gray>(" + secondsLeft + "s)"));
        }
    }

    private void sever(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Only players hold threads.");
            return;
        }
        List<Graft> owned = manager.ofOwner(player.getUniqueId());
        if (args.length < 2 || args[1].equalsIgnoreCase("all")) {
            owned.forEach(g -> manager.end(g, "was severed"));
            if (owned.isEmpty()) Text.send(sender, "<gray>You hold no grafts.");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[1].replace("#", ""));
        } catch (NumberFormatException ex) {
            Text.send(sender, "<red>Usage: /graft sever [id|all]");
            return;
        }
        for (Graft graft : owned) {
            if (graft.id() == id) {
                manager.end(graft, "was severed");
                return;
            }
        }
        Text.send(sender, "<red>You hold no graft #" + id + ".");
    }

    private void help(CommandSender sender, String label) {
        Text.send(sender, "<gray>Reassembly: connect two things that should never touch.");
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " give [player]</light_purple> <dark_gray>-</dark_gray> <gray>get the Thread of Grafting"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " mode <ability></light_purple> <dark_gray>-</dark_gray> <gray>switch ability"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " book</light_purple> <dark_gray>-</dark_gray> <gray>open the pathway book (or click the sigil in your inventory)"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " list</light_purple> <dark_gray>-</dark_gray> <gray>see your active grafts"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " sever [id|all]</light_purple> <dark_gray>-</dark_gray> <gray>cut a graft"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " pack</light_purple> <dark_gray>-</dark_gray> <gray>re-send the texture pack"));
        sender.sendMessage(Text.mm(" <gray>Left-click switches ability, right-click ties the thread. Abilities:"));
        for (Mode mode : Mode.values()) {
            sender.sendMessage(Text.mm("  " + mode.tag() + mode.display() + " <dark_gray>(" + mode.shape() + ")</dark_gray> <gray>"
                    + mode.description()));
        }
    }

    private static List<String> modeIds() {
        List<String> ids = new ArrayList<>();
        for (Mode m : Mode.values()) ids.add(m.id());
        return ids;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBS);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            options.addAll(modeIds());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sever") && sender instanceof Player p) {
            options.add("all");
            for (Graft g : manager.ofOwner(p.getUniqueId())) options.add(String.valueOf(g.id()));
        }
        String prefix = args[args.length - 1].toLowerCase();
        options.removeIf(o -> !o.toLowerCase().startsWith(prefix));
        return options;
    }
}
