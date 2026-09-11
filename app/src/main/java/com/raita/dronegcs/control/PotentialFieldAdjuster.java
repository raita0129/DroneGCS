package com.raita.dronegcs.control;

import com.raita.dronegcs.core.DroneState;
import com.raita.dronegcs.core.GeoUtils;

import java.util.Map;

public class PotentialFieldAdjuster {

    private static final double REPULSION_RADIUS = 3.0;
    private static final double REPULSION_GAIN = 0.5;

    public double[] adjustTarget(String droneId, double targetLat, double targetLon,
                                 Map<String, DroneState> currentFleetState) {
        double adjustedLat = targetLat;
        double adjustedLon = targetLon;

        for (Map.Entry<String, DroneState> entry : currentFleetState.entrySet()) {
            if (entry.getKey().equals(droneId)) continue;

            DroneState other = entry.getValue();
            double distance = haversineMeters(targetLat, targetLon, other.latitude, other.longitude);

            if (distance < REPULSION_RADIUS && distance > 0.01) {
                double repulsionMagnitude = REPULSION_GAIN * (REPULSION_RADIUS - distance) / distance;
                float[] direction = GeoUtils.latLonToOffset(other.latitude, other.longitude, targetLat, targetLon);
                double norm = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1]);
                if (norm > 0.01) {
                    double[] pushedLatLon = GeoUtils.offsetToLatLon(adjustedLat, adjustedLon,
                            (float) (direction[0] / norm * repulsionMagnitude),
                            (float) (direction[1] / norm * repulsionMagnitude));
                    adjustedLat = pushedLatLon[0];
                    adjustedLon = pushedLatLon[1];
                }
            }
        }
        return new double[]{adjustedLat, adjustedLon};
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}