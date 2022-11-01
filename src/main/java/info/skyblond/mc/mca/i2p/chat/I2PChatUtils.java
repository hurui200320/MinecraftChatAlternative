package info.skyblond.mc.mca.i2p.chat;

import net.i2p.data.Destination;

public class I2PChatUtils {
    public static String getDestinationHash(Destination dest) {
        try {
            var hash = dest.getHash();
            if (hash != null) {
                return hash.toBase64();
            } else {
                return null;
            }
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
