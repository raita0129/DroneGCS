package com.raita.dronegcs.core;

public class GeoUtils {

    private static final double METERS_PER_DEG_LAT = 111320.0;

    public static double[] offsetToLatLon(double originLat, double originLon, float northOffset, float eastOffset) {
        double metersPerDegLon = METERS_PER_DEG_LAT * Math.cos(Math.toRadians(originLat));
        double lat = originLat + (northOffset / METERS_PER_DEG_LAT);
        double lon = originLon + (eastOffset / metersPerDegLon);
        return new double[]{lat, lon};
    }

    public static float[] latLonToOffset(double originLat, double originLon, double targetLat, double targetLon) {
        double metersPerDegLon = METERS_PER_DEG_LAT * Math.cos(Math.toRadians(originLat));
        float north = (float) ((targetLat - originLat) * METERS_PER_DEG_LAT);
        float east = (float) ((targetLon - originLon) * metersPerDegLon);
        return new float[]{north, east};
    }
}