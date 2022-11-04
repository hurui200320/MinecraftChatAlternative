package info.skyblond.mc.mca.i2p;

import info.skyblond.mc.mca.model.AuthPayload;
import net.i2p.I2PException;
import net.i2p.client.streaming.I2PServerSocket;
import net.i2p.client.streaming.I2PSocket;
import net.i2p.client.streaming.RouterRestartException;
import net.i2p.util.I2PThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

class I2PServerSocketHandler implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(I2PServerSocketHandler.class);

    private final I2PServerSocket serverSocket;
    private final ConcurrentHashMap<String, AuthPayload> knownIds;
    private final ConcurrentLinkedQueue<AuthPayload> newPeersQueue;
    private final Function<I2PSocket, String> getConnectionUsername;

    public I2PServerSocketHandler(
            I2PServerSocket serverSocket,
            ConcurrentHashMap<String, AuthPayload> knownIds,
            ConcurrentLinkedQueue<AuthPayload> newPeersQueue,
            Function<I2PSocket, String> getConnectionUsername
    ) {
        this.serverSocket = serverSocket;
        this.knownIds = knownIds;
        this.newPeersQueue = newPeersQueue;
        this.getConnectionUsername = getConnectionUsername;
    }

    @Override
    public void run() {
        while (true) {
            try {
                var socket = this.serverSocket.accept();
                if (socket != null) {
                    // handle socket in new thread
                    I2PThread thread = new I2PThread(new I2PClientSocketHandler(
                            socket, this.knownIds, this.newPeersQueue,
                            () -> this.getConnectionUsername.apply(socket)));
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
        this.logger.info("Receiver stopped");
    }
}