package com.raita.dronegcs.control;

import android.content.*;
import android.os.IBinder;

import io.reactivex.Observable;

import com.raita.dronegcs.core.ConnectionConfig;
import com.raita.dronegcs.core.DroneState;
import com.raita.dronegcs.service.GcsControlService;

import java.util.List;

public class DroneFleetRepository {

    private GcsControlService service;
    private boolean isBound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((GcsControlService.LocalBinder) binder).getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    public void bind(Context context) {
        Intent intent = new Intent(context, GcsControlService.class);
        context.startForegroundService(intent);
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    public void unbind(Context context) {
        if (isBound) {
            context.unbindService(connection);
            isBound = false;
        }
    }

    public Observable<DroneState> observeAllDroneStates() {
        return (!isBound || service == null) ? Observable.empty() : service.observeAllStates();
    }

    public void connectAll(List<ConnectionConfig> configs) {
        if (isBound) configs.forEach(service::connectToDrone);
    }

    public void requestTakeoff(String droneId) {
        if (isBound) service.requestTakeoff(droneId);
    }

    public void requestLand(String droneId) {
        if (isBound) service.requestLand(droneId);
    }

    public void requestTakeoffAll() {
        if (isBound) service.requestTakeoffAll();
    }

    public void requestLandAll() {
        if (isBound) service.requestLandAll();
    }

    public void startFormation(FormationController.FormationType type) {
        if (isBound) service.startFormation(type);
    }

    public void stopFormation() {
        if (isBound) service.stopFormation();
    }
}