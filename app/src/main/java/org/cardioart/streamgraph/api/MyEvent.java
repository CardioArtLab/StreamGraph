package org.cardioart.streamgraph.api;

/**
 * Created by jirawat on 08/09/2014.
 */
public class MyEvent {
    public static final int STATE_BT_SERVER_UP       = 0;
    public static final int STATE_BT_SERVER_DOWN     = 1;
    public static final int STATE_BT_RX_UP           = 2;
    public static final int STATE_BT_RX_DOWN         = 3;
    public static final int STATE_WIFI_CLIENT_UP     = 4;
    public static final int STATE_WIFI_CLIENT_DOWN   = 5;
    public static final int STATE_WIFI_TX_UP         = 6;
    public static final int STATE_WIFI_TX_DOWN       = 7;
    public static final int STATE_INTERNET_THREAD_START = 8;
    public static final int STATE_INTERNET_THREAD_STOP = 9;
    public static final int STATE_INTERNET_THREAD_MSG = 10;
    public static final int STATE_PACKETREADER_THREAD_START = 11;
    public static final int STATE_PACKETREADER_THREAD_STOP = 12;
    public static final int STATE_PACKETREADER_THREAD_MSG = 13;
    public static final int STATE_DEBUG_MSG          = 100;
    public static final int STATE_DEBUG_TX           = 101;
    public static final int STATE_DEBUG_RX           = 102;
    public static final int STATE_DEBUG_PACKET       = 103;
    public static final int REQUEST_ENABLE_BT = 2718;
}
