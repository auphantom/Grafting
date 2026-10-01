package io.github.auphantom.grafting;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Tiny MiniMessage helper so the rest of the code can stay readable. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<dark_gray>[<gradient:#7b5cff:#d9ccff>Grafting</gradient>]</dark_gray> ";

    private Text() {
    }

    public static Component mm(String message, TagResolver... resolvers) {
        return MM.deserialize(message, resolvers);
    }

    /** Escapes user / world supplied text before it is embedded in a MiniMessage string. */
    public static String esc(String raw) {
        return MM.escapeTags(raw);
    }

    public static void send(CommandSender to, String message, TagResolver... resolvers) {
        to.sendMessage(mm(PREFIX + message, resolvers));
    }

    public static void actionBar(Player to, String message, TagResolver... resolvers) {
        to.sendActionBar(mm(message, resolvers));
    }
}
