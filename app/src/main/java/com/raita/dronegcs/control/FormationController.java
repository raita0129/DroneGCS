package com.raita.dronegcs.control;

import com.raita.dronegcs.core.PositionTarget;
import com.raita.dronegcs.core.Vector3;
import com.raita.dronegcs.core.GeoUtils;
import java.util.*;

public class FormationController {

    public enum FormationType { TRIANGLE, LINE, SQUARE }

    private final Map<String, Vector3> offsets = new HashMap<>();

    public void setFormation(FormationType type, List<String> droneIds) {
        offsets.clear();
        switch (type) {
            case TRIANGLE:
                applyOffsetsInOrder(droneIds,
                        new Vector3(-3f, 0f, 0f),
                        new Vector3(-3f, -3f, 0f),
                        new Vector3(-3f, 3f, 0f));
                break;
            case LINE:
                for (int i = 0; i < droneIds.size(); i++) {
                    offsets.put(droneIds.get(i), new Vector3(0f, i * 3f, 0f));
                }
                break;
            case SQUARE:
                applyOffsetsInOrder(droneIds,
                        new Vector3(0f, 0f, 0f),
                        new Vector3(0f, 3f, 0f),
                        new Vector3(-3f, 0f, 0f),
                        new Vector3(-3f, 3f, 0f));
                break;
        }
    }

    private void applyOffsetsInOrder(List<String> droneIds, Vector3... pattern) {
        for (int i = 0; i < droneIds.size() && i < pattern.length; i++) {
            offsets.put(droneIds.get(i), pattern[i]);
        }
    }

    public Map<String, PositionTarget> computeTargets(double leaderLat, double leaderLon,
                                                      float leaderAltitude, float leaderYawDeg) {
        Map<String, PositionTarget> targets = new HashMap<>();
        for (Map.Entry<String, Vector3> entry : offsets.entrySet()) {
            Vector3 rotatedOffset = entry.getValue().rotateByYaw(leaderYawDeg);
            double[] latLon = GeoUtils.offsetToLatLon(leaderLat, leaderLon, rotatedOffset.north, rotatedOffset.east);
            targets.put(entry.getKey(), new PositionTarget(
                    latLon[0], latLon[1], leaderAltitude - rotatedOffset.down, leaderYawDeg
            ));
        }
        return targets;
    }

    public Set<String> getFormationDroneIds() { return offsets.keySet(); }
}