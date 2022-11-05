package info.skyblond.mc.mca.i2p;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import info.skyblond.mc.mca.MCAUtils;
import info.skyblond.mc.mca.MinecraftChatAlternative;
import info.skyblond.mc.mca.model.AuthPayload;
import info.skyblond.mc.mca.model.MCAPlatform;
import info.skyblond.mc.mca.model.MCARequest;
import info.skyblond.mc.mca.model.MessagePayload;
import net.i2p.client.streaming.I2PSocket;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import static info.skyblond.mc.mca.MCAUtils.addBroadcastMessageToChat;
import static info.skyblond.mc.mca.MCAUtils.addIncomingMessageToChat;
import static info.skyblond.mc.mca.i2p.I2PChatUtils.getDestinationHash;
import static info.skyblond.mc.mca.i2p.I2PChatUtils.writeLine;

class I2PClientSocketHandler implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(I2PClientSocketHandler.class);

    @NotNull
    private final I2PSocket socket;
    private final Gson gson = MinecraftChatAlternative.GSON;

    @NotNull
    private final ConcurrentLinkedQueue<AuthPayload> newPeersQueue;
    @NotNull
    private final Supplier<String> getConnectionUsername;
    @NotNull
    private final ConcurrentLinkedQueue<AuthPayload> knownIds;

    public I2PClientSocketHandler(
            @NotNull I2PSocket socket,
            @NotNull ConcurrentLinkedQueue<AuthPayload> knownIds,
            @NotNull ConcurrentLinkedQueue<AuthPayload> newPeersQueue,
            @NotNull Supplier<String> getConnectionUsername) {
        this.socket = socket;
        this.newPeersQueue = newPeersQueue;
        this.getConnectionUsername = getConnectionUsername;
        this.knownIds = knownIds;
    }

    private String handleAuth(MCARequest request, BufferedWriter bw) throws IOException {
        var payload = request.parseJsonPayload(this.gson, AuthPayload.class);
        try {
            if (payload.verifyFailed()) {
                // failed to verify
                writeLine(bw, "Failed to verify your id payload");
                return null;
            }
            // check if we're in same server
            var playerList = MCAUtils.getPlayerList();
            if (playerList == null) {
                writeLine(bw, "Failed to get local player list");
                return null;
            }
            if (playerList.stream()
                    .noneMatch(it -> it.equals(payload.username()))) {
                writeLine(bw, "You're in different server");
                return null;
            }
            this.knownIds.stream()
                    .filter(it -> it.username().equals(payload.username()))
                    .findAny().ifPresent(this.knownIds::remove);
            this.knownIds.add(payload);
            // everything is ok, auth done
            writeLine(bw, "ok");
            return payload.username();
        } catch (Exception e) {
            // failed to verify
            writeLine(bw, e.getMessage());
            return null;
        }
    }

    private void handleExchange(MCARequest request, BufferedWriter bw) throws IOException {
        @SuppressWarnings("unchecked")
        var payload = (List<AuthPayload>) request.parseJsonPayload(this.gson,
                TypeToken.getParameterized(List.class, AuthPayload.class));
        // save to queue
        this.newPeersQueue.addAll(payload);
        // return all auth we know
        writeLine(bw, this.gson.toJson(this.knownIds.stream().toList()));
    }

    private void handleRequest(MCARequest request, BufferedWriter bw) throws IOException {
        switch (request.action()) {
            case "auth" -> handleAuth(request, bw);
            case "exchange" -> handleExchange(request, bw);
            case "message" -> {
                var payload = request.parseJsonPayload(this.gson, MessagePayload.class);
                var sourcePlayerName = this.getConnectionUsername.get();
                if (sourcePlayerName != null) {
                    switch (payload.type()) {
                        case "broadcast" -> addBroadcastMessageToChat(MCAPlatform.I2P,
                                sourcePlayerName, Text.literal(payload.content()));
                        case "whisper" -> addIncomingMessageToChat(MCAPlatform.I2P,
                                sourcePlayerName, Text.literal(payload.content()));
                        default -> this.logger.warn("Unknown message type '{}'", payload.type());
                    }

                    writeLine(bw, "ok");
                } else {
                    this.logger.warn("Unknown message source '{}'", getDestinationHash(this.socket));
                }
            }
            default -> this.logger.warn("Unknown message action '{}'", request.action());
        }
    }


    @Override
    public void run() {
        String username = null;
        try (this.socket) {
            //Receive from clients
            BufferedReader br = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8));
            //Send to clients
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream(), StandardCharsets.UTF_8));
            String line;
            // expect auth first
            line = br.readLine();
            if (line == null) { // broken socket
                return;
            }
            MCARequest request = this.gson.fromJson(line, MCARequest.class);
            if (!request.action().equals("auth")) {
                writeLine(bw, "You need send auth first");
                return;
            }
            // try auth
            username = this.handleAuth(request, bw);
            if (username == null) {
                // auth failed
                return;
            }
            // auth ok, enter read-loop
            MCAUtils.addSystemMessageToChat(MCAPlatform.I2P,
                    Text.literal("Incoming connection from " + username));
            while ((line = br.readLine()) != null) {
                request = this.gson.fromJson(line, MCARequest.class);
                if (request != null) {
                    this.handleRequest(request, bw);
                }
            }
        } catch (IOException e) {
            this.logger.error("Failed to handling client", e);
        }
        if (username != null) {
            MCAUtils.addSystemMessageToChat(MCAPlatform.I2P,
                    Text.literal("Lost incoming connection from " + username));
        }
    }
}
