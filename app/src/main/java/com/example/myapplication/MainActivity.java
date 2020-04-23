package com.example.myapplication;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;

public class MainActivity extends AppCompatActivity {
    String EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE";
    long SCAN_PERIOD = 6000;

    BluetoothManager manager;
    BluetoothAdapter adapter;
    BluetoothLeScanner scanner;
    int count=0;
    Button startScan;
    Button stopScan;
    Button advertising;
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
        advertising=findViewById(R.id.advertising);

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
                buttonListeners();
            }
        }
    }

    public void buttonListeners(){
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
                    count=0;
                    Toast.makeText(MainActivity.this,"start scanning",Toast.LENGTH_SHORT).show();

                    ScanSettings.Builder scanSettings = new ScanSettings.Builder();
                    scanSettings.setScanMode(SCAN_MODE_LOW_LATENCY);
                    scanner.startScan(null, scanSettings.build(), scanCallback);
                }
            }
        });

        stopScan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //when the stop button is clicked, stop scanning for BLE devices
                scanner.stopScan(scanCallback);
            }
        });

        advertising.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent result) {
        if (requestCode == ENABLE_BLUETOOTH) {
            if (resultCode == RESULT_OK) { //bluetooth is turned on, continuing
                buttonListeners();
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
                    buttonListeners();
                }
            } else {
                //the permission is not granted, the app cannot be used
                Toast.makeText(this,"this app needs a location permission",Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    SortedSet<ScanResult> scannedDevices=new TreeSet<>(); //stores all the scanned devices
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            //when a BLE device is scanned, add it to the hashset
            scannedDevices.add(result);
            count+=1;
            countField.setText(count);
            printScanResult(result);
        }

        public void onBatchScanResults(List<ScanResult> results) {
            addressField.append("Received " + results.size() + " batch results:\n");
            for (ScanResult r : results) {
                printScanResult(r);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            switch (errorCode) {
                case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                    nameField.append("Scan failed: already started.\n");
                    break;
                case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    nameField.append("Scan failed: app registration failed.\n");
                    break;
                case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                    nameField.append("Scan failed: feature unsupported.\n");
                    break;
                case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                    nameField.append("Scan failed: internal error.\n");
                    break;
            }
        }

        private void printScanResult(ScanResult result) {
            Iterator it=scannedDevices.iterator();
            while(it.hasNext()){
                BluetoothDevice i= (BluetoothDevice) it.next();
                String id =i.getAddress();
                nameField.append(" from " + id+ ".\n");
            }
        }
    };

    public boolean checkAdvertising(){
        return adapter.isMultipleAdvertisementSupported() && adapter.isOffloadedFilteringSupported() && adapter.isOffloadedScanBatchingSupported();
    }

    public void sendMessage() {

        /*if (adapter == null)
            editText.setText("bluetooth pas ok");
        else if (! adapter.isEnabled())
            editText.setText("bluetooth pas activé");
        else {
            editText.setText("bluetooth activé");*/
            BluetoothLeAdvertiser advertiser = adapter.getBluetoothLeAdvertiser();

            AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder().setAdvertiseMode( AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY ).setTxPowerLevel( AdvertiseSettings.ADVERTISE_TX_POWER_HIGH ).setTimeout(0).build();
            Log.i("BLE","start of advertise data after settings");

            UUID SERVICE_UUID = UUID.fromString("0000fe02-0000-1000-8000-00805F9B34FB");
            if (advertiser == null)
                addressField.setText("advertiser pas trouvé");
            else
                addressField.setText("advertiser trouvé");

            AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName( true )
                .setIncludeTxPowerLevel(true)
                .addServiceData(new ParcelUuid(SERVICE_UUID), "D".getBytes() )
                .build();

            Log.d("ERROR", addressField.getText().toString());
            if(!checkAdvertising()){
                Toast.makeText(this,"ERROR ADVERTISING NOT SUPPORTED", Toast.LENGTH_SHORT).show();
            }
            advertiser.startAdvertising(advertiseSettings, advertiseData, advertisingCallback);
        }


    private AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartFailure(int errorCode) {
            Log.d("ERROR", String.format("Advertisement failure (code %d)", errorCode));
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d("ERROR", "Advertisement started");

        }
    };
}