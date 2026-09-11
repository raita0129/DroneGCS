package com.raita.dronegcs.debug;

import android.content.Context;
import android.content.SharedPreferences;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String CRASH_PREFS = "crash_state";
    private static final String KEY_LAST_CRASH_TIME = "last_crash_time";
    private static final String KEY_HAD_CRASH = "had_unhandled_crash";
    private static final String KEY_CRASH_COUNT = "crash_count_in_window";
    private static final String KEY_WINDOW_START_TIME = "crash_window_start_time";

    private static final long CRASH_WINDOW_MS = 5 * 60 * 1000; // 5分鐘內的crash算同一個窗口
    private static final int MAX_CRASHES_IN_WINDOW = 3;         // 超過3次就判定為崩潰迴圈

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            Logger.e("CrashHandler", "App崩潰: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            for (StackTraceElement element : ex.getStackTrace()) {
                Logger.e("CrashHandler", "  at " + element.toString());
            }
            Logger.flush();

            SharedPreferences prefs = context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();

            // 崩潰迴圈判斷：在時間窗內累計次數，超過窗口就重置計數
            long windowStart = prefs.getLong(KEY_WINDOW_START_TIME, 0);
            int count = prefs.getInt(KEY_CRASH_COUNT, 0);
            if (now - windowStart > CRASH_WINDOW_MS) {
                windowStart = now;
                count = 0;
            }
            count++;

            boolean isCrashLoop = count >= MAX_CRASHES_IN_WINDOW;
            if (isCrashLoop) {
                Logger.e("CrashHandler",
                        "偵測到崩潰迴圈(" + CRASH_WINDOW_MS / 60000 + "分鐘內第" + count + "次崩潰)，停止自動重連旗標已設置");
                Logger.flush();
            }

            prefs.edit()
                    .putBoolean(KEY_HAD_CRASH, true)
                    .putLong(KEY_LAST_CRASH_TIME, now)
                    .putInt(KEY_CRASH_COUNT, count)
                    .putLong(KEY_WINDOW_START_TIME, windowStart)
                    .putBoolean("is_crash_loop", isCrashLoop)
                    .apply();
        } catch (Exception loggingFailure) {
        } finally {
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, ex);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }
    }

    public static boolean consumeLastCrashFlag(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE);
        boolean hadCrash = prefs.getBoolean(KEY_HAD_CRASH, false);
        if (hadCrash) {
            prefs.edit().putBoolean(KEY_HAD_CRASH, false).apply();
        }
        return hadCrash;
    }

    public static boolean isInCrashLoop(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean("is_crash_loop", false);
    }

    public static void resetCrashLoopState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_CRASH_COUNT, 0)
                .putBoolean("is_crash_loop", false)
                .apply();
    }
}