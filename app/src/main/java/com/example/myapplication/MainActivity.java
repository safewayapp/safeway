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
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;

public class MainActivity extends AppCompatActivity {
    long WAIT_PERIOD = 25*60*1000; //25 minutes
    BluetoothManager manager;
    BluetoothAdapter adapter;
    BluetoothLeScanner scanner;
    BluetoothLeAdvertiser advertiser;
    int count=0;
    List<ScanFilter> filters;
    ScanSettings.Builder scanSettings;
    CheckBox positif;
    HashMap<BluetoothDevice,Long> scannedDevices = new HashMap<>();
    Button advertising;
    TextView countField;
    TextView nameField;
    Handler handler;
    boolean enabled=true;
    private final static int ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_LOCATION = 2;
    private static UUID SERVICE_UUID = UUID.fromString("0000ff02-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handler=new Handler();

        positif=findViewById(R.id.contagion);
        countField=findViewById(R.id.count);
        nameField=findViewById(R.id.name);
        advertising=findViewById(R.id.advertising);

        manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) { //checking if the phone can use BLE; if not stops the app
            Toast.makeText(this.getApplicationContext(), "Bluetooth low energy is not supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (adapter == null || !adapter.isEnabled()) { //checking if bluetooth is turned off, then asks for activation
            enabled=false;
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH);
        }
        if (Build.VERSION.SDK_INT >= 23) { //if the phone is version Marshmallow or higher it needs location permissions and runtime checks
            enabled=false;
            checkLocation();
        }
    }

    @RequiresApi(23)
    public void checkLocation(){
        boolean permission=ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!permission) { //permission for location is not granted
            //requests the permission to the user
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }
        else { //permission is already granted, checking if location is turned on
            LocationManager locationManager = (LocationManager) MainActivity.this.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            boolean gps, network;
            gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            enabled=gps&&network;
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent result) {
        if (requestCode == ENABLE_BLUETOOTH) {
            if (resultCode == RESULT_OK) { //bluetooth is turned on, now checking for location if the phone requires it
                if (Build.VERSION.SDK_INT >= 23) { //if the phone is version Marshmallow or higher it needs location permissions and runtime checks
                    checkLocation();
                }
                enabled=true;
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
                LocationManager locationManager = (LocationManager) MainActivity.this.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
                boolean gps, network;
                gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                enabled=gps&&network;
            } else {
                //the permission is not granted, the app cannot be used
                Toast.makeText(this,"this app needs a location permission",Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPref = getSharedPreferences("SharedPref",MODE_PRIVATE);
        count = sharedPref.getInt("count", 0);
        countField.setText(String.valueOf(count));
        scanner = adapter.getBluetoothLeScanner();
        if(enabled) {
            scanner = adapter.getBluetoothLeScanner();
            Toast.makeText(MainActivity.this, "start scanning", Toast.LENGTH_SHORT).show();

            filters = new ArrayList<>();
            //only scanning devices with the right service UUID
            ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build();
            filters.add(filter);
            scanSettings = new ScanSettings.Builder();
            scanSettings.setScanMode(SCAN_MODE_LOW_LATENCY);
            loop.run();
        }
        else{
            Toast.makeText(MainActivity.this.getApplicationContext(), "this app needs bluetooth and location services on to work", Toast.LENGTH_SHORT).show();
        }
    }
//lance immédiatemment, continue de scanner, et WAIT_PERIOD plus tard relance le loop
    Runnable loop = new Runnable() {
        @Override
        public void run() {
            try {
                scanner.stopScan(scanCallback);
                scanner.startScan(filters,scanSettings.build(),scanCallback);
            } finally {
                handler.postDelayed(loop, WAIT_PERIOD);
            }
        }
    };

    @Override
    protected void onPause() {
        //stop scan but not advertising
        //doesn't work well, keeps counting when screen is turned off and when app is in background
        handler.removeCallbacks(loop);
        SharedPreferences sharedPreferences = getSharedPreferences("SharedPref",MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("count", count);
        editor.apply();
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(advertiser!=null) {
            advertiser.stopAdvertising(advertisingCallback);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) { //when a BLE device is scanned, add it to the list
            double dist,dist2=0;
            BluetoothDevice device=result.getDevice();
            int estimation=-77; //tx power approximation for devices under API 26, based on a conversion between TX_POWER_HIGH (defined in advertising settings) and a value in dbm
            float environment=2;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                dist=Math.pow(10d,(result.getTxPower()-result.getRssi()) /(10 * environment));
            }
            else{
                dist=Math.pow(10d,(estimation-result.getRssi()) /(10 * environment));
            }
            if(scannedDevices.containsKey(device)) {
                Log.d("TIME SINCE LAST", String.valueOf(System.currentTimeMillis() - scannedDevices.get(device)));
            }
            if (dist<=2){
                if(!scannedDevices.containsKey(device)||System.currentTimeMillis()-scannedDevices.get(device)>7000){
                    scannedDevices.put(device,System.currentTimeMillis());
                    nameField.append("name: "+device.getName()+" ,address: "+device.getAddress()+"\n");
                    count += 1;
                    countField.setText(Integer.toString(count));
                    byte[] a = result.getScanRecord().getServiceData(new ParcelUuid(SERVICE_UUID));
                    if(a!=null && a.length>0){
                        int g= a[0];
                        if (g == 1) {
                            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                            } else { //deprecated in API 26
                                vibrator.vibrate(1000);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            switch (errorCode) {
                case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                    nameField.setText("Scan failed: already started.\n");
                    break;
                case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                    nameField.setText("Scan failed: feature unsupported.\n");
                    break;
                case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                    nameField.setText("Scan failed: internal error.\n");
                    break;
            }
        }
    };

    public void sendMessage(View view) {
        if(!adapter.isMultipleAdvertisementSupported()){
            Toast.makeText(this,"ERROR ADVERTISING NOT SUPPORTED", Toast.LENGTH_SHORT).show();
        }
        else {
            advertiser = adapter.getBluetoothLeAdvertiser();

            byte[] data= new byte[]{(byte) (positif.isChecked()?1:0)};

            AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).setTimeout(0).build();
            AdvertiseData advertiseData = new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(SERVICE_UUID)).addServiceData(new ParcelUuid(SERVICE_UUID), data).build();

            if(advertiser!=null) {
                advertiser.stopAdvertising(advertisingCallback);
                advertiser.startAdvertising(advertiseSettings, advertiseData, advertisingCallback);
            }
        }
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