package com.raita.dronegcs.connection;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.concurrent.TimeUnit;

import android.content.Context;

import com.raita.dronegcs.debug.Logger;

public class HeartbeatWatchdog {

    private static final long TIMEOUT_MS = 3000;
    private Disposable watchdog;
    private final Context context;

    public HeartbeatWatchdog(Context context) {
        this.context = context;
    }

    public void start(Observable<Long> heartbeatTimestamps, Runnable onTimeout) {
        stop();
        watchdog = heartbeatTimestamps
                .timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .subscribe(
                        ts -> {
                        },
                        error -> {
                            Logger.e("Heartbeat", "心跳逾時，判定斷線");
                            onTimeout.run();
                        }
                );
    }

    public void stop() {
        if (watchdog != null && !watchdog.isDisposed()) watchdog.dispose();
    }
}