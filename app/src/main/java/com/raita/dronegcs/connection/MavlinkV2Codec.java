package com.raita.dronegcs.connection;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MavlinkV2Codec {

    private static final int MAVLINK_STX = 0xFD;

    private static int crcAccumulate(byte b, int crc) {
        int tmp = (b & 0xFF) ^ (crc & 0xFF);
        tmp = (tmp ^ (tmp << 4)) & 0xFF;
        crc = ((crc >> 8) ^ (tmp << 8) ^ (tmp << 3) ^ (tmp >> 4)) & 0xFFFF;
        return crc;
    }

    public static byte[] encode(int sysId, int compId, int msgId, byte[] payload, int crcExtra, int seq) {
        int len = payload.length;
        byte[] header = new byte[] {
                (byte) MAVLINK_STX,
                (byte) len,
                0, // incompat_flags
                0, // compat_flags
                (byte) seq,
                (byte) sysId,
                (byte) compId,
                (byte) (msgId & 0xFF),
                (byte) ((msgId >> 8) & 0xFF),
                (byte) ((msgId >> 16) & 0xFF)
        };

        int crc = 0xFFFF;
        for (int i = 1; i < header.length; i++) {
            crc = crcAccumulate(header[i], crc);
        }
        for (byte b : payload) {
            crc = crcAccumulate(b, crc);
        }
        crc = crcAccumulate((byte) crcExtra, crc);

        ByteBuffer buffer = ByteBuffer.allocate(header.length + payload.length + 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(header);
        buffer.put(payload);
        buffer.putShort((short) crc);
        return buffer.array();
    }

    public static DecodedMessage decode(byte[] raw, int length) {
        if (length < 10 || (raw[0] & 0xFF) != MAVLINK_STX) return null;
        int payloadLen = raw[1] & 0xFF;
        int msgId = (raw[7] & 0xFF) | ((raw[8] & 0xFF) << 8) | ((raw[9] & 0xFF) << 16);
        byte[] payload = new byte[payloadLen];
        System.arraycopy(raw, 10, payload, 0, Math.min(payloadLen, length - 10));
        return new DecodedMessage(msgId, payload);
    }

    public static class DecodedMessage {
        public final int msgId;
        public final byte[] payload;
        public DecodedMessage(int msgId, byte[] payload) {
            this.msgId = msgId;
            this.payload = payload;
        }
    }
}