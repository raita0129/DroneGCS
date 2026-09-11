package com.raita.dronegcs.debug;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LogExporter {

    public static Uri exportAllLogsAsZip(Context context) throws IOException {
        Logger.flush();
        File logDir = Logger.getLogDirectory();
        File zipFile = new File(context.getCacheDir(), "gcs_logs_export.zip");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            File[] logs = logDir.listFiles();
            if (logs != null) {
                for (File log : logs) {
                    try (FileInputStream fis = new FileInputStream(log)) {
                        zos.putNextEntry(new ZipEntry(log.getName()));
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = fis.read(buffer)) > 0) zos.write(buffer, 0, len);
                        zos.closeEntry();
                    }
                }
            }
        }
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", zipFile);
    }

    public static void shareLog(Context context, Uri fileUri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "匯出除錯Log"));
    }
}