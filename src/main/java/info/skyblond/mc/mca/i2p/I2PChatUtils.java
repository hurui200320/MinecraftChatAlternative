package info.skyblond.mc.mca.i2p;

import net.i2p.client.streaming.I2PSocket;
import net.i2p.data.Destination;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;

public class I2PChatUtils {
    public static String getDestinationHash(@NotNull I2PSocket socket){
        return getDestinationHash(socket.getPeerDestination());
    }

    public static String getDestinationHash(@NotNull Destination dest) {
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

    public static void writeLine(@NotNull BufferedWriter bw, @NotNull String line) throws IOException {
        bw.write(line);
        if (!line.endsWith("\n")) {
            bw.write("\n");
        }
        bw.flush();
    }
}
