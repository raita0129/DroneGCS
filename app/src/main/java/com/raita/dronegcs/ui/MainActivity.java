package com.raita.dronegcs.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.osmdroid.views.MapView;
import com.raita.dronegcs.R;
import com.raita.dronegcs.core.DroneState;
import com.raita.dronegcs.debug.CrashHandler;
import com.raita.dronegcs.debug.LogExporter;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private DroneViewModel viewModel;
    private MapController mapController;
    private DroneStatusAdapter droneStatusAdapter;

    private final ActivityResultLauncher<String> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) viewModel.importConfig(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (CrashHandler.consumeLastCrashFlag(this)) {
            boolean wasCrashLoop = CrashHandler.isInCrashLoop(this);
            String message = wasCrashLoop
                    ? "警告：偵測到反覆崩潰，自動重連已暫停，請確認機隊狀態並手動重新連線"
                    : "警告：上次連線異常中斷，請確認機隊狀態後再操作";
            ((TextView) findViewById(R.id.status_text)).setText(message);

            if (wasCrashLoop) {
                findViewById(R.id.btn_acknowledge_crash).setVisibility(View.VISIBLE);
                findViewById(R.id.btn_acknowledge_crash).setOnClickListener(v -> {
                    CrashHandler.resetCrashLoopState(this);
                    findViewById(R.id.btn_acknowledge_crash).setVisibility(View.GONE);
                });
            }
        }

        MapView mapView = findViewById(R.id.map_view);
        mapController = new MapController(this, mapView);

        RecyclerView droneList = findViewById(R.id.drone_list);
        droneList.setLayoutManager(new LinearLayoutManager(this));
        droneStatusAdapter = new DroneStatusAdapter(new DroneStatusAdapter.OnDroneActionListener() {
            @Override
            public void onTakeoff(String droneId) { viewModel.takeoff(droneId); }
            @Override
            public void onLand(String droneId) { viewModel.land(droneId); }
        });
        droneList.setAdapter(droneStatusAdapter);

        viewModel = new ViewModelProvider(this).get(DroneViewModel.class);

        viewModel.getFleetState().observe(this, this::updateMap);
        viewModel.getStaleFlags().observe(this, flags -> updateMap(viewModel.getFleetState().getValue()));
        viewModel.getConnectionStatusText().observe(this, text ->
                ((TextView) findViewById(R.id.status_text)).setText(text));
        viewModel.getExportedLogUri().observe(this, uri -> {
            if (uri != null) LogExporter.shareLog(this, uri);
        });

        findViewById(R.id.btn_import_config).setOnClickListener(v -> filePickerLauncher.launch("application/json"));
        findViewById(R.id.btn_export_log).setOnClickListener(v -> viewModel.exportLog());
        findViewById(R.id.btn_takeoff_all).setOnClickListener(v -> viewModel.takeoffAll());
        findViewById(R.id.btn_land_all).setOnClickListener(v -> viewModel.landAll());
        findViewById(R.id.btn_start_formation).setOnClickListener(v -> viewModel.startTriangleFormation());
        findViewById(R.id.btn_stop_formation).setOnClickListener(v -> viewModel.stopFormation());

        viewModel.bindService();
        viewModel.connectWithSavedConfig();
    }

    private void updateMap(Map<String, DroneState> fleetState) {
        if (fleetState != null) {
            Map<String, Boolean> staleFlags = viewModel.getStaleFlags().getValue();
            mapController.updateFleet(fleetState, staleFlags);
            droneStatusAdapter.update(fleetState, staleFlags);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapController.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapController.onPause();
    }
}