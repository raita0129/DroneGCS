package com.raita.dronegcs.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.raita.dronegcs.R;
import com.raita.dronegcs.core.DroneState;
import java.util.*;

public class DroneStatusAdapter extends RecyclerView.Adapter<DroneStatusAdapter.ViewHolder> {

    public interface OnDroneActionListener {
        void onTakeoff(String droneId);
        void onLand(String droneId);
    }

    private List<String> droneIds = new ArrayList<>();
    private Map<String, DroneState> fleetState = new HashMap<>();
    private Map<String, Boolean> staleFlags = new HashMap<>();
    private final OnDroneActionListener listener;

    public DroneStatusAdapter(OnDroneActionListener listener) {
        this.listener = listener;
    }

    public void update(Map<String, DroneState> newFleetState, Map<String, Boolean> newStaleFlags) {
        this.fleetState = newFleetState;
        this.staleFlags = newStaleFlags != null ? newStaleFlags : new HashMap<>();
        this.droneIds = new ArrayList<>(newFleetState.keySet());
        Collections.sort(this.droneIds);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_drone_status, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String droneId = droneIds.get(position);
        DroneState state = fleetState.get(droneId);
        boolean isStale = Boolean.TRUE.equals(staleFlags.get(droneId));

        holder.droneIdText.setText(isStale ? "⚠ " + droneId : droneId);
        if (state != null) {
            holder.detailText.setText(String.format("模式:%s／電量:%.0f%%%s",
                    state.flightMode, state.batteryPercent, isStale ? "／資料過期" : ""));
        }
        holder.itemView.setAlpha(isStale ? 0.5f : 1.0f);
        holder.statusIndicator.setBackgroundResource(
                isStale ? R.drawable.circle_indicator_stale : R.drawable.circle_indicator);

        holder.btnTakeoff.setOnClickListener(v -> listener.onTakeoff(droneId));
        holder.btnLand.setOnClickListener(v -> listener.onLand(droneId));
    }

    @Override
    public int getItemCount() { return droneIds.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView droneIdText, detailText;
        View statusIndicator;
        android.widget.Button btnTakeoff, btnLand;

        ViewHolder(View itemView) {
            super(itemView);
            droneIdText = itemView.findViewById(R.id.drone_id_text);
            detailText = itemView.findViewById(R.id.drone_detail_text);
            statusIndicator = itemView.findViewById(R.id.status_indicator);
            btnTakeoff = itemView.findViewById(R.id.btn_item_takeoff);
            btnLand = itemView.findViewById(R.id.btn_item_land);
        }
    }
}