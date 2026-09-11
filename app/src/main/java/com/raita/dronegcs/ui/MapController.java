package com.raita.dronegcs.ui;

import android.content.Context;

import java.io.File;
import java.util.*;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import com.raita.dronegcs.core.DroneState;

public class MapController {

    private final MapView mapView;
    private final Map<String, Marker> markers = new HashMap<>();
    private boolean followDrone = false;

    public MapController(Context context, MapView mapView) {
        this.mapView = mapView;
        setup(context);
    }

    private void setup(Context context) {
        Configuration.getInstance().setUserAgentValue(context.getPackageName());
        Configuration.getInstance().setOsmdroidBasePath(context.getExternalFilesDir(null));
        Configuration.getInstance().setOsmdroidTileCache(
                new File(context.getExternalFilesDir(null), "osmdroid/tiles"));

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(17.0);
    }

    public void updateFleet(Map<String, DroneState> fleetState, Map<String, Boolean> staleFlags) {
        for (Map.Entry<String, DroneState> entry : fleetState.entrySet()) {
            String droneId = entry.getKey();
            DroneState state = entry.getValue();
            GeoPoint point = new GeoPoint(state.latitude, state.longitude);

            Marker marker = markers.get(droneId);
            if (marker == null) {
                marker = new Marker(mapView);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                mapView.getOverlays().add(marker);
                markers.put(droneId, marker);
            }
            marker.setPosition(point);

            boolean isStale = staleFlags != null && Boolean.TRUE.equals(staleFlags.get(droneId));
            marker.setTitle(isStale ? "⚠ " + droneId + "（資料過期）" : droneId);
            marker.setSubDescription(String.format("模式:%s／電量:%.0f%%%s",
                    state.flightMode, state.batteryPercent, isStale ? "／最後更新已逾3秒" : ""));
            marker.setAlpha(isStale ? 0.5f : 1.0f);
        }
        if (followDrone && !fleetState.isEmpty()) {
            double avgLat = fleetState.values().stream().mapToDouble(s -> s.latitude).average().orElse(0);
            double avgLon = fleetState.values().stream().mapToDouble(s -> s.longitude).average().orElse(0);
            mapView.getController().animateTo(new GeoPoint(avgLat, avgLon));
        }
        mapView.invalidate();
    }

    public void setFollowDrone(boolean follow) {
        this.followDrone = follow;
    }

    public void onResume() {
        mapView.onResume();
    }

    public void onPause() {
        mapView.onPause();
    }
}