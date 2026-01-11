package com.xseth.homey;

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.wear.widget.WearableRecyclerView;
import androidx.wear.widget.drawer.WearableActionDrawerView;
import androidx.wear.widget.drawer.WearableDrawerLayout;

import com.xseth.homey.adapters.DeviceViewModel;
import com.xseth.homey.adapters.OnOffAdapter;
import com.xseth.homey.homey.DeviceRepository;
import com.xseth.homey.utils.ColorRunner;
import com.xseth.homey.homey.HomeyAPI;
import com.xseth.homey.utils.OAuth;
import com.xseth.homey.utils.utils;

import java.net.UnknownHostException;

import timber.log.Timber;

public class MainActivity extends FragmentActivity implements MenuItem.OnMenuItemClickListener,
        View.OnClickListener{

    // deviceViewModel for holding device data
    private DeviceViewModel deviceViewModel;
    // Bottom drawer object
    private WearableActionDrawerView drawer;
    // Layout used in notifications
    public FrameLayout notifications;
    // ProgressBar in notifications view
    public ProgressBar notificationsProgress;
    // Recyclerview containing devices
    public WearableRecyclerView vOnOffList;
    // Adapter for showing onoff devices
    private OnOffAdapter onOffAdapter;
    // Path to app directory on system
    public static String appPath;
    // ApplicationContext
    public static Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Configure Timber logging
        if (BuildConfig.DEBUG)
            Timber.plant(new Timber.DebugTree());

        // Create view
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this.getApplicationContext();
        appPath = context.getFilesDir().getAbsolutePath();

        // View used for rainbow background
        WearableDrawerLayout vOnOffBack = findViewById(R.id.onoff_back);
        ColorRunner.startColorRunner(this, vOnOffBack);

        // View used for notifications
        notifications = findViewById(R.id.notification);
        notifications.setOnClickListener(this);

        notificationsProgress = notifications.findViewById(R.id.progressBar);
        utils.randomiseProgressBar(notificationsProgress);

        // Verify if there is authentication/internet in background
        new Thread(() -> {
            try {
                // Start the HomeyAPI
                HomeyAPI api = HomeyAPI.getAPI();

                if (api.isLoggedIn()) {
                    Timber.i("G");
                    api.authenticateHomey();

                } else {
                    Timber.w("No session, authenticating!");
                    OAuth.startOAuth(this);
                    setNotification(R.string.login, R.drawable.ic_login);
                }

            // No internet connection, show notification
            } catch(UnknownHostException uhe){
                setNotification(R.string.no_internet, R.drawable.ic_cloud_off);

            }catch(Exception e) {
                Timber.e(e);
                setNotification(R.string.error, R.drawable.ic_error);
            }
        }).start();

        // Recycler view containing devices
        vOnOffList = findViewById(R.id.onoff_list);
        vOnOffList.requestFocus(); // Focus required for scrolling via hw-buttons

        // use a linear layout manager
        vOnOffList.setLayoutManager(new LinearLayoutManager(this));

        // specify an adapter (see also next example)
        onOffAdapter = new OnOffAdapter();
        vOnOffList.setAdapter(onOffAdapter);

        // Add PagerSnapHelper to vOnOffList
        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(vOnOffList);

        // Get ViewModelProvider, and set LiveData devices list as input for adapter
        deviceViewModel = new ViewModelProvider(this).get(DeviceViewModel.class);
        deviceViewModel.getDevices().observe(this, onOffAdapter::setDevices);

        // Top Navigation Drawer
        drawer = findViewById(R.id.action_drawer);
        drawer.setOnMenuItemClickListener(this);

        Timber.d("Finish onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Restart background thread
        ColorRunner.resumeColorRunner();
        onOffAdapter.setLoading(true);

        // Sync statuses of devices.
        new Thread(() -> {
            DeviceRepository.getInstance().refreshDeviceStatuses();

            // Device statusses updated, remove loading
            runOnUiThread(() -> onOffAdapter.setLoading(false));
        }).start();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Pause background thread
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

        OAuth.sendAuthorization(this);
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        final int itemId = menuItem.getItemId();

        if (itemId == R.id.device_refresh) {
            this.onOffAdapter.setLoading(true);
            this.deviceViewModel.refreshDevices();

            // Device refreshed, remove loading
            runOnUiThread(() -> onOffAdapter.setLoading(false));
        } else if (itemId == R.id.voice_control) {
            // Launch voice activity
            Intent intent = new Intent(this, com.xseth.homey.voice.VoiceActivity.class);
            startActivity(intent);
        }

        drawer.getController().closeDrawer();
        return true;
    }

    /**
     * Show the notification fragment with selected text & icon
     * @param message_id text to show in fragment
     * @param icon_id icon to show in fragment
     */
    public void setNotification(int message_id, int icon_id){
        TextView message = notifications.findViewById(R.id.message);
        ImageView icon = findViewById(R.id.icon);

        message.setText(message_id);
        icon.setImageResource(icon_id);

        runOnUiThread(() -> {
            vOnOffList.setVisibility(View.GONE);
            notifications.setVisibility(View.VISIBLE);
        });
    }
}


