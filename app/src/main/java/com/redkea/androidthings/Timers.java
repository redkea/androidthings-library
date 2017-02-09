package com.redkea.androidthings;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

class Timers {
    private static String TAG = "REDKEA";

    private Network network;
    private PeripheralManagerService peripheralManager;
    private Map<String, SendFunction> senders = new HashMap<>();
    private Map<String, Gpio> gpioMap = new HashMap<>();
    private List<Runnable> runnables = new ArrayList<>();
    private Handler timerHandler = new Handler(Looper.getMainLooper());

    Timers(Network network) {
        this.network = network;
        this.peripheralManager = new PeripheralManagerService();
    }

    void setup(List<Object> params) throws IOException {
        int index = 0;
        short numTimers = (short) params.get(index++);
        for (int i = 0; i < numTimers; ++i) {
            CommandType commandType = Command.commandTypeMap.inverse().get(params.get(index++));
            final String source = (String) params.get(index++);
            final String widgetID = (String) params.get(index++);
            final short interval = (short) params.get(index++);
            if (commandType == CommandType.READ_FROM_FUNCTION) {
                setupFunctionTimer(source, widgetID, interval);
            } else if (commandType == CommandType.READ_FROM_DIGITAL_PIN) {
                setupDigitalPinTimer(source, widgetID, interval);
            }
        }
    }

    void registerSender(String key, SendFunction sendFunction) {
        this.senders.put(key, sendFunction);
    }

    void unregisterSender(String key) {
        if (this.senders.containsKey(key)) {
            this.senders.remove(key);
        }
    }

    private void setupFunctionTimer(final String source, final String widgetID, final short interval) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (!senders.containsKey(source)) {
                    Log.d(TAG, "No sender registered for key " + source);
                } else {
                    senders.get(source).onSend(new Sender(network, widgetID));
                }
                timerHandler.postDelayed(this, interval);
            }
        };
        this.runnables.add(runnable);
        this.timerHandler.post(runnable);
    }

    private void setupDigitalPinTimer(final String source, final String widgetID, final short interval) throws IOException {
        Gpio gpio = this.peripheralManager.openGpio(source);
        gpio.setDirection(Gpio.DIRECTION_IN);
        this.gpioMap.put(source, gpio);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    Gpio gpio = gpioMap.get(source);
                    boolean value = gpio.getValue();
                    Command command = Command.obtain();
                    command.setCommandType(CommandType.DATA_SEND);
                    command.addString(widgetID);
                    command.addBool(value);
                    network.sendCommand(command);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                timerHandler.postDelayed(this, interval);
            }
        };
        this.runnables.add(runnable);
        this.timerHandler.post(runnable);
    }

    void closeAllPins() {
        for (Runnable runnable : this.runnables) {
            this.timerHandler.removeCallbacks(runnable);
        }
        this.runnables.clear();

        for (Gpio gpio : this.gpioMap.values()) {
            try {
                gpio.close();
            } catch (IOException e) {
                // ignore
                Log.d(TAG, "Error closing gpio: " + e.toString());
            }
            this.gpioMap.clear();
        }
    }
}

