package info.skyblond.mc.mca.i2p;

import com.google.gson.Gson;
import info.skyblond.mc.mca.model.MCAMessage;
import info.skyblond.mc.mca.model.MCAPlatform;
import net.i2p.I2PException;
import net.i2p.client.streaming.I2PServerSocket;
import net.i2p.client.streaming.I2PSocketManager;
import net.i2p.client.streaming.I2PSocketManagerFactory;
import net.i2p.client.streaming.RouterRestartException;
import net.i2p.data.Destination;
import net.i2p.util.I2PThread;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static info.skyblond.mc.mca.MinecraftChatAlternative.addBroadcastMessageToChat;

/**
 * This is the peer in this i2p p2p chat system.
 * It has a server (receive messages from others),
 * and a lot of clients (sending messages to others).
 */
@SuppressWarnings("unused")
public class I2PChatPeer implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(I2PChatPeer.class);
    private final I2PSocketManager manager = I2PSocketManagerFactory.createManager();
    private final I2PServerSocket serverSocket;
    private final Gson gson;

    public Destination getMyDestination() {
        return this.manager.getSession().getMyDestination();
    }

    public I2PChatPeer(Gson gson) {
        this.gson = gson;
        this.serverSocket = manager.getServerSocket();
        // start server thread
        var t = new I2PThread(() -> {
            while (true) {
                try {
                    var socket = this.serverSocket.accept();
                    if (socket != null) {
                        // handle socket
                        I2PThread thread = new I2PThread(() -> {
                            try (socket) {
                                //Receive from clients
                                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                //Send to clients
                                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                                String line;
                                while ((line = br.readLine()) != null) {
                                    var message = gson.fromJson(line, MCAMessage.class);
                                    if (message != null) {
                                        // TODO handle message
                                        var sourceProfile = resolveSource(socket.getPeerDestination());
                                        if (sourceProfile != null) {
                                            switch (message.parseAction()) {
                                                case BROADCAST -> addBroadcastMessageToChat(
                                                        MCAPlatform.I2P,
                                                        // TODO json to Component?
                                                        sourceProfile, Text.literal(message.payload()));
                                                case WHISPER -> {
                                                    // TODO
                                                }
                                                case PEER_EXCHANGE -> {
                                                    // TODO: Trackers? User sign?
                                                }
                                                default -> logger.warn("Unknown message action '{}'", message.action());
                                            }
                                        } else {
                                            logger.warn("Unknown message source '{}'", socket.getPeerDestination().getHash().toBase64());
                                        }
                                    }
                                    // TODO response based on reply
                                    bw.write("ok\n");
                                    bw.flush();
                                }
                            } catch (IOException e) {
                                this.logger.error("Failed to handling client", e);
                            }
                        });
                        thread.setName("ClientHandler");
                        thread.setDaemon(false);
                        thread.start();
                    }
                } catch (RouterRestartException e) {
                    this.logger.error("I2P router restarted when accepting new connections", e);
                } catch (I2PException e) {
                    this.logger.error("Communication error between i2p router (I2PSession)", e);
                } catch (ConnectException e) {
                    this.logger.error("I2P server socket closed", e);
                    this.logger.warn("Receiver stopped because server socket is closed");
                    break;
                } catch (SocketTimeoutException e) {
                    this.logger.error("Socket timeout", e);
                }
            }
            logger.info("Receiver stopped");
        });
        t.setDaemon(false);
        t.setName("I2PMessageRx");
        t.start();
    }

    private final ConcurrentHashMap<String, I2PMessageTx> transmitters = new ConcurrentHashMap<>();

    /**
     * Find the user with given dest.
     * Return null if not found.
     */
    public String resolveSource(Destination source) {
        var optionTx = this.transmitters.entrySet().stream()
                .filter(e -> e.getValue().getDest().equals(source))
                .findAny();
        if (optionTx.isEmpty()) {
            return null;
        } else {
            return optionTx.get().getKey();
        }
    }

    /**
     * Return ture if success.
     * Either success or fail, the map and the list will be updated.
     */
    public boolean connect(String user, Destination destination) {
        AtomicBoolean result = new AtomicBoolean(false);
        this.transmitters.compute(user, (u, tx) -> {
            var t = Objects.requireNonNullElseGet(tx, () -> new I2PMessageTx(this.gson, this.manager));
            result.set(t.connect(destination));
            return t;
        });
        return result.get();
    }

    /**
     * Send message to a user. Async.
     * Return true if success.
     */
    public CompletableFuture<Boolean> sendMessage(MCAMessage message, String user) {
        return CompletableFuture.supplyAsync(() -> {
            var tx = this.transmitters.get(user);
            if (tx == null) {
                this.logger.warn("User '{}' not found", user);
                return false;
            } else {
                return tx.sendMessage(message);
            }
        });
    }

    /**
     * Send message to some users.
     * Return the list of failed users.
     */
    public List<String> sendMessage(MCAMessage message, List<String> users) {
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
    public List<String> sendMessage(MCAMessage message) {
        return sendMessage(message, this.transmitters.keySet().stream().toList());
    }

    /**
     * Disconnect all clients.
     * This is not thread-safe: Do not calling this while sending messages.
     * TODO: Call this on exit server
     */
    public void reset() {
        this.transmitters.forEach((username, tx) -> tx.close());
        this.transmitters.clear();
    }

    @Override
    public void close() throws I2PException {
        this.transmitters.forEach((username, tx) -> tx.close());
        this.serverSocket.close();
    }
}
