package com.example.myapplication;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.HashSet;

public class MainActivity extends AppCompatActivity {
    BluetoothManager manager;
    BluetoothAdapter adapter;
    BluetoothLeScanner scanner;
    int count=0;
    Button startScan;
    Button stopScan;
    TextView countField;
    TextView addressField;
    TextView nameField;
    private final static int ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_LOCATION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        countField=findViewById(R.id.count);
        nameField=findViewById(R.id.name);
        addressField=findViewById(R.id.address);
        startScan = findViewById(R.id.startScanning);
        stopScan = findViewById(R.id.stopScanning);

        manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //checking if the phone can use BLE; if not stops the app
            Toast.makeText(this.getApplicationContext(), "Bluetooth low energy is not supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        boolean permission=ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!permission) { //permission for location is not granted
            //requests the permission to the user
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }
        else { //permission is already granted, continuing
            //asks for bluetooth activation if it's turned off
            if (adapter == null || !adapter.isEnabled()) { //bluetooth is turned off, asks for activation
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH);
            }
            else { //bluetooth is turned on, continuing
                resume();
            }
        }
    }

    public void resume(){
        startScan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { //when the start button is clicked, start scanning for BLE devices
                //check if location services are turned on
                LocationManager locationManager = (LocationManager) MainActivity.this.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
                boolean gps, network;
                gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                if (!(gps&&network)) {
                    //location is not turned on
                    Toast.makeText(MainActivity.this.getApplicationContext(), "this app needs location services on to work", Toast.LENGTH_SHORT).show();
                } else {
                    //location is turned on, the app can be used
                    scanner = adapter.getBluetoothLeScanner();
                    startScan();
                }
            }
        });

        stopScan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //when the stop button is clicked, stop scanning for BLE devices
                stopScan();
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent result) {
        if (requestCode == ENABLE_BLUETOOTH) {
            if (resultCode == RESULT_OK) { //bluetooth is turned on, continuing
                resume();
            } else { //asks again for permission
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        //callback for when the location permission is asked to the user
        if(requestCode == REQUEST_LOCATION) { //checks the requestCode for the relevant permission
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (adapter == null || !adapter.isEnabled()) { //bluetooth is turned off, asks for activation
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH);
                }
                else { //bluetooth is turned on, continuing
                    resume();
                }
            } else {
                //the permission is not granted, the app cannot be used
                Toast.makeText(this,"this app needs a location permission",Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    public void startScan() {
        count=0;
        Toast.makeText(this,"start scanning",Toast.LENGTH_SHORT).show();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                scanner.startScan(scanCallback);
            }
        });
    }

    public void stopScan() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                scanner.stopScan(scanCallback);
            }
        });
        for (BluetoothDevice s : scannedDevices) {
            Toast.makeText(MainActivity.this,"device "+s+" found",Toast.LENGTH_SHORT).show();
            nameField.setText(s.getName());
            addressField.setText(s.getAddress());
        }

        countField.setText(String.valueOf(scannedDevices.size()));
    }

    HashSet<BluetoothDevice> scannedDevices=new HashSet<>(); //stores all the scanned devices
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            //when a BLE device is scanned, add it to the hashset
            scannedDevices.add(result.getDevice());
            count+=1;
        }
    };
}