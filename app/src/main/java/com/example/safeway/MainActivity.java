package com.example.safeway;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;

public class MainActivity extends AppCompatActivity {
    BluetoothAdapter adapter;
    BluetoothLeScanner scanner;
    BluetoothLeAdvertiser advertiser;
    ScanSettings.Builder scanSettings;

    HashMap<BluetoothDevice,Long[]> scannedDevices = new HashMap<>(); //map of scanned devices and the time at which they were last seen
    HashMap<String,String> savedDevice=new HashMap<>();

    int count=0; //number of encountered devices

    CheckBox positif;
    TextView countField;
    TextView nameField;
    TextView advField;
    TextView rcvField;
    Handler handler;
    Vibrator vibrator;

    public static final long WAIT_PERIOD = 25*60*1000; //25 minutes, scanning time
    public static final long DELAY=10*1000;//10 seconds, wait time for devices that have already been scanned
    public static final int SCAN_DIST=10; //distance for scan
    public static final double VIBRATE_DIST=3.5;
    public static final double SOUND_DIST=1.5;
    public static final UUID SERVICE_UUID = UUID.fromString("0000483e-0000-1000-8000-00805f9b34fb"); //UUID for advertising

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler=new Handler();
        vibrator= (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        /* fields from view */
        positif=findViewById(R.id.contagion);
        countField=findViewById(R.id.count);
        nameField=findViewById(R.id.name);
        advField=findViewById(R.id.adv_text);
        rcvField=findViewById(R.id.receiv_text);
    }

    @SuppressLint("HardwareIds")
    @Override
    protected void onResume() {
        super.onResume();

        /* Restoring values from SharedPreferences */
        SharedPreferences sharedPref = getSharedPreferences("SharedPref",MODE_PRIVATE);
        count = sharedPref.getInt("count", 0);
        countField.setText(String.valueOf(count));
        Set<String> savedNames =sharedPref.getStringSet("devicesNames",null);
        Set<String> savedData = sharedPref.getStringSet("devicesData", null);
        if(savedNames != null && savedData != null) {
            Iterator<String> iterator = savedNames.iterator();
            Iterator<String> it = savedData.iterator();
            while (iterator.hasNext() && it.hasNext()) {
                savedDevice.put(iterator.next(), it.next());
            }
        }

        /* Runtime permissions check */
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
        boolean gps=true,network=true,permission=true,permission2=true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permission=ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            LocationManager locationManager = (LocationManager) MainActivity.this.getSystemService(Context.LOCATION_SERVICE);
            gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permission2 = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        if (adapter == null || !adapter.isEnabled()||!(permission&&permission2&&gps&&network)) { //checking if bluetooth is turned off, then asks for activation
            Intent backToPermissions= new Intent(MainActivity.this, PermissionsActivity.class);
            startActivity(backToPermissions);
        }

        /* sets the device name */
        String identifier=adapter.getAddress().split(":")[0];
        adapter.setName("SWbox_"+identifier);
        scanner = adapter.getBluetoothLeScanner();

        /* starts scanning */
        Toast.makeText(MainActivity.this, "Scan d'appareils bluetooth démarré", Toast.LENGTH_SHORT).show();
        scanSettings = new ScanSettings.Builder();
        scanSettings.setScanMode(SCAN_MODE_LOW_LATENCY);
        loop.run();
    }

