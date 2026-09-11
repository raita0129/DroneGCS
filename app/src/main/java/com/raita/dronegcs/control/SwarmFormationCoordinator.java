package com.raita.dronegcs.control;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.concurrent.TimeUnit;
import java.util.*;

import android.content.Context;

import com.raita.dronegcs.connection.DroneFleetManager;
import com.raita.dronegcs.core.*;
import com.raita.dronegcs.debug.Logger;

public class SwarmFormationCoordinator {

    private static final long TICK_INTERVAL_MS = 200;

    private final DroneFleetManager fleetManager;
    private final FormationController formationController;
    private final CollisionGuard collisionGuard;
    private final PotentialFieldAdjuster potentialFieldAdjuster;
    private final Context context;

    private Disposable controlLoop;
    private double virtualLeaderLat;
    private double virtualLeaderLon;
    private float virtualLeaderAltitude = 5f;
    private float virtualLeaderYaw = 0f;
    private Map<String, DroneState> latestFleetState = new HashMap<>();

    public SwarmFormationCoordinator(Context context, DroneFleetManager fleetManager) {
        this.context = context;
        this.fleetManager = fleetManager;
        this.formationController = new FormationController();
        this.collisionGuard = new CollisionGuard(context);
        this.potentialFieldAdjuster = new PotentialFieldAdjuster();
    }

    public void updateFleetState(Map<String, DroneState> state) {
        this.latestFleetState = state;
    }

    public void startFormation(FormationController.FormationType type, List<String> droneIds) {
        if (droneIds.isEmpty()) {
            Logger.w("SwarmCoordinator", "沒有已連線的無人機，無法啟動隊形");
            return;
        }
        double avgLat = latestFleetState.values().stream().mapToDouble(s -> s.latitude).average().orElse(0);
        double avgLon = latestFleetState.values().stream().mapToDouble(s -> s.longitude).average().orElse(0);
        virtualLeaderLat = avgLat;
        virtualLeaderLon = avgLon;

        formationController.setFormation(type, droneIds);
        for (String id : droneIds) {
            fleetManager.enterOffboard(id);
        }
        Logger.i("SwarmCoordinator",
                "啟動隊形: " + type + "，機隊: " + droneIds + "，原點:(" + avgLat + "," + avgLon + ")");
        startLoop();
    }

    private void startLoop() {
        stopLoop();
        controlLoop = Observable.interval(TICK_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .observeOn(Schedulers.computation())
                .subscribe(tick -> {
                            Map<String, PositionTarget> rawTargets = formationController.computeTargets(
                                    virtualLeaderLat, virtualLeaderLon, virtualLeaderAltitude, virtualLeaderYaw);

                            Map<String, PositionTarget> adjustedTargets = new HashMap<>();
                            for (Map.Entry<String, PositionTarget> entry : rawTargets.entrySet()) {
                                String droneId = entry.getKey();
                                PositionTarget raw = entry.getValue();
                                double[] adjusted = potentialFieldAdjuster.adjustTarget(
                                        droneId, raw.latitude, raw.longitude, latestFleetState);
                                adjustedTargets.put(droneId, new PositionTarget(
                                        adjusted[0], adjusted[1], raw.relativeAltitude, raw.yawDeg));
                            }

                            List<String> violations = collisionGuard.checkViolations(latestFleetState);
                            if (!violations.isEmpty()) {
                                Logger.w("SwarmCoordinator",
                                        "APF修正後仍偵測到過近距離，本次tick暫停位置更新作為最後防線");
                                return;
                            }

                            for (Map.Entry<String, PositionTarget> entry : adjustedTargets.entrySet()) {
                                fleetManager.setPositionTarget(entry.getKey(), entry.getValue());
                            }
                        }, error ->
                                Logger.e("SwarmCoordinator", "控制迴圈發生例外: " + error.getMessage())
                );
    }

    public void stopFormation(List<String> droneIds) {
        stopLoop();
        for (String id : droneIds) fleetManager.stopOffboard(id);
        Logger.i("SwarmCoordinator", "隊形已停止");
    }

    private void stopLoop() {
        if (controlLoop != null && !controlLoop.isDisposed()) controlLoop.dispose();
    }

    public void moveVirtualLeader(double lat, double lon, float altitude, float yawDeg) {
        this.virtualLeaderLat = lat;
        this.virtualLeaderLon = lon;
        this.virtualLeaderAltitude = altitude;
        this.virtualLeaderYaw = yawDeg;
    }
}