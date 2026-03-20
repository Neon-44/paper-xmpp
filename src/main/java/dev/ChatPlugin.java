package dev.xmppbridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatPlugin extends JavaPlugin {

    public static ChatListener chat_listener;
    public static XmppClient chat_bridge;

    @Override
    public void onEnable() {
        // Write config.yml to the plugin folder on first run, then load it
        saveDefaultConfig();

        chat_listener = new ChatListener();
        getServer().getPluginManager().registerEvents(chat_listener, this);
        getLogger().info("ChatPlugin enabled — chat listener registered.");

        String host     = getConfig().getString("xmpp.host");
        String domain   = getConfig().getString("xmpp.domain");
        String username = getConfig().getString("xmpp.username");
        String password = getConfig().getString("xmpp.password");
        String mucRoom  = getConfig().getString("xmpp.muc_room");
        String nickname = getConfig().getString("xmpp.nickname");

        // Fix for Paper's isolated classloader breaking Smack's SPI lookup
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        try {
            chat_bridge = new XmppClient();
            chat_bridge.login(host, domain, username, password, mucRoom, nickname);

            chat_bridge.setMessageHandler((from, message) -> {
                Component component = Component.text()
                        .append(Component.text("[XMPP] ", NamedTextColor.AQUA))
                        .append(Component.text(from + ": ", NamedTextColor.YELLOW))
                        .append(Component.text(message, NamedTextColor.WHITE))
                        .build();
                getServer().broadcast(component);
            });

            chat_bridge.joinRoom();
        } catch (Exception e) {
            getLogger().severe("Failed to connect to XMPP: " + e.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Override
    public void onDisable() {
        if (chat_bridge != null) chat_bridge.disconnect();
        getLogger().info("ChatPlugin disabled.");
    }
}