    /* scans continuously and stop/start scan every WAIT_PERIOD -> counters Android rule to stop scan after 30 continuous min */
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
        /* saves the scanned devices in the saved devices list */
        Set<BluetoothDevice> s=scannedDevices.keySet();
        for (BluetoothDevice b : s) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd/HH/mm", new Locale("fr", "FR"));
            String[] output = formatter.format(new Date()).split("/");
            savedDevice.put(b.getName(), b.getAddress() + "," + scannedDevices.get(b)[1] + "," + output[0] + "," + output[1] + "," + output[2] + "," + output[3] + "," + output[4]);
        }

        /* saves the count and saved devices list */
        SharedPreferences sharedPreferences = getSharedPreferences("SharedPref",MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Set<String> devicesNames = new HashSet<>(savedDevice.keySet());
        Set<String> devicesData=new HashSet<>(savedDevice.values());
        editor.putStringSet("devicesNames", devicesNames);
        editor.putStringSet("devicesData",devicesData);
        editor.putInt("count", count);
        editor.apply();

        /* resets text fields */
        advField.setText(R.string.advertising_test);
        rcvField.setText(R.string.received_text);
        nameField.setText(R.string.device_name);

        /* stops scanning and advertising */
        handler.removeCallbacks(loop);
        scanner.stopScan(scanCallback);

        if(advertiser!=null) {
            advertiser.stopAdvertising(advertisingCallback);
        }

        super.onStop();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onScanResult(int callbackType, ScanResult result) { //when a BLE device is scanned, add it to the list
            double dist; //distance between the two devices
            int contagion=0; //data received from advertising
            BluetoothDevice device=result.getDevice();
            int tx_estimation=-68; //tx power approximation for devices under API 26, based on a conversion between TX_POWER_HIGH (defined in advertising settings) and a value in dbm
           // float environment=2; //approximation for the environment, 2 is the value for outside
            ScanRecord scan=result.getScanRecord();

            if(scan!=null && scan.getDeviceName()!=null && scan.getDeviceName().contains("SWbox")) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    double ratio = result.getRssi()*1.0/tx_estimation; //result.getTxPower();
                    dist =  (0.89976)*Math.pow(ratio,7.7095) + 0.111;
                    //dist = Math.pow(10d, (result.getTxPower() - result.getRssi()) / (10 * environment));
                } else {
                    double ratio = result.getRssi()*1.0/tx_estimation;
                    dist =  (0.89976)*Math.pow(ratio,7.7095) + 0.111;
                }
                if (dist <= SCAN_DIST) { //ignore devices past the maximum distance
                    byte[] serviceData = scan.getServiceData(new ParcelUuid(SERVICE_UUID)); //get data from advertising

                    if (serviceData != null && serviceData.length > 0) {
                        contagion = serviceData[0]; //set contagion field
                        rcvField.setText("Lu " + contagion + " de " + device.getName());
                    }

                    if (contagion == 1 && !scannedDevices.containsKey(device) && dist <= VIBRATE_DIST) {
                        //the first time a device is scanned, if it advertises 1 and is close, the phone vibrates
                        if (vibrator != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                            } else { //deprecated in API 26
                                vibrator.vibrate(500);
                            }
                        }
                        if(dist<=SOUND_DIST){ //if it's really close, the phone also rings a notification
                            Uri notification =  RingtoneManager.getActualDefaultRingtoneUri(MainActivity.this,RingtoneManager.TYPE_NOTIFICATION);
                            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                            r.play();
                            //r.stop();
                        }
                    }

                    if (!scannedDevices.containsKey(device)) { //if a device has not already been scanned
                        scannedDevices.put(device, new Long[]{System.currentTimeMillis(), (long) contagion});
                        if (!savedDevice.containsKey(device.getName())) { //if the device has not been saved we increase the count
                            count += 1;
                            countField.setText(Integer.toString(count));
                        }
                    }
                    else if (scannedDevices.containsKey(device) && System.currentTimeMillis() - scannedDevices.get(device)[0] > DELAY) { //permanently saves devices that have already been scanned for DELAY time
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd/HH/mm",new Locale("fr", "FR"));
                        String[] output=formatter.format(new Date()).split("/");
                        savedDevice.put(device.getName(),device.getAddress()+","+scannedDevices.get(device)[1]+","+output[0]+","+output[1]+","+output[2]+","+output[3]+","+output[4]);
                        scannedDevices.remove(device);
                    }

                    nameField.setText("Appareils bluetooth scannés: \n");
                    for (Iterator<BluetoothDevice> iterator = scannedDevices.keySet().iterator(); iterator.hasNext();) { //print every devices nearby and saves the ones scanned for more than DELAY time
                        BluetoothDevice bd = iterator.next();
                        if(System.currentTimeMillis() - scannedDevices.get(bd)[0]>DELAY){
                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd/HH/mm",new Locale("fr", "FR"));
                            String[] output=formatter.format(new Date()).split("/");
                            savedDevice.put(device.getName(),device.getAddress()+","+scannedDevices.get(bd)[1]+","+output[0]+","+output[1]+","+output[2]+","+output[3]+","+output[4]);
                            iterator.remove();
                        }
                        else {
                            nameField.append("nom: " + bd.getName() + ", adresse: " + bd.getAddress() + "\n");
                        }
                    }
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            System.out.println("ERREUR, scan échoué");
        }
    };

    public void sendMessage(View view) {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
        advertiser = adapter.getBluetoothLeAdvertiser(); //get the advertiser

        byte[] data= new byte[]{(byte) (positif.isChecked()?1:0)}; //advertising data

        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).build();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceData(new ParcelUuid(SERVICE_UUID), data).build();

        if(advertiser!=null) {
            advertiser.stopAdvertising(advertisingCallback);
            advField.setText("envoie " + Arrays.toString(advertiseData.getServiceData().get(new ParcelUuid(SERVICE_UUID))) + " par advertising");
            advertiser.startAdvertising(advertiseSettings, advertiseData, advertisingCallback);
        }
        else{
            advField.setText(R.string.adv_non_trouve);
        }
    }

    private AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartFailure(int errorCode) {
             Toast.makeText(MainActivity.this,String.format(new Locale("fr", "FR"),"ERREUR, L'advertisement a échoué (code %d)", errorCode),Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Toast.makeText(MainActivity.this,"SUCCES, L'advertisement a démarré",Toast.LENGTH_SHORT).show();
        }
    };

    public void printList(View view) {
        /* stops scanning and advertising */
        Toast.makeText(this,"stopping scan and printing list",Toast.LENGTH_SHORT).show();
        handler.removeCallbacks(loop);
        scanner.stopScan(scanCallback);

        if(advertiser!=null) {
            advertiser.stopAdvertising(advertisingCallback);
        }

        /* saves the scanned devices in the saved devices list */
        Set<BluetoothDevice> s=scannedDevices.keySet();
        for (BluetoothDevice b : s) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd/HH/mm", new Locale("fr", "FR"));
            String[] output = formatter.format(new Date()).split("/");
            savedDevice.put(b.getName(), b.getAddress() + "," + scannedDevices.get(b)[1] + "," + output[0] + "," + output[1] + "," + output[2] + "," + output[3] + "," + output[4]);
        }

        /* Calls the print activity */
        Intent it = new Intent(MainActivity.this, PrintActivity.class);
        it.putExtra("deviceList",savedDevice);
        startActivity(it);
    }
}