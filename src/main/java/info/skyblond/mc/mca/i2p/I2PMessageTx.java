package info.skyblond.mc.mca.i2p;

import com.google.gson.Gson;
import info.skyblond.mc.mca.model.MCAMessage;
import net.i2p.I2PException;
import net.i2p.client.streaming.I2PSocket;
import net.i2p.client.streaming.I2PSocketManager;
import net.i2p.data.Destination;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.util.function.Function;

/**
 * A thread-safe message transmitter.
 */
public class I2PMessageTx implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(I2PMessageTx.class);

    private final Gson gson;
    private final I2PSocketManager manager;


    public I2PMessageTx(Gson gson, I2PSocketManager manager) {
        this.gson = gson;
        this.manager = manager;
    }

    private Destination dest;

    public Destination getDest() {
        return this.dest;
    }

    // The visibility of those variables rely on synchronized.
    private I2PSocket socket;
    private BufferedReader br;
    private BufferedWriter bw;

    /**
     * Return true if connect success.
     */
    public synchronized boolean connect(@NotNull Destination dest) {
        if (dest.equals(this.dest) && this.socket != null && !this.socket.isClosed()) {
            // if already connected to the same dest
            // and connection is still alive
            // skip connect and return success
            return true;
        }

        close();

        this.dest = dest;
        try {
            this.socket = this.manager.connect(dest);
            this.br = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            this.bw = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
            return true;
        } catch (I2PException e) {
            this.logger.error("General i2p exception occurred", e);
            return false;
        } catch (ConnectException e) {
            this.logger.error("Connection refused by peer", e);
            return false;
        } catch (NoRouteToHostException e) {
            this.logger.error("Peer not found or not reachable", e);
            return false;
        } catch (InterruptedIOException e) {
            this.logger.error("Connection timeout", e);
            return false;
        } catch (IOException e) {
            this.logger.error("Failed to get I/O streams", e);
            return false;
        }
    }

    /**
     * Return true if send success.
     */
    public synchronized boolean sendMessage(@NotNull MCAMessage message) {
        if (this.socket == null || this.socket.isClosed()) {
            return false;
        }

        try {
            var text = gson.toJson(message);
            this.bw.write(text);
            if (!text.endsWith("\n")) {
                this.bw.write("\n");
            }
            this.bw.flush();
            String response = this.br.readLine();
            if (response.equals("ok")) {
                return true;
            } else {
                this.logger.warn("Peer responds '{}', expect 'ok'", response);
                return false;
            }
        } catch (IOException e) {
            this.logger.error("Error occurred while sending message", e);
            return false;
        }
    }

    @Override
    public synchronized void close() {
        if (this.socket != null) {
            try {
                this.socket.close();
            } catch (IOException e) {
                this.logger.error("Failed to close socket", e);
            }
            this.socket = null;
            this.br = null;
            this.bw = null;
        }
    }
}
