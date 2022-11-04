package info.skyblond.mc.mca.i2p;

import net.i2p.client.streaming.I2PSocket;
import net.i2p.data.Destination;

import java.io.BufferedWriter;
import java.io.IOException;

public class I2PChatUtils {
    public static String getDestinationHash(I2PSocket socket){
        return getDestinationHash(socket.getPeerDestination());
    }

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

    public static void writeLine(BufferedWriter bw, String line) throws IOException {
        bw.write(line);
        if (!line.endsWith("\n")) {
            bw.write("\n");
        }
        bw.flush();
    }
}
