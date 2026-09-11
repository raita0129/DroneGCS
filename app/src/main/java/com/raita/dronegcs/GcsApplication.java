package com.raita.dronegcs;

import android.app.Application;

import com.raita.dronegcs.debug.CrashHandler;
import com.raita.dronegcs.debug.Logger;

public class GcsApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.init(this);
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
    }
}