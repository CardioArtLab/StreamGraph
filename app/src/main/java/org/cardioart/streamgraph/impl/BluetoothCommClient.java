package org.cardioart.streamgraph.impl;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import org.cardioart.streamgraph.api.BluetoothConnection;
import org.cardioart.streamgraph.api.MyEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Created by jirawat on 27/10/2014.
 */
public class BluetoothCommClient extends BluetoothConnection {

    private static final UUID MY_UUID       = UUID.fromString("513a6c9c-ea27-4abd-90c0-6997dd532866");
    private static final String TAG         = "streamgraph";
    private static final String SDP_NAME    = "BtClient";

    private Handler mainHandler;
    private Handler mHandler;
    private ConnectThread connectThread;
    private BluetoothAdapter mAdapter;
    private CommState commState;
    private long rxByte = 0;
    private long txByte = 0;
    private boolean mAllowInsecureConnections;

    public  BluetoothCommClient(Handler handler) {
        mainHandler = handler;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        commState = CommState.NONE;
        mAllowInsecureConnections = true;
        HandlerThread handlerThread = new HandlerThread("HandlerThread");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                //process handle message
                if (msg.what == MyEvent.STATE_BT_RX_UP) {
                    mainHandler.obtainMessage(MyEvent.STATE_BT_RX_UP).sendToTarget();
                } else if (msg.what == MyEvent.STATE_BT_RX_DOWN) {
                    mainHandler.obtainMessage(MyEvent.STATE_BT_RX_DOWN).sendToTarget();
                } else if (msg.what == MyEvent.STATE_INTERNET_THREAD_MSG) {
                    //Log. transmission speed
                    synchronized (this) {
                        rxByte += msg.arg2;
                    }
                    mainHandler.obtainMessage(MyEvent.STATE_INTERNET_THREAD_MSG, msg.obj).sendToTarget();
                    //Log.d(TAG, "Read " + msg.arg2);
                } else {
                    Log.d(TAG, "mHandler ELSE");
                }
            }
        };
    }
    @Override
    public void startListening() {}

    @Override
    public void stopListening() {}

    @Override
    public synchronized void startConnection(BluetoothDevice device) {
        stopConnection();
        connectThread = new ConnectThread(this, device, mHandler);
        connectThread.start();
    }
    @Override
    public void startConnection(BluetoothSocket socket) {}
    @Override
    public synchronized void stopConnection() {
        if (connectThread != null) {
            connectThread.interrupt();
            connectThread.cancel();
        }
    }
    public void setCommState(CommState state) {
        commState = state;
    }

    public synchronized long getRxSpeed() {
        long buffer = rxByte;
        rxByte = 0;
        return buffer;
    }
    public synchronized long getTxSpeed() {
        long buffer = txByte;
        txByte = 0;
        return buffer;
    }
    public void stop () {
        stopConnection();
    }
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private InputStream mInputStream;
        private OutputStream mOutputStream;
        private WeakReference<BluetoothCommClient> commClient;
        private boolean hasStartTimeoutTask = false;
        private final Handler mHandler;

        public ConnectThread(BluetoothCommClient self, BluetoothDevice device, Handler handler) {

            commClient = new WeakReference<BluetoothCommClient>(self);
            mmDevice = device;
            mHandler = handler;
            BluetoothSocket tmp = null;

            //Get bluetoothSocket to connect with the given BluetoothDevice
            try {
                if ( mAllowInsecureConnections ) {
                    Method method;
                    method = device.getClass().getMethod("createRfcommSocket", int.class);
                    tmp = (BluetoothSocket) method.invoke(device, 1);
                } else {
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                }
            } catch (Exception e) {
                Log.e(TAG, "Cannot connect to BT service" + MY_UUID.toString());
            }
            mmSocket = tmp;
        }
        public void run() {
            Log.d(TAG, "BEGIN Connect to BT");
            try {
                mmSocket.connect();
                mInputStream = mmSocket.getInputStream();
                mOutputStream = mmSocket.getOutputStream();
                commClient.get().setCommState(CommState.CONNECTED);
                mHandler.obtainMessage(MyEvent.STATE_BT_RX_UP).sendToTarget();
                while(!isInterrupted()) {
                    try {
                        int byteLength = mInputStream.available();
                        if (byteLength <= 0) {
                            if (!hasStartTimeoutTask) {
                                mHandler.postDelayed(timeOutCancelTask, 3000);
                                hasStartTimeoutTask = true;
                            }
                        } else {
                            if (hasStartTimeoutTask) {
                                mHandler.removeCallbacks(timeOutCancelTask);
                                hasStartTimeoutTask = false;
                            }
                            //Read data from Bluetooth Service
                            byte[] buffer = new byte[byteLength];
                            int readStatus, startPacketPosition;
                            readStatus = mInputStream.read(buffer, 0, byteLength);
                            //Send Message to Handle
                            mHandler.obtainMessage(MyEvent.STATE_INTERNET_THREAD_MSG, readStatus, byteLength, buffer).sendToTarget();
                        }
                    } catch (IOException e) {
                        Log.d(TAG, "DISCONNECT", e);
                        commClient.get().setCommState(CommState.DISCONNECTED);
                        mHandler.obtainMessage(MyEvent.STATE_BT_RX_DOWN).sendToTarget();
                        break;
                    }
                }
            } catch (IOException exception) {
                Log.d(TAG, "ERROR in running");
            } finally {
               cancel();
            }
        }
        public void write() {

        }
        public void cancel() {
            try {
                mmSocket.close();
                commClient.get().setCommState(CommState.DISCONNECTED);
                mHandler.obtainMessage(MyEvent.STATE_BT_RX_DOWN).sendToTarget();
            } catch (IOException e) {
                Log.d(TAG, "END connection");
            }
        }

        private Runnable timeOutCancelTask = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "TIMEOUT connectedThread");
                interrupt();
                cancel();
            }
        };
    }
}
