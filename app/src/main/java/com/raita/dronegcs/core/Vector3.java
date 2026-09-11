package com.raita.dronegcs.core;

public class Vector3 {
    public final float north;
    public final float east;
    public final float down;

    public Vector3(float north, float east, float down) {
        this.north = north;
        this.east = east;
        this.down = down;
    }

    public Vector3 rotateByYaw(float yawDeg) {
        double rad = Math.toRadians(yawDeg);
        float n = (float) (north * Math.cos(rad) - east * Math.sin(rad));
        float e = (float) (north * Math.sin(rad) + east * Math.cos(rad));
        return new Vector3(n, e, down);
    }
}