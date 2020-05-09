package com.example.safeway;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class PrintActivity extends Activity {
    HashMap<String,String> savedDevice=new HashMap<>();
    TextView nameField;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print);
        nameField = findViewById(R.id.name);
        getFromIntent();
    }

    @Override
    protected void onResume(){
        super.onResume();
        if(savedDevice!=null) {
            Set<String> names = savedDevice.keySet();
            Collection<String> datas = savedDevice.values();
            Iterator<String> it = names.iterator();
            Iterator<String> it2 = datas.iterator();
            nameField.setText(R.string.list);
            while (it.hasNext() && it2.hasNext()) {
                String name = it.next();
                String data = it2.next();
                String[] output = data.split(",");
                nameField.append(name + " a dit " + (output[1].equals("0") ? "non contaminé" : "contamine") + " le " + output[4] + " " + output[3] + " " + output[2] + " à " + output[5] + "h" + output[6] + "\n");
            }
        }
    }

    private void getFromIntent() {
        /* gets all extras from intent */
        savedDevice = (HashMap<String, String>) getIntent().getSerializableExtra("deviceList");
    }

    public void backToMain(View view) {
        Intent mainIntent = new Intent(PrintActivity.this, MainActivity.class);
        startActivity(mainIntent);
    }
}
