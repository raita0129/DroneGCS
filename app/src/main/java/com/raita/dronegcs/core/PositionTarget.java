package com.raita.dronegcs.core;

public class PositionTarget {
    public final double latitude;
    public final double longitude;
    public final float relativeAltitude;
    public final float yawDeg;

    public PositionTarget(double latitude, double longitude, float relativeAltitude, float yawDeg) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.relativeAltitude = relativeAltitude;
        this.yawDeg = yawDeg;
    }
}