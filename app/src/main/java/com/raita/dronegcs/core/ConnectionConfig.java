package com.raita.dronegcs.core;

public class ConnectionConfig {
    public enum Mode { UDP_LISTEN, UDP_CONNECT, SERIAL }

    public String droneId;
    public Mode mode = Mode.UDP_LISTEN;
    public String host;
    public int port = 14550;
    public String serialPath;
    public String companionHost = "127.0.0.1";
    public int companionPort;

    public String toMavsdkUrl() {
        switch (mode) {
            case UDP_LISTEN:  return "udpin://:" + port;
            case UDP_CONNECT: return "udpout://" + host + ":" + port;
            case SERIAL:      return "serial://" + serialPath + ":" + port;
            default: throw new IllegalStateException("未知連線模式");
        }
    }

    public boolean isValid() {
        if (droneId == null || droneId.isEmpty()) return false;
        if (port <= 0 || port > 65535) return false;
        if (mode == Mode.UDP_CONNECT && (host == null || host.isEmpty())) return false;
        if (mode == Mode.SERIAL && (serialPath == null || serialPath.isEmpty())) return false;
        return true;
    }
}