package org.cardioart.streamgraph;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;

import org.cardioart.streamgraph.api.BluetoothConnection;
import org.cardioart.streamgraph.api.BluetoothScanHelper;
import org.cardioart.streamgraph.api.MyEvent;
import org.cardioart.streamgraph.api.PacketReaderThread;
import org.cardioart.streamgraph.impl.BluetoothCommClient;
import org.cardioart.streamgraph.impl.Protocol2ReaderThreadImpl;

import java.util.ArrayList;

public class GraphActivity extends AppCompatActivity implements Handler.Callback, AdapterView.OnItemSelectedListener {

    private static final int MAX_CHANNEL = 8;
    private static final int MAX_SAMPLE_LENGTH = 300;
    private static final String TAG = "streamgraph";
    private static String deviceName;
    private static String deviceAddress;

    private Handler mainHandler;
    private BluetoothConnection commHelper;
    private PacketReaderThread packetReader;
    private Runnable mTimerMonitorSpeed;
    private Runnable mTimerGraphPlot;
    private TextView textViewRxSpeed;
    private TextView textViewRxPacket;

    private static final int GRAPHVIEW_SERIE_HISTORY_SIZE = 1000;
    private GraphView graphView;
    private GraphViewSeries series1;
    private double data1Time = 0d;
    private ArrayList<GraphView.GraphViewData> data1 = new ArrayList<>();

    private boolean isBluetoothActive = false;
    private boolean isPacketReaderActive = false;
    private int currentChannelId = 0;

    public static final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        //Get ExtraParameter from DeviceSelectionActivity
        deviceName = getIntent().getStringExtra("device_name");
        deviceAddress = getIntent().getStringExtra("device_address");
        TextView textDevice = findViewById(R.id.textDevice);
        textDevice.setText("Device: " + deviceName + " (" + deviceAddress + ")");

        //Initialize private variables
        mainHandler = new Handler(this);
        commHelper = new BluetoothCommClient(mainHandler);
        textViewRxSpeed = findViewById(R.id.textViewRxSpeed);
        textViewRxPacket= findViewById(R.id.textViewRxPacket);

