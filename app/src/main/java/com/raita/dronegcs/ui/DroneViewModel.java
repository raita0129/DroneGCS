package com.raita.dronegcs.ui;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.*;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

import com.raita.dronegcs.control.DroneFleetRepository;
import com.raita.dronegcs.control.FormationController;
import com.raita.dronegcs.core.*;
import com.raita.dronegcs.debug.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DroneViewModel extends AndroidViewModel {

    private static final long STALE_THRESHOLD_MS = 3000; // 超過3秒沒更新視為過期

    private final DroneFleetRepository repository;
    private final ConnectionConfigStore configStore;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<Map<String, DroneState>> fleetState = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Map<String, Boolean>> staleFlags = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<String> connectionStatusText = new MutableLiveData<>("未連線");
    private final MutableLiveData<Uri> exportedLogUri = new MutableLiveData<>();

    public DroneViewModel(@NonNull Application application) {
        super(application);
        repository = new DroneFleetRepository();
        configStore = new ConnectionConfigStore(application);
    }

    public void bindService() {
        repository.bind(getApplication());
        disposables.add(
                repository.observeAllDroneStates()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                state -> {
                                    Map<String, DroneState> current = new HashMap<>(fleetState.getValue());
                                    current.put(state.droneId, state);
                                    fleetState.setValue(current);
                                    connectionStatusText.setValue(current.size() + "台已連線");
                                },
                                error -> connectionStatusText.setValue("連線異常: " + error.getMessage())
                        )
        );

        disposables.add(
                Observable.interval(1000, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(tick -> {
                            Map<String, DroneState> currentFleet = fleetState.getValue();
                            if (currentFleet == null) return;
                            Map<String, Boolean> stale = new HashMap<>();
                            long now = System.currentTimeMillis();
                            for (Map.Entry<String, DroneState> entry : currentFleet.entrySet()) {
                                boolean isStale = (now - entry.getValue().timestamp) > STALE_THRESHOLD_MS;
                                stale.put(entry.getKey(), isStale);
                                if (isStale) {
                                    Logger.w("ViewModel",
                                            entry.getKey() + " 遙測資料已超過" + STALE_THRESHOLD_MS + "ms未更新");
                                }
                            }
                            staleFlags.setValue(stale);
                        }, error -> Logger.e("ViewModel", "資料新鮮度檢查發生錯誤: " + error.getMessage()))
        );
    }

    public void connectWithSavedConfig() {
        repository.connectAll(configStore.loadAll());
    }

    public void importConfig(Uri fileUri) {
        try {
            List<ConnectionConfig> configs = configStore.importFromUri(getApplication(), fileUri);
            Logger.i("ViewModel", "已匯入 " + configs.size() + " 台無人機設定");
            repository.connectAll(configs);
        } catch (IOException e) {
            Logger.e("ViewModel", "設定檔匯入失敗: " + e.getMessage());
            connectionStatusText.setValue("設定檔匯入失敗");
        }
    }

    public void takeoff(String droneId) {
        repository.requestTakeoff(droneId);
    }

    public void land(String droneId) {
        repository.requestLand(droneId);
    }

    public void takeoffAll() {
        repository.requestTakeoffAll();
    }

    public void landAll() {
        repository.requestLandAll();
    }

    public void startTriangleFormation() {
        repository.startFormation(FormationController.FormationType.TRIANGLE);
    }

    public void stopFormation() {
        repository.stopFormation();
    }

    public void exportLog() {
        try {
            exportedLogUri.setValue(LogExporter.exportAllLogsAsZip(getApplication()));
        } catch (IOException e) {
            Logger.e("ViewModel", "Log匯出失敗: " + e.getMessage());
        }
    }

    public LiveData<Map<String, DroneState>> getFleetState() {
        return fleetState;
    }

    public LiveData<Map<String, Boolean>> getStaleFlags() {
        return staleFlags;
    }

    public LiveData<String> getConnectionStatusText() {
        return connectionStatusText;
    }

    public LiveData<Uri> getExportedLogUri() {
        return exportedLogUri;
    }

    @Override
    protected void onCleared() {
        disposables.clear();
        repository.unbind(getApplication());
        super.onCleared();
    }
}