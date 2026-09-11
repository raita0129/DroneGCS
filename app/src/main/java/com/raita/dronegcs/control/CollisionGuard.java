package com.raita.dronegcs.control;

import com.raita.dronegcs.core.DroneState;
import com.raita.dronegcs.debug.Logger;

import android.content.Context;

import java.util.*;

public class CollisionGuard {

    private static final double SAFE_DISTANCE_METERS = 2.0; // 安全最小間距，依實際機體尺寸調整
    private final Context context;

    public CollisionGuard(Context context) {
        this.context = context;
    }

    public List<String> checkViolations(Map<String, DroneState> fleetState) {
        List<String> violations = new ArrayList<>();
        List<String> ids = new ArrayList<>(fleetState.keySet());

        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                DroneState a = fleetState.get(ids.get(i));
                DroneState b = fleetState.get(ids.get(j));
                double dist = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude);
                if (dist < SAFE_DISTANCE_METERS) {
                    String msg = ids.get(i) + " 與 " + ids.get(j) + " 距離 " + String.format("%.2f", dist) + "m，低於安全門檻";
                    violations.add(msg);
                    Logger.e("CollisionGuard", msg);
                }
            }
        }
        return violations;
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}