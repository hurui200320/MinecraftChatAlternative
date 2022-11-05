package info.skyblond.mc.mca.i2p;

import info.skyblond.mc.mca.MCAUtils;
import info.skyblond.mc.mca.model.AuthPayload;
import info.skyblond.mc.mca.model.MCAPlatform;
import info.skyblond.mc.mca.model.MessagePayload;
import net.i2p.I2PException;
import net.i2p.client.streaming.I2PServerSocket;
import net.i2p.client.streaming.I2PSocket;
import net.i2p.client.streaming.I2PSocketManager;
import net.i2p.data.Destination;
import net.i2p.util.I2PThread;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

/**
 * This is the peer in this i2p p2p chat system.
 * It has a server (receive messages from others),
 * and a lot of clients (sending messages to others).
 */
@SuppressWarnings("unused")
public class I2PChatPeer implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(I2PChatPeer.class);
    @NotNull
    private final I2PSocketManager manager;
    @NotNull
    private final I2PServerSocket serverSocket;
    @NotNull
    private final I2PServerSocketHandler serverSocketHandler;

    @NotNull
    public Destination getMyDestination() {
        return this.manager.getSession().getMyDestination();
    }

    private final ConcurrentLinkedQueue<AuthPayload> newPeersQueue = new ConcurrentLinkedQueue<>();

    public I2PChatPeer(@NotNull I2PSocketManager manager) {
        this.manager = manager;
        this.serverSocket = this.manager.getServerSocket();
        // start server thread
        this.serverSocketHandler = new I2PServerSocketHandler(this.serverSocket,
                this.newPeersQueue, this::resolveSource);
        I2PThread thread = new I2PThread(this.serverSocketHandler);
        thread.setName("ServerSocketHandler");
        thread.setDaemon(true);
        thread.start();
        // handle new peers
        MCAUtils.runLater(new I2PNewPeerHandler(this.newPeersQueue,
                this::connect));
        // TODO exchange peer periodically?
    }

    // here are all outgoing connections, we're the client.
    private final CopyOnWriteArrayList<I2PConnection> clientSockets = new CopyOnWriteArrayList<>();

    /**
     * Find the user with given socket.
     * Return null if not found.
     */
    public String resolveSource(@NotNull I2PSocket source) {
        var sourceDest = source.getPeerDestination();
        var optionTx = this.clientSockets.stream()
                .filter(e -> e.getPeerDestination().equals(sourceDest))
                .findAny();
        if (optionTx.isEmpty()) {
            return null;
        } else {
            return optionTx.get().username;
        }
    }

    /**
     * Use null as username if you don't sure about the username.
     * Return ture if success.
     * Either success or fail, the map and the list will be updated.
     */
    public boolean connect(@Nullable String username, @NotNull Destination destination) {
        if (username != null) {
            var oldConnect = this.clientSockets.stream().filter(it -> username.equals(it.username))
                    .findAny().orElse(null);
            if (oldConnect != null && !oldConnect.isClosed()) {
                return true;
            }
        }

        try {
            var clientConnect = new I2PConnection(username, this.manager.connect(destination),
                    socket -> {
                        this.clientSockets.remove(socket);
                        MCAUtils.addSystemMessageToChat(MCAPlatform.I2P, Text.literal(
                                "Lost outgoing connection to " + socket.username));
                        this.logger.info("Lost outgoing connection to {}", socket.username);
                        // TODO onFailed: try re-connect using known ids?
                    });
            // first send auth
            if (!clientConnect.auth(getMyDestination())) {
                this.logger.info("Failed to auth with {}", username);
                return false;
            }
            // then exchange peers
            var result = clientConnect.exchange(this.serverSocketHandler.getKnownIds());
            if (result == null) {
                this.logger.info("Failed to exchange peers with {}", username);
                return false;
            }
            this.newPeersQueue.addAll(result);
            if (username != null) {
                // we're good, add to client map
                this.clientSockets.add(clientConnect);
                MCAUtils.addSystemMessageToChat(MCAPlatform.I2P, Text.literal(
                        "Connected to " + username));
            } else {
                // not sure username, close and their connection
                clientConnect.close();
            }
            return true;
        } catch (Exception e) {
            this.logger.error("Error when connect to " + username, e);
            return false;
        }
    }

    /**
     * Send message to a user. Async.
     * Return true if success.
     */
    public CompletableFuture<Boolean> sendMessage(@NotNull MessagePayload chatMessage, @NotNull String user) {
        return CompletableFuture.supplyAsync(() -> {
            var tx = this.clientSockets.stream().filter(it -> user.equals(it.username)).findAny();
            if (tx.isEmpty()) {
                this.logger.warn("User '{}' not found", user);
                return false;
            } else {
                return tx.get().message(chatMessage);
            }
        });
    }

    /**
     * Send message to some users.
     * Return the list of failed users.
     */
    public List<String> sendMessage(@NotNull MessagePayload message, @NotNull List<String> users) {
        var map = new HashMap<String, CompletableFuture<Boolean>>();
        users.forEach(user -> map.put(user, sendMessage(message, user)));
        return map.entrySet().parallelStream()
                .map(entry -> {
                    try {
                        if (entry.getValue().get()) {
                            // send success
                            return null;
                        } else {
                            // failed
                            return entry.getKey();
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        this.logger.error("Failed to send message to " + entry.getKey(), e);
                        return entry.getKey();
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Send message to all known users.
     * Return the list of failed users.
     */
    public List<String> broadcastMessage(@NotNull String message) {
        var playerList = MCAUtils.getPlayerList();
        if (playerList != null) {
            return sendMessage(new MessagePayload("broadcast", message), playerList);
        }
        return null;
    }

    @Override
    public void close() throws I2PException {
        this.clientSockets.forEach(I2PConnection::close);
        this.clientSockets.clear();
        this.serverSocket.close();
    }

}
