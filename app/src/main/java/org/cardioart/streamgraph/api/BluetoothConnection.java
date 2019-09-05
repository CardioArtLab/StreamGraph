package org.cardioart.streamgraph.api;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

/**
 * Created by jirawat on 27/10/2014.
 */
public abstract class BluetoothConnection {
    public enum CommState {
        NONE,
        LISTEN,
        CONNECTED,
        DISCONNECTED;

        public static CommState fromInt(int x) {
            switch(x) {
                case 0:
                    return NONE;
                case 1:
                    return LISTEN;
                case 2:
                    return CONNECTED;
                case 3:
                    return DISCONNECTED;
            }
            return null;
        }
    }
    abstract public void startListening();
    abstract public void stopListening();
    abstract public void stop();
    abstract public void startConnection(BluetoothSocket socket);
    abstract public void startConnection(BluetoothDevice device);
    abstract public void stopConnection();
    abstract public long getRxSpeed();
    abstract public long getTxSpeed();
}
