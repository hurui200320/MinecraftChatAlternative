package info.skyblond.mc.mca.i2p;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import info.skyblond.mc.mca.MCAUtils;
import net.i2p.I2PAppContext;
import net.i2p.client.streaming.I2PSocketEepGet;
import net.i2p.client.streaming.I2PSocketManager;
import net.i2p.crypto.SHA1;
import net.i2p.data.Destination;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

// TODO Tracker announce sometimes work, sometimes not.
public class I2PTracker implements Runnable {
    // This charset accepts all byte values
    private static final Charset CHARSET = StandardCharsets.ISO_8859_1;

    private final Logger logger = LoggerFactory.getLogger(I2PTracker.class);
    @NotNull
    private final I2PSocketManager manager;
    @NotNull
    private final String announceUrl;
    private final byte[] myPeerId = RandomUtils.nextBytes(20);
    @NotNull
    private final Destination myDestination;
    @NotNull
    private final BiConsumer<String, Destination> connectPeer;

    // TODO InfoHash per server?
    //      Use single peer might not able to fetch the peer in same server
    private final byte[] infoHash = SHA1.getInstance().digest("MinecraftChatAlternative-I2P".getBytes(StandardCharsets.UTF_8));

    private final AtomicLong lastAnnounceTime = new AtomicLong(0);
    private final AtomicLong announceInterval = new AtomicLong(Long.MAX_VALUE);

    public I2PTracker(
            @NotNull I2PSocketManager manager,
            @NotNull String announceUrl,
            @NotNull Destination myDestination,
            @NotNull BiConsumer<@Nullable String, @NotNull Destination> connectPeer) {
        this.manager = manager;
        this.announceUrl = announceUrl;
        this.myDestination = myDestination;
        this.connectPeer = connectPeer;
        this.logger.info("Starting tracker {}", announceUrl);
        MCAUtils.runLater(() -> {
            // do start announce
            while (doAnnounce("started") == null) {
                this.logger.warn("Initial announcing failed on {}", announceUrl);
                MCAUtils.sleepInaccurate(10_000);
            }
            MCAUtils.sleepInaccurate(10 * 1000);
            while (doAnnounce() == null) {
                this.logger.warn("Second announcing failed on {}", announceUrl);
                MCAUtils.sleepInaccurate(10_000);
            }
            this.lastAnnounceTime.set(System.currentTimeMillis());
            this.logger.info("Tracker start: {}", announceUrl);
        });
    }

    @Override
    public void run() {
        //noinspection InfiniteLoopStatement
        while (true) {
            if (System.currentTimeMillis() < this.lastAnnounceTime.get() + this.announceInterval.get()) {
                MCAUtils.sleepInaccurate(30 * 1000);
                // wait 1 minutes
                continue;
            }
            try {
                var result = doAnnounce();
                if (result != null) {
                    if (result.get("peers") instanceof List<?> peers) {
                        handlePeers(peers);
                    } else {
                        this.logger.error("Announced but no peers returned: {}", result);
                    }
                } else {
                    this.logger.error("Failed to announce");
                }
            } catch (Throwable t) {
                this.logger.error("Failed to announce", t);
            }
        }
    }

    private void handlePeers(List<?> peers) {
        this.logger.info("Fetched {} peer(s)", peers.size());
        peers.forEach(p -> {
            if (p instanceof Map<?, ?> pp) {
                var dest = getDestination(pp);
                if (dest == null) {
                    return;
                }
                // use null to close after exchange peers
                this.connectPeer.accept(null, dest);
            }
        });
    }

    private Destination getDestination(Map<?, ?> pp) {
        if (pp.get("ip") instanceof String s) {
            try {
                return new Destination(s);
            } catch (Throwable t) {
                return null;
            }
        } else {
            return null;
        }
    }

    private Map<String, Object> doAnnounce() {
        return doAnnounce(null);
    }

    private synchronized Map<String, Object> doAnnounce(String event) {
        var url = this.announceUrl +
                "?info_hash=" + urlEncode(this.infoHash) +
                "&peer_id=" + urlEncode(this.myPeerId) +
                "&ip=" + this.myDestination.toBase64() +
                "&port=" + 9 +
                "&uploaded=0" +
                "&downloaded=0" +
                "&left=130311984";
//                "&numwant=10";
        byte[] resp = doI2PGet(event == null ? url : url + "&event=started");
        if (resp == null) {
            return null;
        }
        try {
            Bencode bencode = new Bencode(CHARSET);
            Map<String, Object> result = bencode.decode(resp, Type.DICTIONARY);

            if (event == null) {
                this.announceInterval.set(60 * 1000);
            } else {
                // with started event, shorten the interval
                this.announceInterval.set(20 * 1000);
            }
            this.logger.info("Next announce in {}s", this.announceInterval.get() / 1000);

            this.lastAnnounceTime.set(System.currentTimeMillis());
            return result;
        } catch (Throwable t) {
            this.logger.error("Failed to decode bencode", t);
            return null;
        }
    }

    private byte[] doI2PGet(String url) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        var get = new I2PSocketEepGet(I2PAppContext.getGlobalContext(), this.manager,
                3, -1, -1, null, out, url);
        if (!get.fetch(45 * 1000)) {
            this.logger.warn("Timeout on tracker {}", announceUrl);
            return null;
        }
        if (get.getStatusCode() == 200) {
            return out.toByteArray();
        } else {
            this.logger.warn("Wrong response: {}\nAt: {}", out.toString(CHARSET), url);
            return null;
        }
    }

    private static String urlEncode(byte[] binary) {
        return URLEncoder.encode(new String(binary, CHARSET), CHARSET);
    }
}
