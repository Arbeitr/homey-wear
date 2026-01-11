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
                HomeyAPI api = HomeyAPI.getAPI();
                Map<String, Device> allDevices = api.getAllDevices();
                
                List<Device> zoneDevices = new ArrayList<>();
                for (Device device : allDevices.values()) {
                    if (zoneId.equals(device.getZoneId())) {
                        device.fetchIconImage();
                        zoneDevices.add(device);
                    }
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    if (zoneDevices.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        deviceList.setVisibility(View.GONE);
                    } else {
                        deviceAdapter.setDevices(zoneDevices);
                        emptyView.setVisibility(View.GONE);
                        deviceList.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                Timber.e(e, "Failed to load devices for zone");
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    emptyView.setText(R.string.error);
                    emptyView.setVisibility(View.VISIBLE);
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
