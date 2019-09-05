package org.cardioart.streamgraph;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import org.cardioart.streamgraph.api.BluetoothScanHelper;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "streamgraph";
    public BluetoothScanHelper bluetoothScanHelper;
    private ArrayAdapter<String> adapterPaired;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothScanHelper = new BluetoothScanHelper(this);
        bluetoothScanHelper.setTextViewStatus((Button) findViewById(R.id.buttonSearch));
        bluetoothScanHelper.enableBluetooth();

        ListView listViewPaired = findViewById(R.id.listViewPaired);
        Button buttonSearch = findViewById(R.id.buttonSearch);

        adapterPaired = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        listViewPaired.setAdapter(adapterPaired);
        bluetoothScanHelper.setPairedAdapter(adapterPaired);

        listViewPaired.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String device_name = (String) adapterView.getItemAtPosition(i);
                String device_address = bluetoothScanHelper.getDeviceAddressFromName(device_name);
                Toast.makeText(
                        getApplicationContext(),
                        bluetoothScanHelper.getDeviceAddressFromName(device_name) + " select",
                        Toast.LENGTH_SHORT
                ).show();
                Intent graphIntent = new Intent(getApplicationContext(), GraphActivity.class);
                graphIntent.putExtra("device_name", device_name);
                graphIntent.putExtra("device_address", device_address);
                startActivity(graphIntent);
            }
        });

        buttonSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothScanHelper.searchDevice();
            }
        });

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(bluetoothScanHelper.getReceiver(), filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(bluetoothScanHelper.getReceiver(), filter);

        bluetoothScanHelper.searchDevice();
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
    protected void onDestroy() {
        this.unregisterReceiver(bluetoothScanHelper.getReceiver());
        super.onDestroy();
    }
}
