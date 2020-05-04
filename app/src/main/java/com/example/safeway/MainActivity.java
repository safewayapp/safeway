package com.example.safeway;

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
import android.bluetooth.le.ScanRecord;
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

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;

public class MainActivity extends AppCompatActivity {
    long WAIT_PERIOD = 25*60*1000; //25 minutes, scanning time
    long DELAY=7*1000;//7 seconds, wait time for devices that have already been scanned

    static int maxdistance=25; //distance for scan
    BluetoothManager manager;
    BluetoothAdapter adapter;
    BluetoothLeScanner scanner;
    BluetoothLeAdvertiser advertiser;
    ScanSettings.Builder scanSettings;

    HashMap<BluetoothDevice,Long> scannedDevices = new HashMap<>(); //map of scanned devices and the time at which they were last seen
    int count=0; //number of encountered devices

    CheckBox positif;
    Button advertising;
    TextView countField;
    TextView nameField;
    TextView advField;
    TextView rcvField;
    Handler handler;

    Vibrator vibrator;

    boolean enabled=true; //bluetooth/location enabled

    private final static int ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_LOCATION = 2;
    private static final int REQUEST_BACKGROUND_LOCATION = 3;

    public static final UUID SERVICE_UUID = UUID.fromString("0000483e-0000-1000-8000-00805f9b34fb"); //UUID for advertising

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler=new Handler();
        vibrator= (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        positif=findViewById(R.id.contagion);
        countField=findViewById(R.id.count);
        nameField=findViewById(R.id.name);
        advertising=findViewById(R.id.advertising);
        advField=findViewById(R.id.adv_text);
        rcvField=findViewById(R.id.receiv_text);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) { //checking if the phone can use BLE; if not stops the app
            Toast.makeText(this.getApplicationContext(), "Bluetooth low energy is not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            System.out.println("ERROR BLUETOOTH NOT SUPPORTED");
            finish();
        }
        adapter = manager.getAdapter();

        if (adapter == null || !adapter.isEnabled()) { //checking if bluetooth is turned off, then asks for activation
            enabled=false;
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //if the phone is version Marshmallow or higher it needs location permissions and runtime checks
            enabled=false;
            checkLocation();
        }
    }

