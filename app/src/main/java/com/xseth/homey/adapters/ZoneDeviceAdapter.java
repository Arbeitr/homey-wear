package com.xseth.homey.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.xseth.homey.R;
import com.xseth.homey.homey.DeviceRepository;
import com.xseth.homey.homey.models.Device;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Adapter for displaying devices in a grid layout
 */
public class ZoneDeviceAdapter extends RecyclerView.Adapter<ZoneDeviceAdapter.DeviceViewHolder> {

    private List<Device> devices = new ArrayList<>();
    private int loadingIndex = -1;

    @Override
    public DeviceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_zone_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(DeviceViewHolder holder, int position) {
        Device device = devices.get(position);
        holder.bind(device, position == loadingIndex);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public void setDevices(List<Device> devices) {
        this.devices = devices;
        notifyDataSetChanged();
    }

    public void setLoading(int index) {
        this.loadingIndex = index;
        notifyDataSetChanged();
    }

    public void refreshDeviceStates() {
        notifyDataSetChanged();
    }

    class DeviceViewHolder extends RecyclerView.ViewHolder {
        private CardView cardView;
        private ImageView deviceIcon;
        private TextView deviceName;
        private ProgressBar progressBar;
        private Device currentDevice;

        public DeviceViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.device_card);
            deviceIcon = itemView.findViewById(R.id.device_icon);
            deviceName = itemView.findViewById(R.id.device_name);
            progressBar = itemView.findViewById(R.id.progress_bar);

            cardView.setOnClickListener(v -> {
                if (currentDevice != null) {
                    toggleDevice();
                }
            });
        }

        public void bind(Device device, boolean isLoading) {
            this.currentDevice = device;
            
            deviceName.setText(device.getName());
            deviceIcon.setImageBitmap(device.getIconImage());
            
            // Show/hide progress bar
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            
            // Set background color based on device state
            updateCardColor();
        }

        private void updateCardColor() {
            int colorId = currentDevice.isOn() ? R.color.device_on : R.color.device_off;
            String colorString = cardView.getContext().getResources().getString(colorId);
            cardView.setCardBackgroundColor(Color.parseColor(colorString));
        }

        private void toggleDevice() {
            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            
            setLoading(position);

            currentDevice.turnOnOff().enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    boolean status = true;

                    if (response.body() != null && response.body().containsKey("value")) {
                        status = (boolean) response.body().get("value");
                    }

                    currentDevice.setOn(status);
                    DeviceRepository.getInstance().update(currentDevice);
                    
                    updateCardColor();

                    if (currentDevice.isButton()) {
                        String text = cardView.getContext().getString(R.string.button_press, currentDevice.getName());
                        Toast.makeText(cardView.getContext(), text, Toast.LENGTH_SHORT).show();
                    }

                    setLoading(-1);
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    setLoading(-1);
                    Timber.e(t, "Failed to toggle device");
                    Toast.makeText(cardView.getContext(), R.string.fail_turnonoff, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
