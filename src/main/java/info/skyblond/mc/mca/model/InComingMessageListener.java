package info.skyblond.mc.mca.model;

import info.skyblond.mc.mca.model.MCAMessage;
import net.i2p.data.Destination;

/**
 * The listener for incoming message.
 * */
public interface InComingMessageListener {
    /**
     * Do things when there is a new incoming message.
     * The listener should be thread safe, since
     * the listener will be called on different threads.
     *
     * @param message The parsed incoming message
     * @param source  The address of who sent this message
     * */
    void onInComingMessage(MCAMessage message, Destination source);
}
