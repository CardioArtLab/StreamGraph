package org.cardioart.streamgraph.api;

/**
 * Created by jirawat on 18/09/2014.
 */
public abstract class PacketReaderThread extends Thread implements Runnable{
    abstract public Integer[] getChannel(int index);
    abstract public void readPacket(byte[] data);
    abstract public long getTotalByteRead();
}
