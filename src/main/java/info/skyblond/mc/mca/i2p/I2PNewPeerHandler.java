package info.skyblond.mc.mca.i2p;

import info.skyblond.mc.mca.MCAUtils;
import info.skyblond.mc.mca.model.AuthPayload;
import net.i2p.data.Destination;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;

class I2PNewPeerHandler implements Runnable {
    @NotNull
    private final ConcurrentLinkedQueue<AuthPayload> newPeersQueue;
    @NotNull
    private final BiConsumer<String, Destination> connectPeer;


    I2PNewPeerHandler(
            @NotNull ConcurrentLinkedQueue<AuthPayload> newPeersQueue,
            @NotNull BiConsumer<@Nullable String, @NotNull Destination> connectPeer
    ) {
        this.newPeersQueue = newPeersQueue;
        this.connectPeer = connectPeer;
    }

    @Override
    public void run() {
        //noinspection InfiniteLoopStatement
        while (true) {
            while (!this.newPeersQueue.isEmpty()) {
                var playerList = MCAUtils.getPlayerList();
                if (playerList == null) {
                    // can't get player list, break this loop
                    break;
                }
                var newPeer = this.newPeersQueue.poll();
                if (newPeer == null) {
                    // empty queue, break this loop
                    break;
                }
                try {
                    if (newPeer.verifyFailed()) {
                        continue;
                    }
                } catch (Exception e) {
                    // failed to auth
                    continue;
                }
                if (playerList.stream()
                        .noneMatch(it -> it.equals(newPeer.username()))) {
                    // not in same server
                    continue;
                }
                var username = newPeer.username();
                // we don't care since:
                //      a) if connect ok -> we connect to new peer
                //      b) if failed     -> peer is not available
                this.connectPeer.accept(username, newPeer.parseDestination());
            }
            // done, sleep
            MCAUtils.sleepInaccurate(3000);
        }
    }
}
