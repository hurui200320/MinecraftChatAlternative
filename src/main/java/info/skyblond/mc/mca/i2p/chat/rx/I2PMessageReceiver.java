package info.skyblond.mc.mca.i2p.chat.rx;

import info.skyblond.mc.mca.i2p.chat.I2PChatUtils;
import net.i2p.I2PException;
import net.i2p.client.streaming.I2PServerSocket;
import net.i2p.client.streaming.I2PSocketManager;
import net.i2p.client.streaming.RouterRestartException;
import net.i2p.data.Destination;
import net.i2p.util.I2PThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A thread-safe message receiver.
 */
public class I2PMessageReceiver<T> implements AutoCloseable, Runnable {
    private final Logger logger = LoggerFactory.getLogger(I2PMessageReceiver.class);

    private final Function<String, T> messageConverter;
    private final I2PSocketManager manager;
    private final I2PServerSocket serverSocket;

    private final List<InComingMessageListener<T>> listeners = new CopyOnWriteArrayList<>();

    public I2PMessageReceiver(
            Function<String, T> messageConverter,
            I2PSocketManager manager
    ) {
        this.messageConverter = messageConverter;
        this.manager = manager;
        this.serverSocket = manager.getServerSocket();
    }

    public Destination getMyDestination() {
        return this.manager.getSession().getMyDestination();
    }

    public void addInComingMessageListener(InComingMessageListener<T> listener) {
        this.listeners.add(listener);
    }

    public void removeInComingMessageListener(InComingMessageListener<T> listener) {
        this.listeners.remove(listener);
    }

    @Override
    public void run() {
        while (true) {
            try {
                var socket = this.serverSocket.accept();
                if (socket != null) {
                    var dest = I2PChatUtils.getDestinationHash(socket.getPeerDestination());
                    this.logger.info("Handling client {}", dest);
                    // handle socket
                    I2PThread thread = new I2PThread(() -> {
                        try (socket) {
                            //Receive from clients
                            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            //Send to clients
                            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                            String line;
                            while ((line = br.readLine()) != null) {
                                var message = this.messageConverter.apply(line);
                                if (message != null) {
                                    this.listeners.forEach(l -> l.onInComingMessage(message, socket.getPeerDestination()));
                                }
                                bw.write("ok\n");
                                bw.flush();
                            }
                            this.logger.info("Done with client {}", dest);
                        } catch (IOException e) {
                            this.logger.error("Failed to handling client " + dest, e);
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
    }

    @Override
    public void close() throws I2PException {
        this.serverSocket.close();
    }

}
