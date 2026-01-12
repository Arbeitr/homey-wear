package com.xseth.homey.zones;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.wear.widget.WearableRecyclerView;
import androidx.wear.widget.drawer.WearableActionDrawerView;
import androidx.wear.widget.drawer.WearableDrawerLayout;

import com.xseth.homey.BuildConfig;
import com.xseth.homey.MainActivity;
import com.xseth.homey.R;
import com.xseth.homey.homey.HomeyAPI;
import com.xseth.homey.homey.models.Zone;
import com.xseth.homey.utils.ColorRunner;
import com.xseth.homey.utils.OAuth;
import com.xseth.homey.utils.utils;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Activity showing list of zones for navigation
 */
public class ZoneListActivity extends FragmentActivity implements MenuItem.OnMenuItemClickListener,
        View.OnClickListener {

    private WearableRecyclerView zoneList;
    private ZoneListAdapter zoneAdapter;
    private WearableActionDrawerView drawer;
    private FrameLayout notifications;
    private ProgressBar notificationsProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Configure Timber logging
        if (BuildConfig.DEBUG)
            Timber.plant(new Timber.DebugTree());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zone_list);

        // Initialize static context and appPath needed by HomeyAPI and Token
        MainActivity.context = this.getApplicationContext();
        MainActivity.appPath = MainActivity.context.getFilesDir().getAbsolutePath();
        Timber.d("onCreate: Set appPath to %s", MainActivity.appPath);

        // View used for rainbow background
        WearableDrawerLayout background = findViewById(R.id.zone_back);
        ColorRunner.startColorRunner(this, background);

        // View used for notifications
        notifications = findViewById(R.id.notification);
        notifications.setOnClickListener(this);
        
        notificationsProgress = notifications.findViewById(R.id.progressBar);
        utils.randomiseProgressBar(notificationsProgress);

        // Verify authentication in background
        new Thread(() -> {
            try {
                Timber.d("onCreate: Starting authentication check");
                HomeyAPI api = HomeyAPI.getAPI();

                if (api.isLoggedIn()) {
                    Timber.d("onCreate: User is logged in, authenticating with Homey");
                    api.authenticateHomey();
                    Timber.d("onCreate: Authentication successful, loading zones");
                    loadZones();
                } else {
                    Timber.w("onCreate: User not logged in, showing login notification");
                    OAuth.startOAuth(this);
                    setNotification(R.string.login, R.drawable.ic_login);
                }
            } catch(UnknownHostException uhe){
                Timber.e(uhe, "onCreate: No internet connection");
                setNotification(R.string.no_internet, R.drawable.ic_cloud_off);
            } catch(Exception e) {
                Timber.e(e, "onCreate: Authentication error - %s", e.getMessage());
                setNotification(R.string.error, R.drawable.ic_error);
            }
        }).start();

        // Setup RecyclerView with GridLayoutManager for better Wear OS experience
        zoneList = findViewById(R.id.zone_list);
        zoneList.setLayoutManager(new GridLayoutManager(this, 2));
        zoneList.requestFocus();

        zoneAdapter = new ZoneListAdapter(zone -> {
            // Navigate to zone devices activity
            Intent intent = new Intent(this, ZoneDevicesActivity.class);
            intent.putExtra("zoneId", zone.getId());
            intent.putExtra("zoneName", zone.getName());
            startActivity(intent);
        });
        zoneList.setAdapter(zoneAdapter);

        // Top Navigation Drawer
        drawer = findViewById(R.id.action_drawer);
        drawer.setOnMenuItemClickListener(this);
    }

    /**
     * Load zones from HomeyAPI
     */
    private void loadZones() {
        new Thread(() -> {
            try {
                Timber.d("Starting loadZones()");
                HomeyAPI api = HomeyAPI.getAPI();
                
                // Wait for API to be fully authenticated
                Timber.d("Waiting for HomeyAPI authentication...");
                api.waitForHomeyAPI();
                Timber.d("HomeyAPI authenticated, fetching zones...");
                
                Map<String, Zone> zonesMap = api.getZones();
                Timber.d("Received zones map with %d zones", zonesMap != null ? zonesMap.size() : 0);
                
                if (zonesMap == null || zonesMap.isEmpty()) {
                    Timber.w("No zones returned from API");
                    runOnUiThread(() -> {
                        setNotification(R.string.error, R.drawable.ic_error);
                        android.widget.Toast.makeText(this, "Keine Zonen gefunden", android.widget.Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                
                List<Zone> zones = new ArrayList<>(zonesMap.values());
                Timber.d("Created zones list with %d items", zones.size());
                
                runOnUiThread(() -> {
                    zoneAdapter.setZones(zones);
                    notifications.setVisibility(View.GONE);
                    zoneList.setVisibility(View.VISIBLE);
                    Timber.d("UI updated with zones");
                });
            } catch (Exception e) {
                Timber.e(e, "Failed to load zones - Exception: %s", e.getMessage());
                runOnUiThread(() -> {
                    setNotification(R.string.error, R.drawable.ic_error);
                    android.widget.Toast.makeText(this, 
                        "Fehler beim Laden der Zonen: " + e.getMessage(), 
                        android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ColorRunner.resumeColorRunner();
    }

    @Override
    protected void onStop() {
        super.onStop();
        ColorRunner.pauseColorRunner();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        OAuth.stopOAuth();
        ColorRunner.stopColorRunner();
    }

    @Override
    public void onClick(View v) {
        TextView message = notifications.findViewById(R.id.message);

        // Check if login notification is shown
        if(!message.getText().toString().equals(getResources().getString(R.string.login)))
            return;

        utils.showConfirmationPhone(this.getApplicationContext(), R.string.authenticate);
        notificationsProgress.setVisibility(View.VISIBLE);

        OAuth.sendAuthorization(this, new OAuth.OAuthUIHandler() {
            @Override
            public void onAuthSuccess() {
                notificationsProgress.setVisibility(View.INVISIBLE);
                notifications.setVisibility(View.GONE);
                zoneList.setVisibility(View.VISIBLE);
                loadZones();
            }

            @Override
            public void onAuthFailure() {
                notificationsProgress.setVisibility(View.INVISIBLE);
            }
        });
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        final int itemId = menuItem.getItemId();

        if (itemId == R.id.device_refresh) {
            loadZones();
        } else if (itemId == R.id.voice_control) {
            Intent intent = new Intent(this, com.xseth.homey.voice.VoiceActivity.class);
            startActivity(intent);
        }

        drawer.getController().closeDrawer();
        return true;
    }

    /**
     * Show notification with message and icon
     */
    private void setNotification(int message_id, int icon_id){
        TextView message = notifications.findViewById(R.id.message);
        ImageView icon = findViewById(R.id.icon);

        message.setText(message_id);
        icon.setImageResource(icon_id);

        runOnUiThread(() -> {
            zoneList.setVisibility(View.GONE);
            notifications.setVisibility(View.VISIBLE);
        });
    }
}
