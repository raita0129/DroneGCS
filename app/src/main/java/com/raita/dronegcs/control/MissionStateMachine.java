package com.raita.dronegcs.control;

import io.mavsdk.System;
import android.content.Context;
import com.raita.dronegcs.debug.Logger;
import java.util.function.Consumer;

public class MissionStateMachine {

    public enum Phase { IDLE, CONNECTING, CONNECTED, ARMING, ARMED, TAKING_OFF, FLYING, LANDING, LANDED }

    private final String droneId;
    private Phase currentPhase = Phase.IDLE;
    private final Context context;

    public MissionStateMachine(String droneId, Context context) {
        this.droneId = droneId;
        this.context = context;
    }

    private void setPhase(Phase phase) {
        Logger.i("StateMachine", droneId + ": " + currentPhase + " -> " + phase);
        currentPhase = phase;
    }

    public Phase getCurrentPhase() { return currentPhase; }

    public void requestArm(System drone, Consumer<Phase> onChange) {
        if (currentPhase != Phase.CONNECTED) {
            Logger.w("StateMachine", droneId + " 拒絕arm請求，目前狀態: " + currentPhase);
            return;
        }
        setPhase(Phase.ARMING);
        drone.getAction().arm().subscribe(
                () -> { setPhase(Phase.ARMED); onChange.accept(Phase.ARMED); },
                err -> { setPhase(Phase.CONNECTED); onChange.accept(Phase.CONNECTED); }
        );
    }

    public void requestTakeoff(System drone, Consumer<Phase> onChange) {
        if (currentPhase != Phase.ARMED) {
            Logger.w("StateMachine", droneId + " 拒絕takeoff請求，目前狀態: " + currentPhase);
            return;
        }
        setPhase(Phase.TAKING_OFF);
        drone.getAction().takeoff().subscribe(
                () -> { setPhase(Phase.FLYING); onChange.accept(Phase.FLYING); },
                err -> { setPhase(Phase.ARMED); onChange.accept(Phase.ARMED); }
        );
    }

    public void requestLand(System drone, Consumer<Phase> onChange) {
        if (currentPhase != Phase.FLYING) return;
        setPhase(Phase.LANDING);
        drone.getAction().land().subscribe(
                () -> { setPhase(Phase.LANDED); onChange.accept(Phase.LANDED); },
                err -> onChange.accept(currentPhase)
        );
    }
}