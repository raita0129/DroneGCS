package com.raita.dronegcs.debug;

import android.content.Context;

public class Logger {

    private static LogCollector collector;

    public static void init(Context context) {
        collector = LogCollector.getInstance(context.getApplicationContext());
    }

    public static void i(String tag, String message) {
        checkInitialized();
        collector.info(tag, message);
    }

    public static void w(String tag, String message) {
        checkInitialized();
        collector.warn(tag, message);
    }

    public static void e(String tag, String message) {
        checkInitialized();
        collector.error(tag, message);
    }

    public static void flush() {
        checkInitialized();
        collector.forceFlush();
    }

    public static java.io.File getLogDirectory() {
        checkInitialized();
        return collector.getLogDirectory();
    }

    private static void checkInitialized() {
        if (collector == null) {
            throw new IllegalStateException("Logger.init(context) 尚未呼叫，請在GcsApplication.onCreate()中初始化");
        }
    }
}