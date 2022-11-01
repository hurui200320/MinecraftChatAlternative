package info.skyblond.mc.mca;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.i2p.client.streaming.I2PSocketManager;
import net.i2p.client.streaming.I2PSocketManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class MinecraftChatAlternative implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("MinecraftChatAlternative");
    @Override
    public void onInitializeClient() {
        LOGGER.info("Hello!");
        I2PSocketManager manager = I2PSocketManagerFactory.createManager();
        LOGGER.info("I2P name: {}", manager.getName());
    }
}
