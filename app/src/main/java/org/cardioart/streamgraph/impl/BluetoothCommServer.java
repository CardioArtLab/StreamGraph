package org.cardioart.streamgraph.impl;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
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
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by jirawat on 02/06/2014.
 */
public class BluetoothCommServer extends BluetoothConnection {

    public static final int ACTION_NOTIFY   = 0;
    public static final String COMMAND_KEY  = "command_key";
    public static final String COMMAND_START_LISTENING = "command_start_discovery";
    private static final String TAG         = "BtS";
    private static final UUID MY_UUID       = UUID.fromString("513a6c9c-ea27-4abd-90c0-6997dd532866");
    private static final String SDP_NAME    = "BtGateway";
    private static final Lock mLock         = new ReentrantLock();
    private static final Condition notConnected = mLock.newCondition();

    //private final Condition not = mLock.newCondition();
    private final Handler mHandle;
    private final Handler mainHandler;
    private final BluetoothAdapter mAdapter;

    private boolean mListening = false;
    private ListeningThread listeningThread;
    private ConnectedThread connectedThread;
    private CommState mCommState;
    private long rxByte = 0;
    private long txByte = 0;


    /**
     * Constructor
     * @param handler
     */
    public BluetoothCommServer(Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mCommState = CommState.NONE;
        mainHandler = handler;
        HandlerThread handlerThread = new HandlerThread("HandlerThread");
        handlerThread.start();
        mHandle = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                //process handle message
                if (msg.what == ACTION_NOTIFY) {
                    setState(CommState.fromInt(msg.arg1));
                    if (mCommState == CommState.NONE && listeningThread == null) {
                        startListening();
                    }
                    Log.d(TAG, "Msg: notify " + msg.arg1);
                } else if (msg.what == MyEvent.STATE_INTERNET_THREAD_MSG) {
                    //Log.
                    synchronized (this) {
                        rxByte += msg.arg2;
                    }
                    mainHandler.obtainMessage(MyEvent.STATE_INTERNET_THREAD_MSG, msg.obj).sendToTarget();

                } else {
                    Log.d(TAG, "mHandler ELSE");
                }
            }
        };
    }

    public synchronized void startListening() {
        //if (connectedThread != null) {
        //    stopConnectSocket();
        //}
        stopListening();
        listeningThread = new ListeningThread(this, mHandle);
        listeningThread.start();
    }

    public synchronized void stopListening() {
        if (listeningThread != null) {
            listeningThread.interrupt();
            listeningThread.cancel();
        }
    }
    public void startConnection(BluetoothDevice device) {}
    public synchronized void startConnection(BluetoothSocket socket) {
        //if (listeningThread != null) {
        //    stopListen();
        //}
        stopConnection();
        connectedThread = new ConnectedThread(this, socket, mHandle);
        connectedThread.start();
    }

    public synchronized void stopConnection() {
        if (connectedThread != null) {
            connectedThread.interrupt();
            connectedThread.cancel();
        }
    }
    public synchronized void stop() {
        stopListening();
        stopConnection();
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
    public synchronized CommState getState() {
        return mCommState;
    }
    public synchronized void setState(CommState commState) {
        mCommState = commState;
        if (commState == CommState.NONE) {
            mainHandler.obtainMessage(MyEvent.STATE_BT_RX_DOWN).sendToTarget();
            mainHandler.obtainMessage(MyEvent.STATE_BT_SERVER_DOWN).sendToTarget();
        } else if (commState == CommState.LISTEN) {
            mainHandler.obtainMessage(MyEvent.STATE_BT_SERVER_UP).sendToTarget();
        } else if (commState == CommState.CONNECTED) {
            mainHandler.obtainMessage(MyEvent.STATE_BT_RX_UP).sendToTarget();
        } else if (commState == CommState.DISCONNECTED) {
            mainHandler.obtainMessage(MyEvent.STATE_BT_RX_DOWN).sendToTarget();
        }
    }

    /**
     *  ListeningThread
     *  WAIT for incoming connections
     */
    private static class ListeningThread extends Thread {
        private final BluetoothServerSocket serverSocket;
        private final WeakReference<BluetoothCommServer> mBluetoothComm;
        private final Handler mHandler;
        public ListeningThread(BluetoothCommServer bluetoothCommServer, Handler handler) {
            mBluetoothComm = new WeakReference<BluetoothCommServer>(bluetoothCommServer);
            mHandler = handler;
            setName("ListeningThread");
            BluetoothServerSocket tmp = null;
            try {
                tmp = mBluetoothComm.get().mAdapter.listenUsingRfcommWithServiceRecord(SDP_NAME, MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "accept() fail ", e);
            }
            serverSocket = tmp;
            mBluetoothComm.get().setState(CommState.LISTEN);
        }
        public void run() {
            Log.d(TAG, "BEGIN listeningThread" + this);
            BluetoothSocket socket;
            mLock.lock();
            try {
                while (!interrupted()) {


                    while (mBluetoothComm.get().getState() == CommState.CONNECTED) {
                        notConnected.await();
                    }

                    try {
                        socket = serverSocket.accept();
                    } catch (IOException e) {
                        Log.d(TAG, "listen() fail");
                        break;
                    }
                    if (socket != null) {
                        mBluetoothComm.get().startConnection(socket);
                    }
                }
                Log.d(TAG, "EXIT LOOP listeningThread");
            } catch (InterruptedException e) {
                Log.d(TAG, "INT listeningThread");
            } finally {
                mLock.unlock();
            }

        }
        public void cancel() {
            Log.d(TAG, "END listeningThread");
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server fail", e);
            }
            mBluetoothComm.get().setState(CommState.NONE);
        }
    }


    /**
     * ConnectedThread
     * WAIT for input/output streaming
     */
    private static class ConnectedThread extends Thread {
        private final BluetoothSocket mSocket;
        private final InputStream mInputStream;
        private final OutputStream mOutputStream;
        private final Handler mHandler;
        private final WeakReference<BluetoothCommServer> mBluetoothComm;
        private volatile boolean hasStartedTimeoutTask = false;
        public ConnectedThread(BluetoothCommServer comm, BluetoothSocket socket, Handler handler) {
            mBluetoothComm = new WeakReference<BluetoothCommServer>(comm);
            mHandler = handler;
            mSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Temp socket not created", e);
            }
            mInputStream = tmpIn;
            mOutputStream = tmpOut;
            setName("ConnectedThread");
            mBluetoothComm.get().setState(CommState.CONNECTED);
        }
        public void run() {
            Log.d(TAG, "BEGIN ConnectedThread");
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            int readStatus;
            mLock.lock();
            Log.d(TAG, "LOCK ConnectedThread");
            try {
                while(!interrupted()) {
                    try {
                        int byteLength = mInputStream.available();
                        if (byteLength <= 0) {
                            if (!hasStartedTimeoutTask) {
                                mHandler.postDelayed(timerOutCancelTask, 3000);
                                hasStartedTimeoutTask = true;
                            }
                        } else {
                            if (hasStartedTimeoutTask) {
                                mHandler.removeCallbacks(timerOutCancelTask);
                                hasStartedTimeoutTask = false;
                            }
                            //Log.d(TAG, "AVAIL: " + byteLength);
                            byte[] buffer = new byte[byteLength];
                            readStatus = mInputStream.read(buffer, 0, byteLength);
                            //byteBuffer.put(buffer);
                            mHandler.obtainMessage(MyEvent.STATE_INTERNET_THREAD_MSG, readStatus, byteLength, buffer).sendToTarget();
                        }

                    } catch (IOException e) {
                        Log.d(TAG, "disconnected", e);
                        notConnected.signal();
                        mBluetoothComm.get().setState(CommState.DISCONNECTED);
                        break;
                    }
                }
            } finally {
                mLock.unlock();
            }
        }
        public void write(byte[] buffer) {
            try {
                mOutputStream.write(buffer);
            } catch (IOException e) {
                Log.d(TAG, "write() socket stream fail", e);
                mBluetoothComm.get().setState(CommState.DISCONNECTED);
            }
        }
        public void cancel() {
            mLock.lock();
            try {
                mSocket.close();
                mBluetoothComm.get().setState(CommState.DISCONNECTED);
                notConnected.signal();
            } catch (IOException e) {
                Log.d(TAG, "close() connection socket fail", e);
            } finally {
                mLock.unlock();
            }
        }
        private Runnable timerOutCancelTask = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "TIMEOUT connectedThread");
                interrupt();
                cancel();
            }
        };
    }

}