    @RequiresApi(23)
    public void checkLocation(){
        boolean permission=ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!permission) { //permission for location is not granted
            //requests the permission to the user
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        }
        else { //permission is already granted, checking if location is turned on
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){ //if phone is android 10 or higher, also checks for background permission
                checkBackgroundLocation();
            }
            else {
                boolean gps, network;
                LocationManager locationManager = (LocationManager) MainActivity.this.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
                if (locationManager == null) {
                    Toast.makeText(this, "Error with location manager", Toast.LENGTH_SHORT).show();
                    return;
                }
                gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                enabled = gps && network;
            }
        }
    }

    @RequiresApi(29)
    public void checkBackgroundLocation(){
        boolean permission=ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!permission) { //permission for background location is not granted
            //requests the permission to the user
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_BACKGROUND_LOCATION);
        }
        else { //permission is already granted, checking if location is turned on
            boolean gps, network;
            LocationManager locationManager = (LocationManager) MainActivity.this.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            if(locationManager==null){
                Toast.makeText(this,"Error with location manager",Toast.LENGTH_SHORT).show();
                return;
            }
            gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            enabled=gps&&network;
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);
        if (requestCode == ENABLE_BLUETOOTH) {
            if (resultCode == RESULT_OK) { //bluetooth is turned on, now checking for location if the phone requires it
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //if the phone is version Marshmallow or higher it needs location permissions and runtime checks
                    checkLocation();
                }
                else {
                    enabled = true;
                }
            } else { //asks again for permission
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //callback for when the location permission is asked to the user
        if(requestCode == REQUEST_LOCATION) { //checks the requestCode for the relevant permission
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
                    checkBackgroundLocation();
                }
                else {
                    LocationManager locationManager = (LocationManager) MainActivity.this.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
                    boolean gps, network;
                    if (locationManager == null) {
                        Toast.makeText(this, "Error with location manager", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                    network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                    enabled = gps && network;
                }
            } else {
                //the permission is not granted, the app cannot be used
                Toast.makeText(this,"this app needs a location permission",Toast.LENGTH_SHORT).show();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    checkLocation();
                }
            }
        }
        if(requestCode == REQUEST_BACKGROUND_LOCATION) { //checks the requestCode for the relevant permission
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LocationManager locationManager = (LocationManager) MainActivity.this.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
                boolean gps, network;
                if (locationManager==null){
                    Toast.makeText(this,"Error with location manager",Toast.LENGTH_SHORT).show();
                    return;
                }
                gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                enabled=gps&&network;
            } else {
                //the permission is not granted, the app cannot be used
                Toast.makeText(this,"this app needs a background location permission",Toast.LENGTH_SHORT).show();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    checkBackgroundLocation();
                }
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
        String identifier=adapter.getAddress().split(":")[0];
        adapter.setName("SWbox_"+identifier);
        if(enabled) {
            scanner = adapter.getBluetoothLeScanner();
            Toast.makeText(MainActivity.this, "start scanning", Toast.LENGTH_SHORT).show();
            scanSettings = new ScanSettings.Builder();
            scanSettings.setScanMode(SCAN_MODE_LOW_LATENCY);
            loop.run();
        }
        else{
            Toast.makeText(MainActivity.this.getApplicationContext(), "this app needs bluetooth and location services on to work", Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                checkLocation();
            }
        }
    }

    //scans continuously and stop/start scan every WAIT_PERIOD
    Runnable loop = new Runnable() {
        @Override
        public void run() {
            scanner.stopScan(scanCallback);
            scanner.startScan(new ArrayList<ScanFilter>(),scanSettings.build(),scanCallback);
            handler.postDelayed(loop,WAIT_PERIOD);
        }
    };

    @Override
    protected void onStop(){
        Log.d("STOP","onstop");
        SharedPreferences sharedPreferences = getSharedPreferences("SharedPref",MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("count", count);
        editor.apply();

        handler.removeCallbacks(loop);
        scanner.stopScan(scanCallback);

        if (vibrator != null) {
            vibrator.cancel();
        }
        if(advertiser!=null) {
            advertiser.stopAdvertising(advertisingCallback);
        }
        super.onStop();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) { //when a BLE device is scanned, add it to the list
            double dist;
            BluetoothDevice device=result.getDevice();
            int tx_estimation=-70; //tx power approximation for devices under API 26, based on a conversion between TX_POWER_HIGH (defined in advertising settings) and a value in dbm
            float environment=2;
            ScanRecord scan=result.getScanRecord();

            if(scan!=null && scan.getDeviceName()!=null && scan.getDeviceName().contains("SWbox")) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    dist = Math.pow(10d, (result.getTxPower() - result.getRssi()) / (10 * environment));
                } else {
                    dist = Math.pow(10d, (tx_estimation - result.getRssi()) / (10 * environment));
                }

                Log.d("SCAN",scan.toString());
                Log.d("DIST",String.valueOf(dist));

                if (dist <= maxdistance) { //ignore devices past the maximum distance
                    if (!scannedDevices.containsKey(device) || System.currentTimeMillis() - scannedDevices.get(device) > DELAY) { //ignore devices that have already been scanned for DELAY time
                        scannedDevices.put(device, System.currentTimeMillis());
                        nameField.append("name: " + device.getName() + " ,address: " + device.getAddress() + "\n");
                        count += 1;
                        countField.setText(Integer.toString(count));

                        byte[] serviceData = scan.getServiceData(new ParcelUuid(SERVICE_UUID));
                        if (serviceData != null && serviceData.length > 0) {
                            int contagion = serviceData[0];
                            rcvField.setText("Lu " + contagion + " from " + device.getName());
                            if (contagion == 1) {
                                if (vibrator != null) {
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
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d("ERROR","scan failed");
        }
    };

    public void sendMessage(View view) {
        /*if(!adapter.isMultipleAdvertisementSupported()){
            Toast.makeText(this,"ERROR ADVERTISING NOT SUPPORTED", Toast.LENGTH_SHORT).show();
        }
        else {*/
            advertiser = adapter.getBluetoothLeAdvertiser();

            byte[] data= new byte[]{(byte) (positif.isChecked()?1:0)}; //add count after

            AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).build();

            AdvertiseData advertiseData = new AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .setIncludeTxPowerLevel(true)
                    .addServiceData(new ParcelUuid(SERVICE_UUID), data).build();

            if(advertiser!=null) {
                advertiser.stopAdvertising(advertisingCallback);
                advField.setText("advertising " +Arrays.toString(advertiseData.getServiceData().get(new ParcelUuid(SERVICE_UUID))));
                advertiser.startAdvertising(advertiseSettings, advertiseData, advertisingCallback);
            }
            else{
                advField.setText(R.string.adv_non_trouve);
            }

    }

    private AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartFailure(int errorCode) {
            Log.d("ERROR", String.format("Advertisement failure (code %d)", errorCode));
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d("SUCCESS", "Advertisement started");
        }
    };
}