package org.cardioart.streamgraph.api;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.cardioart.streamgraph.MainActivity;

import java.util.HashMap;
import java.util.Set;

/**
 * Created by jirawat on 10/05/2014.
 */
public class BluetoothScanHelper {
    public static final int REQUEST_ENABLE_BT = 2718;

    private BluetoothAdapter mBluetoothAdapter;
    private HashMap<String, String> foundDevices = new HashMap<String, String>();
    private Context context;
    private ArrayAdapter<String> PairedAdapter;
    private ArrayAdapter<String> DetectedAdapter;
    private TextView textViewStatus;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (!foundDevices.containsKey(btDevice.getName())) {
                    if (btDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
                        DetectedAdapter.add(btDevice.getName());
                        foundDevices.put(btDevice.getName(), btDevice.getAddress());
                        DetectedAdapter.notifyDataSetChanged();
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {

                mBluetoothAdapter.cancelDiscovery();
                if (foundDevices.isEmpty()) {
                    // No Device
                    Toast.makeText(context, "No Bluetooth Devices", Toast.LENGTH_LONG).show();
                    Log.d("BT", "No BT Devices");
                }
                textViewStatus.setText("Search");
                textViewStatus.setTextColor(Color.parseColor("#FFFFFFFF"));
                textViewStatus.setEnabled(true);
            }
        }
    };

    public BluetoothScanHelper(Context mcontext) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.context = mcontext;
        if (mBluetoothAdapter == null) {
            Log.d("Bluetooth", "Device didn't support bluetooth");
            System.exit(2);
        }
    }
    public void enableBluetooth() {
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            MainActivity activity = (MainActivity) this.context;
            activity.startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
        }
    }
    public void searchDevice() {
        mBluetoothAdapter.cancelDiscovery();
        //DetectedAdapter.clear();
        PairedAdapter.clear();
        foundDevices.clear();
        textViewStatus.setText("Searching...");
        textViewStatus.setEnabled(false);
        textViewStatus.setTextColor(Color.parseColor("#FF333333"));
        searchPairedDevice();
        textViewStatus.setText("Search");
        textViewStatus.setTextColor(Color.parseColor("#FFFFFFFF"));
        textViewStatus.setEnabled(true);
        //mBluetoothAdapter.startDiscovery();
    }
    public void searchPairedDevice() {
        Set<BluetoothDevice> pairedDevice = mBluetoothAdapter.getBondedDevices();
        if (!pairedDevice.isEmpty()) {
            for (BluetoothDevice device : pairedDevice) {
                foundDevices.put(device.getName(), device.getAddress());
                PairedAdapter.add(device.getName());
            }
            PairedAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(context, "No Paired BT", Toast.LENGTH_SHORT).show();
        }
        Log.d("BT", "End search pair");
    }
    public BroadcastReceiver getReceiver() {
        return mReceiver;
    }
    public void setTextViewStatus(TextView textViewStatus) {
        this.textViewStatus = textViewStatus;
    }
    public void setPairedAdapter(ArrayAdapter<String> adapter) {
        this.PairedAdapter = adapter;
    }
    public void setDetectedAdapter(ArrayAdapter<String> adapter) {
        this.DetectedAdapter = adapter;
    }
    public String getDeviceAddressFromName(String name) {
        if(foundDevices.containsKey(name)) {
            return foundDevices.get(name);
        }
        return null;
    }
    public BluetoothDevice getDeviceFromAddress(String address) {
        try {
            return mBluetoothAdapter.getRemoteDevice(address);
        } catch (Exception e) {
            return null;
        }
    }
    public BluetoothDevice getDeviceFromName(String name) {
        String address = getDeviceAddressFromName(name);
        return getDeviceFromAddress(address);
    }
}
