package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
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

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //checking if the phone can use BLE; if not stops the app
            Toast.makeText(this, "Bluetooth low energy is not supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (!hasLocationPermissions()) { //permission for location is not granted
            //requests the permission to the user
            requestLocationPermissions(this, REQUEST_LOCATION);
        }
        else{
            //permission for location is already granted
            if(!areLocationServicesEnabled(this)){
                //location is not turned on
                Toast.makeText(this,"this app needs location services on to work",Toast.LENGTH_SHORT).show();
            }
            else {
                //location is turned on, the app can be used
                manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                adapter = manager.getAdapter();

                //asks for bluetooth activation if it's turned off
                if (adapter == null || !adapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH);
                }
            }
        }

        startScan = findViewById(R.id.startScanning);
        startScan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //when the start button is clicked, start scanning for BLE devices
                scanner = adapter.getBluetoothLeScanner();
                startScan();
            }
        });

        stopScan = findViewById(R.id.stopScanning);
        stopScan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //when the stop button is clicked, stop scanning for BLE devices
                stopScan();
            }
        });

        countField=findViewById(R.id.count);
        nameField=findViewById(R.id.name);
        addressField=findViewById(R.id.address);

    }

    public boolean hasLocationPermissions() { //checks if the location permission is granted
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestLocationPermissions(final Activity activity, int requestCode) { //asks for location permission
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, requestCode);
    }

    public boolean areLocationServicesEnabled(Context context) { //checks if the location is turned on
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gps=false, network=false;
        try {
            gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch(Exception e) {
            e.getStackTrace();
        }

        try {
            network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch(Exception e) {
            e.getStackTrace();
        }

        return gps&&network;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        //callback for when the location permission is asked to the user
        if(requestCode == REQUEST_LOCATION) { //checks the requestCode for the relevant permission
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //the permission is granted
                if(areLocationServicesEnabled(this)) {
                    //the location is turned on, the app can be used
                    manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
                    adapter = manager.getAdapter();
                    if (adapter == null || !adapter.isEnabled()) {
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH);
                    }
                }
                else{
                    //location is turned off, the app cannot be used
                    Toast.makeText(this, "this app needs a location access",Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                //the permission is not granted, the app cannot be used
                Toast.makeText(this,"this app needs a location permission",Toast.LENGTH_SHORT).show();
                finish();
            }
        }
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
}