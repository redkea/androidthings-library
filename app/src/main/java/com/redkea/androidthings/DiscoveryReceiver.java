package com.redkea.androidthings;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/*
        redkea library for Android Things
        Copyright 2017 redkea

        This file is part of the redkea library for Android Things.

    The redkea library for Android Things is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    The redkea library for Android Things is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with the redkea library for Android Things.  If not, see <http://www.gnu.org/licenses/>.
*/


class DiscoveryReceiver {
    private static final byte[] DISCOVERY_REQUEST = "REDKEA_DISCOVERY_REQ".getBytes();
    private static final byte[] DISCOVERY_REPLY = "REDKEA_DISCOVERY_REP".getBytes();
    private static String TAG = "REDKEA";
    private static int DISCOVERY_PORT = 20555;
    private byte[] receiveBuffer = new byte[DISCOVERY_REQUEST.length];
    private byte[] sendBuffer;

    private DatagramSocket socket;
    private HandlerThread handlerThread;
    private Handler handler;
    private Runnable broadcastRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(packet);
                if (Arrays.equals(packet.getData(), DISCOVERY_REQUEST)) {
                    DatagramPacket reply = new DatagramPacket(sendBuffer, sendBuffer.length, packet.getSocketAddress());
                    socket.send(reply);
                } else {
                    Log.w(TAG, "Received invalid discovery request");
                }

                handler.post(this);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    DiscoveryReceiver(String deviceID) throws UnknownHostException, SocketException {
        this.handlerThread = new HandlerThread("redkeaDiscovery");
        this.handlerThread.start();
        this.handler = new Handler(this.handlerThread.getLooper());

        ByteBuffer buffer = ByteBuffer.allocate(DISCOVERY_REPLY.length + deviceID.length());
        buffer.put(DISCOVERY_REPLY);
        buffer.put(deviceID.getBytes());
        this.sendBuffer = buffer.array();
        this.socket = new DatagramSocket(DISCOVERY_PORT);
    }

    void start() {
        this.handler.post(broadcastRunnable);
    }
}
