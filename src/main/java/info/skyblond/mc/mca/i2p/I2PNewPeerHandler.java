package info.skyblond.mc.mca.i2p;

import info.skyblond.mc.mca.MCAUtils;
import info.skyblond.mc.mca.model.AuthPayload;
import net.i2p.data.Destination;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

class I2PNewPeerHandler implements Runnable {
    private final ConcurrentLinkedQueue<AuthPayload> newPeersQueue;
    private final BiConsumer<String, Destination> connectPeer;


    I2PNewPeerHandler(ConcurrentLinkedQueue<AuthPayload> newPeersQueue, CopyOnWriteArrayList<I2PConnection> clientSockets, BiConsumer<String, Destination> connectPeer) {
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
                    if (!newPeer.verify()) {
                        continue;
                    }
                } catch (Exception e) {
                    // failed to auth
                    continue;
                }
                var peerProfile = playerList.stream()
                        .filter(it -> it.getProfile().getId().equals(newPeer.profileUUID()))
                        .findAny();
                if (peerProfile.isEmpty()) {
                    // not in same server
                    continue;
                }
                var username = peerProfile.get().getProfile().getName();
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
