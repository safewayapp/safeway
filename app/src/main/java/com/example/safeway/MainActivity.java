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
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;

public class MainActivity extends AppCompatActivity {
    long WAIT_PERIOD = 25*60*1000; //25 minutes, scanning time
    long DELAY=10*1000;//10 seconds, wait time for devices that have already been scanned

    static int maxdistance=25; //distance for scan
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

        scanner = adapter.getBluetoothLeScanner();
        String identifier=adapter.getAddress().split(":")[0];
        adapter.setName("SWbox_"+identifier);
        scanner = adapter.getBluetoothLeScanner();
        Toast.makeText(MainActivity.this, "Scan d'appareils bluetooth démarré", Toast.LENGTH_SHORT).show();
        scanSettings = new ScanSettings.Builder();
        scanSettings.setScanMode(SCAN_MODE_LOW_LATENCY);
        loop.run();
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
        Set<BluetoothDevice> s=scannedDevices.keySet();
        for (BluetoothDevice b : s) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd/HH/mm", new Locale("fr", "FR"));
            String[] output = formatter.format(new Date()).split("/");
            savedDevice.put(b.getName(), b.getAddress() + "," + scannedDevices.get(b)[1] + "," + output[0] + "," + output[1] + "," + output[2] + "," + output[3] + "," + output[4]);
        }
        SharedPreferences sharedPreferences = getSharedPreferences("SharedPref",MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Set<String> devicesNames = new HashSet<>(savedDevice.keySet());
        Set<String> devicesData=new HashSet<>(savedDevice.values());
        editor.putStringSet("devicesNames", devicesNames);
        editor.putStringSet("devicesData",devicesData);
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
        @SuppressLint("SetTextI18n")
        @Override
        public void onScanResult(int callbackType, ScanResult result) { //when a BLE device is scanned, add it to the list
            double dist;
            int contagion=0;
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

                if (dist <= maxdistance) { //ignore devices past the maximum distance
                    byte[] serviceData = scan.getServiceData(new ParcelUuid(SERVICE_UUID));
                    if (serviceData != null && serviceData.length > 0) {
                        contagion = serviceData[0];
                        rcvField.setText("Lu " + contagion + " de " + device.getName());
                    }
                    if (contagion == 1 && !scannedDevices.containsKey(device)) {
                        if (vibrator != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                            } else { //deprecated in API 26
                                vibrator.vibrate(1000);
                            }
                        }
                    }
                    if (scannedDevices.containsKey(device) && System.currentTimeMillis() - scannedDevices.get(device)[0] > DELAY) {
                        //permanently saves devices that have already been scanned for DELAY time
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd/HH/mm",new Locale("fr", "FR"));
                        String[] output=formatter.format(new Date()).split("/");
                        //format: nom: adresse,contagion,année,mois,jour,heure,minute
                        savedDevice.put(device.getName(),device.getAddress()+","+scannedDevices.get(device)[1]+","+output[0]+","+output[1]+","+output[2]+","+output[3]+","+output[4]);
                        scannedDevices.remove(device);
                    }
                    else if (!scannedDevices.containsKey(device)) {
                        Set<BluetoothDevice> names=scannedDevices.keySet();
                        boolean nameInside=false;
                        for (BluetoothDevice bd : names) {
                            if (bd.getName().equals(device.getName())) {
                                nameInside=true;
                            }
                        }
                        if(!nameInside) {
                            scannedDevices.put(device, new Long[]{System.currentTimeMillis(), (long) contagion});
                            if (!savedDevice.containsKey(device.getName())) {
                                count += 1;
                                countField.setText(Integer.toString(count));
                            }
                        }
                    }

                    nameField.setText("Appareils bluetooth scannés: \n");
                    for (Iterator<BluetoothDevice> iterator = scannedDevices.keySet().iterator(); iterator.hasNext();) {
                        BluetoothDevice bd = iterator.next();
                        if(System.currentTimeMillis() - scannedDevices.get(bd)[0]>DELAY){
                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd/HH/mm",new Locale("fr", "FR"));
                            String[] output=formatter.format(new Date()).split("/");
                            savedDevice.put(device.getName(),device.getAddress()+","+scannedDevices.get(bd)[1]+","+output[0]+","+output[1]+","+output[2]+","+output[3]+","+output[4]);
                            iterator.remove();
                        }
                        else {
                            nameField.append("nom: " + bd.getName() + " ,adresse: " + bd.getAddress() + "\n");
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
        loop.run();
        advertiser = adapter.getBluetoothLeAdvertiser();
        byte[] data= new byte[]{(byte) (positif.isChecked()?1:0)}; //add count after

        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).build();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceData(new ParcelUuid(SERVICE_UUID), data).build();

        if(advertiser!=null) {
            advertiser.stopAdvertising(advertisingCallback);
            advField.setText("envoie " +Arrays.toString(advertiseData.getServiceData().get(new ParcelUuid(SERVICE_UUID)))+" par advertising");
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
        Toast.makeText(this,"stopping scan and printing list",Toast.LENGTH_SHORT).show();
        handler.removeCallbacks(loop);
        scanner.stopScan(scanCallback);

        //updating the saved devices list
        Set<BluetoothDevice> s=scannedDevices.keySet();
        for (BluetoothDevice b : s) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd/HH/mm", new Locale("fr", "FR"));
            String[] output = formatter.format(new Date()).split("/");
            savedDevice.put(b.getName(), b.getAddress() + "," + scannedDevices.get(b)[1] + "," + output[0] + "," + output[1] + "," + output[2] + "," + output[3] + "," + output[4]);
        }

        nameField.setText(R.string.list);
        System.out.println(savedDevice.toString());
        Set<String> names = savedDevice.keySet();
        Collection<String> datas = savedDevice.values();
        Iterator<String> it=names.iterator();
        Iterator<String> it2=datas.iterator();
        while(it.hasNext() && it2.hasNext()){
            String name=it.next();
            String data=it2.next();
            //format: nom: adresse,contagion,année,mois,jour,heure,minute
            String[] output=data.split(",");
            nameField.append(name+" a dit "+(output[1].equals("0")? "non contaminé": "contamine") + " le " + output[4] + " " + output[3] + " " + output[2] + " à " + output[5] + "h" + output[6] + "\n");
        }
    }
}