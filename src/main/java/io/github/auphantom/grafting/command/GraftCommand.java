package io.github.auphantom.grafting.command;

import io.github.auphantom.grafting.beyonder.Beyonder;
import io.github.auphantom.grafting.beyonder.DistanceArt;
import io.github.auphantom.grafting.graft.types.StorageGraft;
import io.github.auphantom.grafting.item.ThreadItem;
import io.github.auphantom.grafting.listener.ThreadListener;
import io.github.auphantom.grafting.pack.PackServer;
import io.github.auphantom.grafting.ui.PathwayMenu;
import io.github.auphantom.grafting.util.Text;

import io.github.auphantom.grafting.graft.Graft;
import io.github.auphantom.grafting.graft.GraftManager;
import io.github.auphantom.grafting.graft.Mode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@code /graft <menu|give|mode|list|sever|body|step|art|lend|storage|spirit|level|sequence|pack|help>} */
public final class GraftCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("menu", "give", "mode", "list", "sever", "body", "step", "art",
            "lend", "storage", "spirit", "level", "sequence", "pack", "help");

    private final GraftManager manager;
    private final Beyonder beyonder;
    private final ThreadListener threads;
    private final PathwayMenu menu;
    private final PackServer pack;

    public GraftCommand(GraftManager manager, ThreadListener threads, PathwayMenu menu, PackServer pack) {
        this.manager = manager;
        this.beyonder = manager.beyonder();
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
            case "menu" -> {
                if (sender instanceof Player p) menu.open(p);
            }
            case "list" -> list(sender);
            case "sever" -> sever(sender, args);
            case "body" -> {
                if (sender instanceof Player p) beyonder.toggleSpiritBody(p);
            }
            case "step", "target" -> step(sender, args);
            case "art" -> art(sender, args);
            case "lend" -> lend(sender, args);
            case "storage" -> {
                if (!(sender instanceof Player p)) return true;
                StorageGraft g = StorageGraft.of(p);
                if (g == null) Text.send(p, "<gray>You have no grafted storage.");
                else g.open(p);
            }
            case "spirit" -> spirit(sender, args);
            case "level" -> level(sender, args);
            case "sequence" -> sequence(sender, args);
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
            for (Graft graft : manager.all()) {
                org.bukkit.OfflinePlayer owner = Bukkit.getOfflinePlayer(graft.owner());
                sender.sendMessage(Text.mm(" <dark_gray>#" + graft.id() + "</dark_gray> " + graft.mode().tag() + graft.name()
                        + "</color> <gray>by " + Text.esc(String.valueOf(owner.getName())) + ": "
                        + Text.esc(graft.first().describe()) + " ⟶ " + Text.esc(graft.second().describe())));
            }
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

    // ------------------------------------------------------------------ Beyonder commands

    private void step(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 4) {
            Text.send(sender, "<red>Usage: /graft step <x> <y> <z> [world]");
            return;
        }
        Location target = Beyonder.parseTarget(player, java.util.Arrays.copyOfRange(args, 1, args.length));
        if (target == null) {
            Text.send(sender, "<red>Unknown place.");
            return;
        }
        beyonder.setArt(player, DistanceArt.STEP);
        beyonder.armStep(player, target);
    }

    private void art(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 2) {
            Text.send(sender, "<gray>Distance arts: step (1), gateway (2), enemy (3), infinity (4). "
                    + "You are distance level <white>" + beyonder.level(player) + "</white>, using <white>"
                    + beyonder.art(player).display() + "</white>.");
            return;
        }
        DistanceArt art = DistanceArt.byId(args[1]);
        if (art == null) {
            Text.send(sender, "<red>Unknown art. Try: step, gateway, enemy, infinity");
            return;
        }
        if (beyonder.setArt(player, art)) {
            Text.send(player, "<gray>Distance art: <color:#9b6bff>" + art.display() + "</color> <dark_gray>("
                    + art.description() + ")");
        }
    }

    private void lend(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        Mode mode = args.length >= 2 ? Mode.byId(args[1]) : null;
        if (mode == null || mode == Mode.ABILITY) {
            Text.send(sender, "<gray>The Ability graft currently lends <white>" + beyonder.lend(player).display()
                    + "</white>. Usage: /graft lend <ability>");
            return;
        }
        beyonder.setLend(player, mode);
        Text.send(sender, "<gray>Ability grafts will now lend your " + mode.tag() + mode.display() + "</color>.");
    }

    /** Admin: {@code /graft spirit [player] [amount|refill]}. Without arguments, shows your own. */
    private void spirit(CommandSender sender, String[] args) {
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
        if (target == null) {
            Text.send(sender, "<red>Usage: /graft spirit [player] [amount|refill]");
            return;
        }
        if (args.length >= 3) {
            if (!sender.hasPermission("grafting.admin")) {
                Text.send(sender, "<red>You may not change spirit.");
                return;
            }
            double amount = args[2].equalsIgnoreCase("refill") ? beyonder.maxSpirit() : parse(args[2], -1);
            if (amount < 0) {
                Text.send(sender, "<red>Not a number: " + Text.esc(args[2]));
                return;
            }
            beyonder.setSpirit(target, amount);
        }
        Text.send(sender, "<gray>" + Text.esc(target.getName()) + ": <color:#6fe6ee>" + (int) beyonder.spirit(target)
                + "</color>/" + (int) beyonder.maxSpirit() + " spirit.");
    }

    /** Admin: {@code /graft level [player] [1-4]} sets the distance level directly. */
    private void level(CommandSender sender, String[] args) {
        Player target = sender instanceof Player p ? p : null;
        int argIndex = 1;
        if (args.length >= 2 && Bukkit.getPlayerExact(args[1]) != null) {
            target = Bukkit.getPlayerExact(args[1]);
            argIndex = 2;
        }
        if (target == null) {
            Text.send(sender, "<red>Usage: /graft level [player] [1-4]");
            return;
        }
        if (args.length > argIndex) {
            if (!sender.hasPermission("grafting.admin")) {
                Text.send(sender, "<red>You may not change distance levels.");
                return;
            }
            int level = (int) parse(args[argIndex], -1);
            if (level < 1 || level > 4) {
                Text.send(sender, "<red>The distance level must be 1 to 4.");
                return;
            }
            beyonder.setLevel(target, level);
            Text.send(target, "<gray>Your distance level is now <white>" + level + "</white>.");
        }
        int level = beyonder.level(target);
        String next = level >= 4 ? "max" : (int) beyonder.profile(target).travelled() + "/" + (int) beyonder.threshold(level + 1)
                + " blocks to level " + (level + 1);
        Text.send(sender, "<gray>" + Text.esc(target.getName()) + ": distance level <white>" + level
                + "</white> <dark_gray>(" + next + ")</dark_gray>, art <white>" + beyonder.art(target).display());
    }

    /** Admin: {@code /graft sequence <player> [0-9|none]} for resistance rolls. */
    private void sequence(CommandSender sender, String[] args) {
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
        if (target == null) {
            Text.send(sender, "<red>Usage: /graft sequence <player> [0-9|none]");
            return;
        }
        if (args.length >= 3) {
            if (!sender.hasPermission("grafting.admin")) {
                Text.send(sender, "<red>You may not change sequences.");
                return;
            }
            int seq = args[2].equalsIgnoreCase("none") ? 10 : (int) parse(args[2], -1);
            if (seq < 0 || seq > 10) {
                Text.send(sender, "<red>A sequence is 0 to 9 (or none).");
                return;
            }
            beyonder.setSequence(target, seq);
        }
        int s = beyonder.sequence(target);
        Text.send(sender, "<gray>" + Text.esc(target.getName()) + " is " + (s >= 10 ? "not a Beyonder" : "Sequence " + s) + ".");
    }

    private static double parse(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void help(CommandSender sender, String label) {
        Text.send(sender, "<gray>Reassembly: connect two things that should never touch.");
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " give [player]</light_purple> <dark_gray>-</dark_gray> <gray>get the Thread of Grafting"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " mode <ability></light_purple> <dark_gray>-</dark_gray> <gray>switch ability"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " menu</light_purple> <dark_gray>-</dark_gray> <gray>open the pathway menu (or click the sigil in your inventory)"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " list</light_purple> <dark_gray>-</dark_gray> <gray>see your active grafts"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " sever [id|all]</light_purple> <dark_gray>-</dark_gray> <gray>cut a graft"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " body</light_purple> <dark_gray>-</dark_gray> <gray>shift into or out of your Spirit Body"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " art <step|gateway|enemy|infinity></light_purple> <dark_gray>-</dark_gray> <gray>choose your Distance art"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " step <x> <y> <z> [world]</light_purple> <dark_gray>-</dark_gray> <gray>your next step lands there"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " lend <ability></light_purple> <dark_gray>-</dark_gray> <gray>what the Ability graft lends"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " storage</light_purple> <dark_gray>-</dark_gray> <gray>open your grafted storage (or sneak + F)"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " level [player] [1-4]</light_purple> <dark_gray>-</dark_gray> <gray>show or set the distance level"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " spirit [player] [amount|refill]</light_purple> <dark_gray>-</dark_gray> <gray>show or set spirit"));
        sender.sendMessage(Text.mm(" <light_purple>/" + label + " sequence <player> [0-9|none]</light_purple> <dark_gray>-</dark_gray> <gray>a player's sequence, for resistance"));
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
        } else if (args.length == 2 && List.of("give", "spirit", "level", "sequence").contains(args[0].toLowerCase())) {
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
            if (args[0].equalsIgnoreCase("level")) options.addAll(List.of("1", "2", "3", "4"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("level")) {
            options.addAll(List.of("1", "2", "3", "4"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("spirit")) {
            options.add("refill");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("art")) {
            for (DistanceArt a : DistanceArt.values()) options.add(a.id());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("mode") || args[0].equalsIgnoreCase("lend"))) {
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
