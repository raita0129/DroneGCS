package com.raita.dronegcs.core;

public class TelemetryValidator {

    public static boolean isValidPosition(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isNaN(lon)
                && !Double.isInfinite(lat) && !Double.isInfinite(lon)
                && lat >= -90 && lat <= 90
                && lon >= -180 && lon <= 180;
    }

    public static boolean isValidAltitude(float relativeAltitude) {
        return !Float.isNaN(relativeAltitude) && relativeAltitude > -10 && relativeAltitude < 10000;
    }

    public static float clampBattery(float percent) {
        if (Float.isNaN(percent)) return 0f;
        return Math.max(0f, Math.min(100f, percent));
    }

    public static boolean isValidState(double lat, double lon, float relativeAltitude) {
        return isValidPosition(lat, lon) && isValidAltitude(relativeAltitude);
    }
}