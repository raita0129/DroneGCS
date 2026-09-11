package com.raita.dronegcs.connection;

import io.mavsdk.System;
import io.mavsdk.mavsdkserver.MavsdkServer;
import io.reactivex.Observable;
import com.raita.dronegcs.core.ConnectionConfig;
import com.raita.dronegcs.core.DroneState;
import com.raita.dronegcs.core.PositionTarget;
import com.raita.dronegcs.core.TelemetryValidator;
import com.raita.dronegcs.debug.Logger;
import android.content.Context;
import java.util.*;

public class DroneFleetManager {

    private final Context context;
    private final Map<String, MavsdkServer> servers = new HashMap<>();
    private final Map<String, System> drones = new HashMap<>();
    private final Map<String, CustomMessageChannel> customChannels = new HashMap<>(); // v1.11新增

    public DroneFleetManager(Context context) {
        this.context = context;
    }

    public void connect(ConnectionConfig config) {
        io.reactivex.schedulers.Schedulers.io().scheduleDirect(() -> {
            String id = config.droneId;
            if (drones.containsKey(id)) {
                Logger.w("FleetManager", id + " 已連線，略過重複連線請求");
                return;
            }
            Logger.i("FleetManager", id + " 連線至: " + config.toMavsdkUrl());

            MavsdkServer server = new MavsdkServer();
            int port = server.run(config.toMavsdkUrl());
            System drone = new System("localhost", port);

            servers.put(id, server);
            drones.put(id, drone);
            customChannels.put(id, new CustomMessageChannel(id, config.companionHost, config.companionPort));

            drone.getCore().getConnectionState().subscribe(
                    state -> Logger.i("FleetManager", id + " connected=" + state.getIsConnected()),
                    err -> Logger.e("FleetManager", id + " connectionState發生錯誤: " + err.getMessage())
            );
        });
    }

    public void disconnect(String droneId) {
        MavsdkServer server = servers.remove(droneId);
        drones.remove(droneId);
        CustomMessageChannel channel = customChannels.remove(droneId);
        if (channel != null) {
            channel.close();
        }
        if (server != null) {
            server.stop();
            Logger.i("FleetManager", droneId + " 已中斷連線");
        }
    }

    public void disconnectAll() {
        new HashSet<>(servers.keySet()).forEach(this::disconnect);
    }

    public Observable<DroneState> observeState(String droneId) {
        System drone = drones.get(droneId);
        if (drone == null) return Observable.empty();
        return Observable.combineLatest(
                        drone.getTelemetry().getPosition().toObservable(),
                        drone.getTelemetry().getFlightMode().toObservable(),
                        drone.getTelemetry().getBattery().toObservable(),
                        drone.getTelemetry().getArmed().toObservable(),
                        (pos, mode, battery, armed) -> new DroneState(
                                droneId, pos.getLatitudeDeg(), pos.getLongitudeDeg(), pos.getRelativeAltitudeM(),
                                mode.toString(), TelemetryValidator.clampBattery(battery.getRemainingPercent() * 100), armed
                        )
                )
                .filter(state -> {
                    boolean valid = TelemetryValidator.isValidState(
                            state.latitude, state.longitude, state.relativeAltitude);
                    if (!valid) {
                        Logger.w("FleetManager",
                                droneId + " 收到異常遙測數值(lat=" + state.latitude + ", lon=" + state.longitude
                                        + ", alt=" + state.relativeAltitude + ")，已捨棄本筆");
                    }
                    return valid;
                });
    }

    public Observable<DroneState> observeAllStates() {
        List<Observable<DroneState>> streams = new ArrayList<>();
        for (String id : drones.keySet()) streams.add(observeState(id));
        return Observable.merge(streams);
    }

    public Observable<Long> observeHeartbeatTimestamps(String droneId) {
        System drone = drones.get(droneId);
        if (drone == null) return Observable.empty();
        return drone.getTelemetry().getPosition().toObservable().map(p -> java.lang.System.currentTimeMillis());
    }

    public System getDrone(String droneId) { return drones.get(droneId); }
    public Set<String> getConnectedDroneIds() { return drones.keySet(); }

    public CustomMessageChannel getCustomMessageChannel(String droneId) {
        return customChannels.get(droneId);
    }

    public void enterOffboard(String droneId) {
        System drone = drones.get(droneId);
        if (drone == null) return;

        drone.getTelemetry().getPosition().firstOrError().subscribe(pos -> {
            io.mavsdk.offboard.Offboard.PositionGlobalYaw initial =
                    new io.mavsdk.offboard.Offboard.PositionGlobalYaw(
                            pos.getLatitudeDeg(), pos.getLongitudeDeg(), pos.getAbsoluteAltitudeM(), 0f,
                            io.mavsdk.offboard.Offboard.PositionGlobalYaw.AltitudeType.AMSL);
            drone.getOffboard().setPositionGlobal(initial)
                    .andThen(drone.getOffboard().start())
                    .subscribe(
                            () -> Logger.i("FleetManager", droneId + " 已進入offboard模式"),
                            err -> Logger.e("FleetManager", droneId + " 進入offboard失敗: " + err.getMessage())
                    );
        });
    }

    public void setPositionTarget(String droneId, PositionTarget target) {
        System drone = drones.get(droneId);
        if (drone == null) return;
        io.mavsdk.offboard.Offboard.PositionGlobalYaw setpoint =
                new io.mavsdk.offboard.Offboard.PositionGlobalYaw(
                        target.latitude, target.longitude, target.relativeAltitude, target.yawDeg,
                        io.mavsdk.offboard.Offboard.PositionGlobalYaw.AltitudeType.REL_HOME);
        drone.getOffboard().setPositionGlobal(setpoint).subscribe();
    }

    public void stopOffboard(String droneId) {
        System drone = drones.get(droneId);
        if (drone == null) return;
        drone.getOffboard().stop().subscribe(
                () -> Logger.i("FleetManager", droneId + " 已退出offboard模式"),
                err -> {}
        );
    }
}