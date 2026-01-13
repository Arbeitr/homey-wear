package com.xseth.homey.zones;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.xseth.homey.R;
import com.xseth.homey.homey.models.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying zones in a grid
 */
public class ZoneListAdapter extends RecyclerView.Adapter<ZoneListAdapter.ZoneViewHolder> {

    private List<Zone> zones = new ArrayList<>();
    private OnZoneClickListener listener;

    public interface OnZoneClickListener {
        void onZoneClick(Zone zone);
    }

    public ZoneListAdapter(OnZoneClickListener listener) {
        this.listener = listener;
    }

    @Override
    public ZoneViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_zone, parent, false);
        return new ZoneViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(ZoneViewHolder holder, int position) {
        holder.bind(zones.get(position));
    }

    @Override
    public int getItemCount() {
        return zones.size();
    }

    public void setZones(List<Zone> zones) {
        this.zones = zones;
        notifyDataSetChanged();
    }

    static class ZoneViewHolder extends RecyclerView.ViewHolder {
        private TextView zoneName;
        private Zone currentZone;

        public ZoneViewHolder(View itemView, OnZoneClickListener listener) {
            super(itemView);
            zoneName = itemView.findViewById(R.id.zone_name);
            
            itemView.setOnClickListener(v -> {
                if (currentZone != null && listener != null) {
                    listener.onZoneClick(currentZone);
                }
            });
        }

        public void bind(Zone zone) {
            this.currentZone = zone;
            zoneName.setText(zone.getName());
        }
    }
}
