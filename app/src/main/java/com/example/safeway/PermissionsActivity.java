package com.example.safeway;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionsActivity extends Activity {
    private static final int ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_LOCATION = 2;
    private static final int REQUEST_BACKGROUND_LOCATION = 3;
    boolean enabled=true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) { //checking if the phone can use BLE; if not stops the app
            Toast.makeText(this.getApplicationContext(), "Le Bluetooth low energy n'est pas utilisable sur ce téléphone", Toast.LENGTH_SHORT).show();
            finish();
        }

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            System.out.println("Erreur, le bluetooth n'est pas utilisable");
            finish();
        }
        BluetoothAdapter adapter = manager.getAdapter();

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
        boolean permission= ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
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
                LocationManager locationManager = (LocationManager) PermissionsActivity.this.getSystemService(Context.LOCATION_SERVICE);
                if (locationManager == null) {
                    Toast.makeText(this, "Erreur de localisation", Toast.LENGTH_SHORT).show();
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
            LocationManager locationManager = (LocationManager) PermissionsActivity.this.getSystemService(Context.LOCATION_SERVICE);
            if(locationManager==null){
                Toast.makeText(this,"Erreur de localisation",Toast.LENGTH_SHORT).show();
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
                    LocationManager locationManager = (LocationManager) PermissionsActivity.this.getSystemService(Context.LOCATION_SERVICE);
                    boolean gps, network;
                    if (locationManager == null) {
                        Toast.makeText(this, "Erreur de localisation", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                    network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                    enabled = gps && network;
                }
            } else {
                //the permission is not granted, the app cannot be used
                Toast.makeText(this,"Cette application a besoin de la permission pour la localisation, pour scanner les appareils bluetooth",Toast.LENGTH_SHORT).show();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    checkLocation();
                }
            }
        }
        if(requestCode == REQUEST_BACKGROUND_LOCATION) { //checks the requestCode for the relevant permission
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LocationManager locationManager = (LocationManager) PermissionsActivity.this.getSystemService(Context.LOCATION_SERVICE);
                boolean gps, network;
                if (locationManager==null){
                    Toast.makeText(this,"Erreur de localisation",Toast.LENGTH_SHORT).show();
                    return;
                }
                gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                enabled=gps&&network;
            } else {
                //the permission is not granted, the app cannot be used
                Toast.makeText(this,"Cette application a besoin de la permission pour la localisation, pour scanner les appareils bluetooth",Toast.LENGTH_SHORT).show();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    checkBackgroundLocation();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(enabled){
            Intent mainIntent = new Intent(PermissionsActivity.this, MainActivity.class);
            startActivity(mainIntent);
        }
        else{
            Toast.makeText(PermissionsActivity.this, "Vous devez activer le bluetooth et la localisation", Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                checkLocation();
            }
        }
    }
}
