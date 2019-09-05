package org.cardioart.streamgraph.impl;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.cardioart.streamgraph.api.MyEvent;
import org.cardioart.streamgraph.api.PacketReaderThread;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by jirawat on 09/09/2014.
 */
public class Protocol2ReaderThreadImpl extends PacketReaderThread {

    private static final int STATE_START = 0;
    private static final int STATE_HEADER_0 = 1;
    private static final int STATE_HEADER_1 = 2;
    private static final int STATE_HEADER_2 = 3;
    private static final int STATE_STATUS_0 = 4;
    private static final int STATE_STATUS_1 = 5;
    private static final int STATE_STATUS_2 = 6;
    private static final int STATE_CH1_0 = 7;
    private static final int STATE_CH1_1 = 8;
    private static final int STATE_CH1_2 = 9;
    private static final int STATE_END = 31;
    private int state = 0; //Current Position
    private int channel = 0;
    private int channel_offset = 0;
    private Integer byteTemp = 0;

    private static final String TAG = "streamgraph";
    private final int LIMIT = 30;
    private final int MAX_CHANNEL = 10;
    private final Handler mainHandler;
    private static final Lock mLock = new ReentrantLock();
    private static final Object mChannelLock = new Object();
    private boolean canChannelRead = false;
    private static final Condition notEmpty = mLock.newCondition();
    private static final Condition notFull = mLock.newCondition();

    private BlockingQueue<byte[]> blockingQueue = new LinkedBlockingDeque<byte[]>(LIMIT);
    private ArrayList<ArrayList<Integer>> arrayChannel = new ArrayList<ArrayList<Integer>>(MAX_CHANNEL);
    private long totalByteRead = 0L;

    public Protocol2ReaderThreadImpl(Handler mHandler) {
        Log.d(TAG, "BEGIN PacketReaderThread");
        mainHandler = mHandler;
        for (int i=0; i < MAX_CHANNEL; i++) {
            arrayChannel.add(new ArrayList<Integer>());
        }
        state = STATE_START;
        channel = 0;
        channel_offset = 16;
        byteTemp = 0;
    }
    @Override
    public void run() {
        Looper.prepare();
        mLock.lock();
        try {
            mainHandler.obtainMessage(MyEvent.STATE_PACKETREADER_THREAD_START).sendToTarget();
            while (!isInterrupted()) {
                while (blockingQueue.size() == 0) {
                    notEmpty.await();
                }
                readByte(blockingQueue.take());
                notFull.signal();
            }
        } catch (Exception e) {
            Log.d(TAG, "EXP: " + e.getLocalizedMessage());
        } finally {
            Log.d(TAG, "END PacketReaderThread");
            mLock.unlock();
            mainHandler.obtainMessage(MyEvent.STATE_PACKETREADER_THREAD_STOP).sendToTarget();
        }
    }

    private void readByte(byte[] data) {
        int length = data.length;
        totalByteRead += length;
        //Parse data 3byte (8group) length
        for (int i = 0; i < length; i++) {
            //Check Header Protocol
            if (state == STATE_END && data[i] != 0x04) {continue;}
            else if (state == STATE_START && data[i] != 0x01) {continue;}
            else if (state == STATE_HEADER_0 && data[i] != 0x01) {continue;}
            else if (state == STATE_HEADER_2 && data[i] != 0x01) {continue;}

            //Skip status byte (3byte)
            if (state == STATE_END) {
                state = STATE_START;
                continue;
            } else if (state == STATE_STATUS_2) {
                    channel_offset = 16;
                    byteTemp = 0;
                    channel = 0;
            } else if (state > STATE_STATUS_2) {
                if (channel_offset == 16) {
                    byteTemp = (data[i] & 0xFF) << 16;
                    channel_offset = 8;
                } else if (channel_offset == 8) {
                    byteTemp += (data[i] & 0xFF) << 8;
                    channel_offset = 0;
                } else if (channel_offset == 0) {
                    byteTemp += (data[i] & 0xFF);
                    if (byteTemp < 0)
                    {
                        Log.d(TAG, String.format("byteTemp: %d (%d,%d,%d)",
                                byteTemp, data[i], data[i-1], data[i-2]));
                    }
                    synchronized (mChannelLock) {
                        if (channel >= 0 && channel < MAX_CHANNEL) {
                            arrayChannel.get(channel).add(byteTemp);
                        }
                    }
                    channel++;
                    channel_offset = 16;
                }
            }
            state++;
        }

        //*/
    }
    public Integer[] getChannel(int index) {
        Integer[] result;
        if (index >= 0 && index < MAX_CHANNEL) {
            synchronized (mChannelLock) {
                int length = arrayChannel.get(index).size();
                result = arrayChannel.get(index).toArray(new Integer[length]);
                arrayChannel.get(index).clear();
            }
        } else {
            result = new Integer[0];
        }
        return result;
    }
    public void readPacket(byte[] data) {
        mLock.lock();
        try {
            while (blockingQueue.remainingCapacity() == 0) {
                notFull.await();
            }
            blockingQueue.put(data);
            notEmpty.signal();
        } catch (Exception e) {
            Log.d(TAG, "EXP: " + e.getLocalizedMessage());
        } finally {
            mLock.unlock();
        }
    }
    public long getTotalByteRead() {
        long buffer = totalByteRead;
        synchronized (this) {
            totalByteRead = 0;
        }
        return buffer;
    }
}
