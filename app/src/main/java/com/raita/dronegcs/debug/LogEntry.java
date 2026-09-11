package com.raita.dronegcs.debug;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogEntry {
    public final long timestamp;
    public final String level;
    public final String tag;
    public final String message;

    public LogEntry(String level, String tag, String message) {
        this.timestamp = System.currentTimeMillis();
        this.level = level;
        this.tag = tag;
        this.message = message;
    }

    public String toCsvLine() {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.TAIWAN).format(new Date(timestamp));
        return String.format("%s,%s,%s,%s", time, level, tag, message.replace(",", ";"));
    }
}