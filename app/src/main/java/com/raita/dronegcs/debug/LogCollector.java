package com.raita.dronegcs.debug;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class LogCollector {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final int MAX_FILES_KEPT = 10;
    private static final long FLUSH_INTERVAL_MS = 3000;

    private static LogCollector instance;
    public static synchronized LogCollector getInstance(Context context) {
        if (instance == null) instance = new LogCollector(context.getApplicationContext());
        return instance;
    }

    private final File logDir;
    private final Deque<String> pendingLines = new ArrayDeque<>();
    private final Object lock = new Object();
    private final Handler flushHandler = new Handler(Looper.getMainLooper());
    private File currentFile;
    private BufferedWriter writer;

    private LogCollector(Context context) {
        logDir = new File(context.getCacheDir(), "logs");
        if (!logDir.exists()) logDir.mkdirs();
        rotateFile();
        scheduleFlush();
    }

    public void info(String tag, String msg)  { log("INFO", tag, msg); }
    public void warn(String tag, String msg)  { log("WARN", tag, msg); }
    public void error(String tag, String msg) { log("ERROR", tag, msg); }

    private void log(String level, String tag, String msg) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.TAIWAN).format(new Date());
        String line = String.format("%s,%s,%s,%s", time, level, tag, msg.replace(",", ";"));
        synchronized (lock) { pendingLines.addLast(line); }
        Log.println(level.equals("ERROR") ? Log.ERROR : level.equals("WARN") ? Log.WARN : Log.INFO, tag, msg);
    }

    private void scheduleFlush() {
        flushHandler.postDelayed(() -> { flushToDisk(); scheduleFlush(); }, FLUSH_INTERVAL_MS);
    }

    private void flushToDisk() {
        List<String> toWrite;
        synchronized (lock) {
            if (pendingLines.isEmpty()) return;
            toWrite = new ArrayList<>(pendingLines);
            pendingLines.clear();
        }
        try {
            if (currentFile.length() > MAX_FILE_SIZE) rotateFile();
            for (String line : toWrite) { writer.write(line); writer.newLine(); }
            writer.flush();
        } catch (IOException e) {
            Log.e("LogCollector", "寫入log檔案失敗", e);
        }
    }

    private void rotateFile() {
        try {
            if (writer != null) writer.close();
            currentFile = new File(logDir, "gcs_" + System.currentTimeMillis() + ".csv");
            writer = new BufferedWriter(new FileWriter(currentFile, true));
            cleanupOldFiles();
        } catch (IOException e) {
            Log.e("LogCollector", "建立新log檔案失敗", e);
        }
    }

    private void cleanupOldFiles() {
        File[] files = logDir.listFiles();
        if (files == null || files.length <= MAX_FILES_KEPT) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - MAX_FILES_KEPT; i++) files[i].delete();
    }

    public void forceFlush() { flushToDisk(); }
    public File getLogDirectory() { return logDir; }
}