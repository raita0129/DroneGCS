package com.raita.dronegcs.connection;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.schedulers.Schedulers;

import com.raita.dronegcs.debug.Logger;

public class CustomMessageChannel {

    private static final int CUSTOM_STATUS_MSG_ID = 220;
    private static final int CUSTOM_STATUS_ACK_MSG_ID = 221;

    private static final int CUSTOM_STATUS_CRC_EXTRA = 0; // TODO: 重建環境後查出實際值替換

    private static final int OUR_SYS_ID = 255;
    private static final int OUR_COMP_ID = 190;

    private final String droneId;
    private final String companionHost;
    private final int companionPort;
    private DatagramSocket socket;
    private volatile boolean listening = false;
    private int seqCounter = 0;

    private final PublishSubject<CustomStatusAck> ackSubject = PublishSubject.create();

    public CustomMessageChannel(String droneId, String companionHost, int companionPort) {
        this.droneId = droneId;
        this.companionHost = companionHost;
        this.companionPort = companionPort;
        startListening();
    }

    private void startListening() {
        Schedulers.io().scheduleDirect(() -> {
            try {
                socket = new DatagramSocket();
                listening = true;
                byte[] buffer = new byte[280];
                Logger.i("CustomMsg", droneId + " UDP channel已啟動，本地port=" + socket.getLocalPort());

                while (listening) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    handleIncoming(packet.getData(), packet.getLength());
                }
            } catch (IOException e) {
                if (listening) {
                    Logger.e("CustomMsg", droneId + " 監聽發生例外: " + e.getMessage());
                }
            }
        });
    }

    private void handleIncoming(byte[] data, int length) {
        MavlinkV2Codec.DecodedMessage decoded = MavlinkV2Codec.decode(data, length);
        if (decoded == null) return;

        if (decoded.msgId == CUSTOM_STATUS_ACK_MSG_ID && decoded.payload.length >= 2) {
            int originalCode = decoded.payload[0] & 0xFF;
            int result = decoded.payload[1] & 0xFF;
            CustomStatusAck ack = new CustomStatusAck(originalCode, result);
            Logger.i("CustomMsg", droneId + " 收到ACK: original_code=" + originalCode + " result=" + result);
            ackSubject.onNext(ack);
        }
    }

    public void sendCustomStatus(int statusCode, float customValue) {
        Schedulers.io().scheduleDirect(() -> {
            try {
                if (socket == null) {
                    Logger.w("CustomMsg", droneId + " socket尚未就緒，略過本次發送");
                    return;
                }
                ByteBuffer payloadBuf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
                payloadBuf.put((byte) statusCode);
                payloadBuf.putFloat(customValue);

                byte[] packet = MavlinkV2Codec.encode(
                        OUR_SYS_ID, OUR_COMP_ID, CUSTOM_STATUS_MSG_ID,
                        payloadBuf.array(), CUSTOM_STATUS_CRC_EXTRA, seqCounter++
                );

                InetAddress address = InetAddress.getByName(companionHost);
                DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, address, companionPort);
                socket.send(datagramPacket);

                Logger.i("CustomMsg", droneId + " 已送出CUSTOM_STATUS: code=" + statusCode + " value=" + customValue);
            } catch (IOException e) {
                Logger.e("CustomMsg", droneId + " 送出失敗: " + e.getMessage());
            }
        });
    }

    public Observable<CustomStatusAck> observeCustomStatusAck() {
        return ackSubject;
    }

    public void close() {
        listening = false;
        if (socket != null) socket.close();
    }

    public static class CustomStatusAck {
        public final int originalStatusCode;
        public final int result;

        public CustomStatusAck(int originalStatusCode, int result) {
            this.originalStatusCode = originalStatusCode;
            this.result = result;
        }
    }
}