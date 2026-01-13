package com.xseth.homey.zones;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import com.xseth.homey.R;
import com.xseth.homey.adapters.ZoneDeviceAdapter;
import com.xseth.homey.homey.HomeyAPI;
import com.xseth.homey.homey.models.Device;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Activity showing devices for a specific zone
 */
public class ZoneDevicesActivity extends FragmentActivity {

    private WearableRecyclerView deviceList;
    private ZoneDeviceAdapter deviceAdapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private String zoneId;
    private String zoneName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zone_devices);

        zoneId = getIntent().getStringExtra("zoneId");
        zoneName = getIntent().getStringExtra("zoneName");

        TextView title = findViewById(R.id.zone_title);
        title.setText(zoneName);

        progressBar = findViewById(R.id.progress_bar);
        emptyView = findViewById(R.id.empty_view);
        deviceList = findViewById(R.id.device_list);

        // Use GridLayoutManager for better layout on round screen
        deviceList.setLayoutManager(new GridLayoutManager(this, 2));
        deviceList.requestFocus();

        deviceAdapter = new ZoneDeviceAdapter();
        deviceList.setAdapter(deviceAdapter);

        loadDevices();
    }

    /**
     * Load devices for the selected zone
     */
    private void loadDevices() {
        progressBar.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            try {
                Timber.d("loadDevices: Starting for zone %s (%s)", zoneName, zoneId);
                HomeyAPI api = HomeyAPI.getAPI();
                
                // Wait for API to be fully authenticated
                Timber.d("loadDevices: Waiting for HomeyAPI authentication...");
                api.waitForHomeyAPI();
                Timber.d("loadDevices: HomeyAPI authenticated, fetching all devices...");
                
                Map<String, Device> allDevices = api.getAllDevices();
                Timber.d("loadDevices: Received %d total devices", allDevices != null ? allDevices.size() : 0);
                
                if (allDevices == null || allDevices.isEmpty()) {
                    Timber.w("loadDevices: No devices returned from API");
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        emptyView.setText("Keine Geräte verfügbar");
                        emptyView.setVisibility(View.VISIBLE);
                    });
                    return;
                }
                
                List<Device> zoneDevices = new ArrayList<>();
                int matchCount = 0;
                for (Device device : allDevices.values()) {
                    if (zoneId.equals(device.getZoneId())) {
                        matchCount++;
                        Timber.d("loadDevices: Found device %s in zone %s", device.getName(), zoneName);
                        device.fetchIconImage();
                        zoneDevices.add(device);
                    }
                }
                
                Timber.d("loadDevices: Found %d devices matching zone %s", matchCount, zoneName);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    if (zoneDevices.isEmpty()) {
                        Timber.d("loadDevices: No devices in this zone, showing empty view");
                        emptyView.setVisibility(View.VISIBLE);
                        deviceList.setVisibility(View.GONE);
                    } else {
                        Timber.d("loadDevices: Setting %d devices to adapter", zoneDevices.size());
                        deviceAdapter.setDevices(zoneDevices);
                        emptyView.setVisibility(View.GONE);
                        deviceList.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                Timber.e(e, "loadDevices: Failed to load devices - %s", e.getMessage());
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    emptyView.setText(R.string.error);
                    emptyView.setVisibility(View.VISIBLE);
                    android.widget.Toast.makeText(this, 
                        "Fehler beim Laden der Geräte: " + e.getMessage(), 
                        android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh device states when returning to this activity
        if (deviceAdapter.getItemCount() > 0) {
            deviceAdapter.refreshDeviceStates();
        }
    }
}
