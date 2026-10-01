package io.github.auphantom.grafting;

import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@code /graft give|list|sever|help} */
public final class GraftCommand implements TabExecutor {

    private final GraftManager manager;

    public GraftCommand(GraftManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();
        switch (sub) {
            case "give" -> give(sender, args);
            case "list" -> list(sender);
            case "sever" -> sever(sender, args);
            default -> help(sender, label);
        }
        return true;
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
        Map<Integer, ?> leftover = target.getInventory().addItem(ThreadItem.create());
        if (!leftover.isEmpty()) target.getWorld().dropItem(target.getLocation(), ThreadItem.create());
        Text.send(target, "<gray>A <light_purple>Thread of Grafting</light_purple> appears in your hand.");
        if (sender != target) Text.send(sender, "<gray>Gave the thread to " + Text.esc(target.getName()) + ".");
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
            sender.sendMessage(Text.mm(" <dark_gray>#" + graft.id() + "</dark_gray> <light_purple>" + graft.name()
                    + "</light_purple> <gray>" + Text.esc(graft.first().describe()) + " <dark_purple>⟶</dark_purple> "
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
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " list</light_purple> <dark_gray>-</dark_gray> <gray>see your active grafts"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " sever [id|all]</light_purple> <dark_gray>-</dark_gray> <gray>cut a graft"));
        sender.sendMessage(Text.mm(" <gray>Right-click two things with the thread. The <white>first</white> is grafted <white>onto</white> the second:"));
        sender.sendMessage(Text.mm("  <dark_purple>Place + Place</dark_purple> <gray>the distance between them becomes zero"));
        sender.sendMessage(Text.mm("  <dark_purple>Being + Being</dark_purple> <gray>harm meant for the first finds the second"));
        sender.sendMessage(Text.mm("  <dark_purple>Place + Being</dark_purple> <gray>the being takes on the block's nature"));
        sender.sendMessage(Text.mm("  <dark_purple>Being + Place</dark_purple> <gray>the being's next death becomes a trip home"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("give", "list", "sever", "help")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sever") && sender instanceof Player p) {
            out.add("all");
            for (Graft g : manager.ofOwner(p.getUniqueId())) out.add(String.valueOf(g.id()));
        }
        return out;
    }
}
