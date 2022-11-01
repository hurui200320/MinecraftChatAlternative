package info.skyblond.mc.mca.i2p.chat.rx;

import net.i2p.data.Destination;

/**
 * The listener for incoming message.
 * */
public interface InComingMessageListener<T> {
    /**
     * Do things when there is a new incoming message.
     * The listener should be thread safe, since
     * the listener will be called on different threads.
     *
     * @param message The parsed incoming message
     * @param source  The address of who sent this message
     * */
    void onInComingMessage(T message, Destination source);
}