        //Enable bluetooth
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetoothIntent, MyEvent.REQUEST_ENABLE_BT);
        }

        //Change orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        //Generate Series data
        for (; data1Time < GRAPHVIEW_SERIE_HISTORY_SIZE; data1Time++) {
            data1.add(new GraphView.GraphViewData(data1Time, 0));
        }

        //Graph configuration
        series1 = new GraphViewSeries(data1.toArray(new GraphView.GraphViewData[data1.size()]));
        graphView = new LineGraphView(this, "");
        graphView.setShowLegend(false);
        series1.getStyle().color = Color.BLUE;
        graphView.addSeries(series1);
        graphView.setHorizontalLabels(new String[] {});
        graphView.getGraphViewStyle().setTextSize(16);
        LinearLayout layout = findViewById(R.id.graph1);
        layout.addView(graphView);

        //Dropdown (Spinner) Configuration
        Spinner spinner = findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(this);
        //Start bluetooth thread
        if (mBluetoothAdapter.isEnabled()) {
            commHelper.startConnection(mBluetoothAdapter.getRemoteDevice(deviceAddress));
            //Start PacketReader thread
            packetReader = new Protocol2ReaderThreadImpl(mainHandler);
            packetReader.start();
        }
    }

    @Override
    public boolean handleMessage(Message message) {
        switch (message.what) {
            case MyEvent.STATE_DEBUG_RX:
                if (message.obj != null) {
                    debugRxSpeed((Long) message.obj);
                }
                break;
            case MyEvent.STATE_DEBUG_PACKET:
                if (message.obj != null) {
                    debugPacketReader((Long)message.obj);
                }
                break;
            case MyEvent.STATE_BT_RX_UP:
                isBluetoothActive = true;
                Toast.makeText(this, "connection start", Toast.LENGTH_SHORT).show();
                break;
            case MyEvent.STATE_BT_RX_DOWN:
                isBluetoothActive = false;
                Toast.makeText(this, "connection stop", Toast.LENGTH_SHORT).show();
                break;
            case MyEvent.STATE_PACKETREADER_THREAD_MSG:
            case MyEvent.STATE_INTERNET_THREAD_MSG:
                packetReader.readPacket((byte[]) message.obj);
                //Log.d(TAG, MyStringConvert.bytesToHex((byte[])message.obj));
                break;
            case MyEvent.STATE_PACKETREADER_THREAD_START:
                isPacketReaderActive = true;
                break;
            case MyEvent.STATE_PACKETREADER_THREAD_STOP:
                isPacketReaderActive = false;
            default:
                break;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BluetoothScanHelper.REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_CANCELED) {
                System.exit(2);
            }
        }
    }
    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (adapterView != null) {
            currentChannelId = i;
        }
    }
    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    @Override
    protected void onResume() {

        mTimerMonitorSpeed = new Runnable() {
            @Override
            public void run() {
                if (isBluetoothActive) {
                    mainHandler.obtainMessage(MyEvent.STATE_DEBUG_RX, commHelper.getRxSpeed()).sendToTarget();
                } else {
                    mainHandler.obtainMessage(MyEvent.STATE_DEBUG_RX, 0L).sendToTarget();
                }
                if (isPacketReaderActive) {
                    mainHandler.obtainMessage(MyEvent.STATE_DEBUG_PACKET, packetReader.getTotalByteRead()).sendToTarget();
                } else {
                    mainHandler.obtainMessage(MyEvent.STATE_DEBUG_PACKET, 27L).sendToTarget();
                }
                mainHandler.postDelayed(this, 1000);
            }
        };
        mTimerGraphPlot = new Runnable() {
            @Override
            public void run() {
                if (isPacketReaderActive) {
                    Integer[] newData = packetReader.getChannel(currentChannelId);
                    for (int i=0, length = newData.length; i < length; i++) {
                        data1Time += 1;
                        data1.remove(0);
                        data1.add(new GraphView.GraphViewData(data1Time, (double)newData[i].longValue()));
                    }
                    series1.resetData(data1.toArray(new GraphView.GraphViewData[data1.size()]));

                }
                mainHandler.postDelayed(this, 100);
            }
        };
        mainHandler.postDelayed(mTimerMonitorSpeed, 1000);
        mainHandler.postDelayed(mTimerGraphPlot, 100);

        super.onResume();
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(mTimerMonitorSpeed);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        commHelper.stop();
        super.onDestroy();
    }

    private void debugRxSpeed (long totalByte) {
        if (textViewRxSpeed != null) {

            double BPS = totalByte / 1024.0;
            if (totalByte < 1024) {
                textViewRxSpeed.setText(String.format("%d Bps", totalByte));
            } else if (BPS > 0 && BPS < 1024) {
                textViewRxSpeed.setText(String.format("%.2f KBps", BPS));
            } else {
                BPS = BPS / 1024;
                if (BPS > 0 && BPS < 1024) {
                    textViewRxSpeed.setText(String.format("%.2f MBps", BPS));
                } else {
                    BPS = BPS / 1024;
                    textViewRxSpeed.setText(String.format("%.2f GBps", BPS));
                }
            }
        }
    }

    private void debugPacketReader(long totalByte) {
        if (textViewRxPacket != null) {
            double BPS = totalByte / 1024.0;
            if (totalByte < 1024) {
                textViewRxPacket.setText(String.format("%d Bps", totalByte));
            } else if (BPS > 0 && BPS < 1024) {
                textViewRxPacket.setText(String.format("%.2f KBps", BPS));
            } else {
                BPS = BPS / 1024;
                if (BPS > 0 && BPS < 1024) {
                    textViewRxPacket.setText(String.format("%.2f MBps", BPS));
                } else {
                    BPS = BPS / 1024;
                    textViewRxPacket.setText(String.format("%.2f GBps", BPS));
                }
            }
        }
    }
}
