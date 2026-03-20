package dev.xmppbridge;

import org.jivesoftware.smack.AbstractConnectionListener;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.IOException;
import java.util.function.BiConsumer;

public class XmppClient {

    private AbstractXMPPConnection connection;
    private MultiUserChat muc;

    private String mucRoom;
    private String nickname;

    /** Called whenever a message arrives from the MUC. Arguments: (nickname, messageBody) */
    private BiConsumer<String, String> messageHandler;

    /** Captured from the calling thread during login() so reconnection callbacks can restore it. */
    private ClassLoader pluginClassLoader;

    /**
     * Stored once so joinRoom() can remove it before re-adding,
     * preventing listener accumulation across restarts/reconnects.
     */
    private final MessageListener mucMessageListener = message -> {
        String body = message.getBody();
        if (body == null || messageHandler == null) return;

        String from = message.getFrom() != null
                ? message.getFrom().getResourceOrEmpty().toString()
                : "unknown";

        if (nickname.equals(from)) return;

        messageHandler.accept(from, body);
    };

    /**
     * True after ChatPlugin calls joinRoom() for the first time.
     * Prevents the authenticated() listener from triggering a second joinRoom()
     * on the initial login — it only acts on subsequent reconnections.
     */
    private volatile boolean initialJoinDone = false;

    public void setMessageHandler(BiConsumer<String, String> handler) {
        this.messageHandler = handler;
    }

    public AbstractXMPPConnection login(String host, String domain, String username, String password,
                                        String mucRoom, String nickname)
            throws SmackException, IOException, XMPPException, InterruptedException {

        this.mucRoom  = mucRoom;
        this.nickname = nickname;
        pluginClassLoader = Thread.currentThread().getContextClassLoader();

        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setHost(host)
                .setXmppDomain(domain)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
                .setSendPresence(true)
                .setHostnameVerifier((hostname, session) -> true)
                .build();

        connection = new XMPPTCPConnection(config);

        ReconnectionManager reconnManager = ReconnectionManager.getInstanceFor(connection);
        reconnManager.setReconnectionPolicy(ReconnectionManager.ReconnectionPolicy.RANDOM_INCREASING_DELAY);
        reconnManager.enableAutomaticReconnection();

        connection.addConnectionListener(new AbstractConnectionListener() {
            @Override
            public void connectionClosedOnError(Exception e) {
                System.err.println("[XmppClient] Connection lost: " + e.getMessage()
                        + " — Smack will attempt to reconnect automatically.");
            }

            @Override
            public void authenticated(XMPPConnection conn, boolean resumed) {
                if (!initialJoinDone) return;

                if (resumed) {
                    System.out.println("[XmppClient] Connection resumed — MUC still joined.");
                    return;
                }

                System.out.println("[XmppClient] Re-authenticated after reconnect — re-joining MUC...");
                ClassLoader prev = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(pluginClassLoader);
                try {
                    joinRoom();
                } catch (Exception e) {
                    System.err.println("[XmppClient] Failed to re-join MUC after reconnect: " + e.getMessage());
                } finally {
                    Thread.currentThread().setContextClassLoader(prev);
                }
            }
        });

        connection.connect();
        connection.login(username, password);

        System.out.println("[XmppClient] Logged in as: " + connection.getUser());
        return connection;
    }

    public void disconnect() {
        if (connection != null) {
            ReconnectionManager.getInstanceFor(connection).disableAutomaticReconnection();
            if (connection.isConnected()) {
                connection.disconnect();
            }
        }
    }

    public void joinRoom() throws XmppStringprepException, SmackException, InterruptedException, XMPPException {
        if (muc != null) {
            muc.removeMessageListener(mucMessageListener);
            if (muc.isJoined()) {
                try { muc.leave(); } catch (Exception ignored) {}
            }
        }

        MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
        muc = manager.getMultiUserChat(JidCreate.entityBareFrom(mucRoom));
        muc.join(Resourcepart.from(nickname));
        muc.addMessageListener(mucMessageListener);

        initialJoinDone = true;
        System.out.println("[XmppClient] Joined room: " + mucRoom);
    }

    public void sendToRoom(String player, String message) throws SmackException.NotConnectedException,
            InterruptedException {
        if (muc == null || !muc.isJoined()) {
            throw new IllegalStateException("Not joined to MUC room. Call joinRoom() first.");
        }
        muc.sendMessage(player + ": " + message);
    }
}