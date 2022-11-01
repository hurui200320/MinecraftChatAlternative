package info.skyblond.mc.mca.i2p.chat;

import info.skyblond.mc.mca.i2p.chat.rx.I2PMessageReceiver;
import info.skyblond.mc.mca.i2p.chat.tx.I2PMessageTransmitter;
import net.i2p.I2PException;
import net.i2p.client.streaming.I2PSocketManager;
import net.i2p.client.streaming.I2PSocketManagerFactory;
import net.i2p.data.Destination;
import net.i2p.util.I2PThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * This is a peer in this i2p p2p chat system.
 * It has a server (receive messages from others),
 * and a lot of clients (sending messages to others).
 */
@SuppressWarnings("unused")
public class I2PChatPeer<UserType, MessageType> implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(I2PChatPeer.class);
    private final I2PSocketManager manager = I2PSocketManagerFactory.createManager();
    private final I2PMessageReceiver<MessageType> messageReceiver;

    public I2PMessageReceiver<MessageType> getMessageReceiver() {
        return this.messageReceiver;
    }

    private final Function<MessageType, String> messageToStringConverter;

    public I2PChatPeer(
            Function<String, MessageType> stringToMessageConverter,
            Function<MessageType, String> messageToStringConverter
    ) {
        this.messageToStringConverter = messageToStringConverter;
        this.messageReceiver = new I2PMessageReceiver<>(stringToMessageConverter, this.manager);
        // start server thread
        var t = new I2PThread(this.messageReceiver);
        t.setDaemon(false);
        t.setName("I2PMessageRx");
        t.start();
    }

    private final ConcurrentHashMap<UserType, I2PMessageTransmitter<MessageType>> transmitters = new ConcurrentHashMap<>();

    /**
     * Find the user with given dest.
     * Return null if not found.
     */
    public UserType resolveSource(Destination source) {
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
    public boolean connect(UserType user, Destination destination) {
        if (this.messageReceiver.getMyDestination().equals(destination)) {
            // no self-connection
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(false);
        this.transmitters.compute(user, (u, tx) -> {
            var t = Objects.requireNonNullElseGet(tx, () -> new I2PMessageTransmitter<>(this.messageToStringConverter, this.manager));
            result.set(t.connect(destination));
            return t;
        });
        return result.get();
    }

    /**
     * Send message to a user. Async.
     * Return true if success.
     */
    public CompletableFuture<Boolean> sendMessage(MessageType message, UserType user) {
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
    public List<UserType> sendMessage(MessageType message, List<UserType> users) {
        var map = new HashMap<UserType, CompletableFuture<Boolean>>();
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
    public List<UserType> sendMessage(MessageType message) {
        return sendMessage(message, this.transmitters.keySet().stream().toList());
    }

    @Override
    public void close() throws I2PException {
        this.transmitters.forEach((username, tx) -> tx.close());
        this.messageReceiver.close();
    }
}
