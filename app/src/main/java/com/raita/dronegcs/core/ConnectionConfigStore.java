package com.raita.dronegcs.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class ConnectionConfigStore {

    private static final String PREFS_NAME = "connection_config";
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public ConnectionConfigStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<ConnectionConfig> importFromUri(Context context, Uri fileUri) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(fileUri);
             InputStreamReader reader = new InputStreamReader(is)) {
            List<ConnectionConfig> configs = gson.fromJson(reader,
                    new TypeToken<List<ConnectionConfig>>(){}.getType());
            if (configs == null || configs.isEmpty()) {
                throw new IOException("設定檔為空或格式不正確");
            }
            for (ConnectionConfig c : configs) {
                if (!c.isValid()) throw new IOException("設定檔含無效欄位：droneId=" + c.droneId);
            }
            saveAll(configs);
            return configs;
        }
    }

    public void saveAll(List<ConnectionConfig> configs) {
        prefs.edit().putString("configs", gson.toJson(configs)).apply();
    }

    public List<ConnectionConfig> loadAll() {
        String json = prefs.getString("configs", null);
        if (json == null) {
            ConnectionConfig def = new ConnectionConfig();
            def.droneId = "drone1";
            return Collections.singletonList(def);
        }
        List<ConnectionConfig> configs = gson.fromJson(json, new TypeToken<List<ConnectionConfig>>(){}.getType());
        return configs != null ? configs : Collections.emptyList();
    }
}