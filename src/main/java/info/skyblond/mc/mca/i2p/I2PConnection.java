package info.skyblond.mc.mca.i2p;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import info.skyblond.mc.mca.MinecraftChatAlternative;
import info.skyblond.mc.mca.model.AuthPayload;
import info.skyblond.mc.mca.model.MCARequest;
import info.skyblond.mc.mca.model.MessagePayload;
import net.i2p.client.streaming.I2PSocket;
import net.i2p.data.Destination;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static info.skyblond.mc.mca.MinecraftChatAlternative.LOGGER;
import static info.skyblond.mc.mca.i2p.I2PChatUtils.writeLine;

public class I2PConnection implements AutoCloseable {
    private final Gson gson = MinecraftChatAlternative.GSON;

    @Nullable
    public final String username;
    @NotNull
    private final I2PSocket socket;
    @NotNull
    private final BufferedReader br;
    @NotNull
    private final BufferedWriter bw;
    @NotNull
    private final Consumer<I2PConnection> onSocketFailed;

    public I2PConnection(
            @Nullable String username, @NotNull I2PSocket socket,
            @NotNull Consumer<I2PConnection> onSocketFailed
    ) throws IOException {
        this.username = username;
        this.socket = socket;
        this.br = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.onSocketFailed = onSocketFailed;
    }

    public Destination getPeerDestination() {
        return this.socket.getPeerDestination();
    }

    public boolean auth(Destination myDestination) {
        var authPayload = AuthPayload.sign(myDestination);
        if (authPayload == null) {
            LOGGER.error("Failed to sign auth payload");
            return false;
        }
        var reply = clientSendAndRead(MCARequest.create(this.gson,
                "auth", authPayload).toJson(this.gson));
        if (reply == null) {
            LOGGER.error("Failed to get auth response");
            return false;
        }
        if (!reply.equals("ok")) {
            LOGGER.error("Failed to auth with {}: {}", this.username, reply);
            return false;
        } else {
            return true;
        }
    }

    /**
     * Return list of new peers. Null if failed.
     */
    public List<AuthPayload> exchange(List<AuthPayload> knownIds) {
        var reply = clientSendAndRead(MCARequest.create(this.gson,
                "exchange", knownIds).toJson(this.gson));
        if (reply == null) {
            LOGGER.error("Failed to exchange peers with {}", this.username);
            return null;
        }
        try {
            //noinspection unchecked
            var result = (List<AuthPayload>) this.gson.fromJson(reply,
                    TypeToken.getParameterized(List.class, AuthPayload.class));
            LOGGER.info("Exchanged {} peer(s) from {}", result.size(), this.username);
            return result;
        } catch (Throwable t) {
            LOGGER.error("Failed to exchange peers with " + this.username, t);
            return null;
        }
    }

    public boolean message(MessagePayload payload) {
        var reply = clientSendAndRead(MCARequest.create(this.gson,
                "message", payload).toJson(this.gson));
        if (reply == null) {
            LOGGER.error("Failed to send message to {}", this.username);
            return false;
        }
        if (!reply.equals("ok")) {
            LOGGER.error("Failed to send message to {}: {}", this.username, reply);
            return false;
        }
        return true;
    }

    /**
     * Send one line of message, read one line of reply and return.
     */
    private synchronized String clientSendAndRead(String line) {
        if (this.socket.isClosed()) {
            // already closed, but still called -> failure
            this.onSocketFailed.accept(this);
            return null;
        }
        // send line, we don't concert about `\r` for now
        var safeLine = line.split("\n")[0];
        try {
            writeLine(this.bw, safeLine);
        } catch (IOException e) {
            // failed
            this.onSocketFailed.accept(this);
            return null;
        }
        // read replay
        try {
            String reply = this.br.readLine();
            if (reply == null) {
                // got null, failed
                this.onSocketFailed.accept(this);
            }
            return reply;
        } catch (IOException e) {
            // failed to read
            this.onSocketFailed.accept(this);
            return null;
        }
    }

    public synchronized boolean isClosed() {
        return this.socket.isClosed();
    }

    @Override
    public synchronized void close() {
        try {
            this.socket.close();
        } catch (IOException ignore) {
            // It won't hurt anything if the close failed.
            // It might be already broken/closed
            // and since this is the close call, the called want to close.
            // no need to call onFailed callback.
        }
    }


}
