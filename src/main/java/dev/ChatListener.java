package dev.xmppbridge;
 
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
 
public class ChatListener implements Listener {
 
    //private final ChatHandler handler;
    
    /*
    public ChatListener(ChatHandler handler) {
        this.handler = handler;
    }
    */
 
    /**
     * Fires on every player chat message.
     * Serializes the Adventure component to a plain String and
     * passes just the text to ChatHandler#handle.
     */
    @EventHandler
    public void onChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message());
        String player = event.getPlayer().getName();
        try {ChatPlugin.chat_bridge.sendToRoom(player, message);} catch (Exception e) {}
    }
}