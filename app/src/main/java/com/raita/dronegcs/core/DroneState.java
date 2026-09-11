package com.raita.dronegcs.core;

public class DroneState {
    public final String droneId;
    public final double latitude;
    public final double longitude;
    public final float relativeAltitude;
    public final String flightMode;
    public final float batteryPercent;
    public final boolean isArmed;
    public final long timestamp;

    public DroneState(String droneId, double latitude, double longitude, float relativeAltitude,
                      String flightMode, float batteryPercent, boolean isArmed) {
        this.droneId = droneId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.relativeAltitude = relativeAltitude;
        this.flightMode = flightMode;
        this.batteryPercent = batteryPercent;
        this.isArmed = isArmed;
        this.timestamp = System.currentTimeMillis();
    }
}