package info.skyblond.mc.mca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import info.skyblond.mc.mca.gson.PublicKeyDataTypeAdapter;
import info.skyblond.mc.mca.i2p.I2PChatPeer;
import info.skyblond.mc.mca.i2p.I2PTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.i2p.client.streaming.I2PSocketManager;
import net.i2p.client.streaming.I2PSocketManagerFactory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.encryption.PlayerPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Environment(EnvType.CLIENT)
public class MinecraftChatAlternative implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("MinecraftChatAlternative");
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(PlayerPublicKey.PublicKeyData.class, new PublicKeyDataTypeAdapter())
            .create();

    private static final I2PSocketManager manager = I2PSocketManagerFactory.createManager();
    public static final I2PChatPeer PEER = new I2PChatPeer(manager);

    private static final List<String> trackers = List.of(
            "http://w7tpbzncbcocrqtwwm3nezhnnsw4ozadvi2hmvzdhrqzfxfum7wa.b32.i2p/a"
    );

    @Override
    public void onInitializeClient() {
        LOGGER.info("Hello!");

        // start tracker
        LOGGER.info("Find {} tracker(s)", trackers.size());
        MCAUtils.runLater(() -> trackers.forEach(t -> MCAUtils.runLater(new I2PTracker(
                manager, t, PEER.getMyDestination(), PEER::connect))));

        MCAUtils.runLater(() -> {
            // TODO: Trigger this when loading new worlds/join servers?
            var minecraft = MinecraftClient.getInstance();
            while (minecraft.getProfileKeys() == null) {
                LOGGER.warn("Account profile keys not found");
                MCAUtils.sleepInaccurate(3000);
            }
            while (MCAUtils.getCurrentUsername() == null) {
                MCAUtils.sleepInaccurate(3000);
            }
            var self = MCAUtils.getCurrentUsername();
            // connect to self, so we can receive our own message
            while (!PEER.connect(self, PEER.getMyDestination())) {
                LOGGER.error("Failed to make self-connect, retry...");
                MCAUtils.sleepInaccurate(5000);
            }
            LOGGER.info("I2P self dest: " + PEER.getMyDestination().toBase64());
        });
    }

}
