package com.raita.dronegcs.service;

import android.app.*;
import android.content.Intent;
import android.os.*;

import androidx.core.app.NotificationCompat;

import io.reactivex.Observable;

import com.raita.dronegcs.R;
import com.raita.dronegcs.connection.DroneFleetManager;
import com.raita.dronegcs.connection.HeartbeatWatchdog;
import com.raita.dronegcs.connection.CustomMessageChannel;
import com.raita.dronegcs.control.MissionStateMachine;
import com.raita.dronegcs.control.SwarmFormationCoordinator;
import com.raita.dronegcs.control.FormationController;
import com.raita.dronegcs.core.ConnectionConfig;
import com.raita.dronegcs.core.ConnectionConfigStore;
import com.raita.dronegcs.core.DroneState;
import com.raita.dronegcs.debug.CrashHandler;
import com.raita.dronegcs.debug.Logger;

import java.util.*;

public class GcsControlService extends Service {

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "gcs_control_channel";

    private DroneFleetManager fleetManager;
    private SwarmFormationCoordinator formationCoordinator;
    private final Map<String, HeartbeatWatchdog> watchdogs = new HashMap<>();
    private final Map<String, MissionStateMachine> stateMachines = new HashMap<>();
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public GcsControlService getService() {
            return GcsControlService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("尚未連線"));
        fleetManager = new DroneFleetManager(this);
        formationCoordinator = new SwarmFormationCoordinator(this, fleetManager);

        fleetManager.setOnConnectionFailedListener((droneId, reason) ->
                updateNotification(droneId + " 連線失敗"));

        fleetManager.observeAllStates()
                .scan(new HashMap<String, DroneState>(), (map, state) -> {
                    HashMap<String, DroneState> updated = new HashMap<>(map);
                    updated.put(state.droneId, state);
                    return updated;
                })
                .subscribe(
                        formationCoordinator::updateFleetState,
                        err -> Logger.e("Service", "機隊狀態串流發生錯誤: " + err.getMessage())
                );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            if (CrashHandler.isInCrashLoop(this)) {
                Logger.e("Service", "偵測到崩潰迴圈，停止自動重連，等待使用者手動處理");
                updateNotification("偵測到反覆崩潰，已停止自動重連，請開啟App確認");
                return START_STICKY;
            }
            Logger.w("Service", "Service由系統重啟(可能因先前crash)，嘗試恢復連線");
            ConnectionConfigStore configStore = new ConnectionConfigStore(this);
            List<ConnectionConfig> savedConfigs = configStore.loadAll();
            for (ConnectionConfig config : savedConfigs) {
                connectToDrone(config);
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void connectToDrone(ConnectionConfig config) {
        String id = config.droneId;
        fleetManager.connect(config);
        stateMachines.put(id, new MissionStateMachine(id, this));

        HeartbeatWatchdog watchdog = new HeartbeatWatchdog(this);
        watchdog.start(fleetManager.observeHeartbeatTimestamps(id), () -> handleHeartbeatTimeout(id));
        watchdogs.put(id, watchdog);

        CustomMessageChannel channel = fleetManager.getCustomMessageChannel(id);
        if (channel != null) {
            channel.observeCustomStatusAck().subscribe(
                    ack -> {
                    }, // observeCustomStatusAck內部已記log，這裡先不額外處理
                    err -> Logger.e("Service", id + " 自訂訊息ACK訂閱發生錯誤: " + err.getMessage())
            );
        }

        updateNotification("已連線: " + fleetManager.getConnectedDroneIds().size() + "台");
    }

    public void sendCustomStatus(String droneId, int statusCode, float customValue) {
        CustomMessageChannel channel = fleetManager.getCustomMessageChannel(droneId);
        if (channel != null) {
            channel.sendCustomStatus(statusCode, customValue);
        }
    }

    private void handleHeartbeatTimeout(String droneId) {
        Logger.e("Service", droneId + " 心跳逾時，觸發重連流程");
        updateNotification(droneId + " 連線中斷，嘗試重連");
    }

    public void requestTakeoff(String droneId) {
        io.mavsdk.System drone = fleetManager.getDrone(droneId);
        MissionStateMachine sm = stateMachines.get(droneId);
        if (drone == null || sm == null) return;

        if (sm.getCurrentPhase() == MissionStateMachine.Phase.CONNECTED) {
            sm.requestArm(drone, phase -> {
                updateNotification(droneId + ": " + phase);
                if (phase == MissionStateMachine.Phase.ARMED) {
                    sm.requestTakeoff(drone, p -> updateNotification(droneId + ": " + p));
                }
            });
        } else {
            sm.requestTakeoff(drone, phase -> updateNotification(droneId + ": " + phase));
        }
    }

    public void requestLand(String droneId) {
        io.mavsdk.System drone = fleetManager.getDrone(droneId);
        MissionStateMachine sm = stateMachines.get(droneId);
        if (drone != null && sm != null) {
            sm.requestLand(drone, phase -> updateNotification(droneId + ": " + phase));
        }
    }

    public void requestTakeoffAll() {
        fleetManager.getConnectedDroneIds().forEach(this::requestTakeoff);
    }

    public void requestLandAll() {
        fleetManager.getConnectedDroneIds().forEach(this::requestLand);
    }

    public Observable<DroneState> observeAllStates() {
        return fleetManager.observeAllStates();
    }

    public void startFormation(FormationController.FormationType type) {
        List<String> ids = new ArrayList<>(fleetManager.getConnectedDroneIds());
        formationCoordinator.startFormation(type, ids);
    }

    public void stopFormation() {
        List<String> ids = new ArrayList<>(fleetManager.getConnectedDroneIds());
        formationCoordinator.stopFormation(ids);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "GCS控制服務", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(String status) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("無人機機隊GCS")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_stat_gcs)
                .build();
    }

    private void updateNotification(String status) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification(status));
    }

    @Override
    public void onDestroy() {
        fleetManager.disconnectAll();
        watchdogs.values().forEach(HeartbeatWatchdog::stop);
        super.onDestroy();
    }
}