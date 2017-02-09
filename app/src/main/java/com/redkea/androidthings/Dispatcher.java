package com.redkea.androidthings;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.Pwm;

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


class Dispatcher {
    private static String TAG = "REDKEA";

    private Manager manager;
    private PeripheralManagerService peripheralManager;
    private List<Runnable> runnables = new ArrayList<>();
    private Handler userFunctionHandler = new Handler(Looper.getMainLooper());
    private Map<String, ReceiveFunction> receivers = new HashMap<>();
    private Map<String, Gpio> gpioMap = new HashMap<>();
    private Map<String, Pwm> pwmMap = new HashMap<>();

    Dispatcher(Manager manager) {
        this.manager = manager;
        this.peripheralManager = new PeripheralManagerService();
    }

    void dispatch(final Command command) throws IOException {
        List params = new ArrayList<>(command.getParams());
        if (command.getCommandType() == CommandType.SETUP_TIMERS) {
            this.manager.setupTimers(params);
        } else if (command.getCommandType() == CommandType.WRITE_TO_DIGITAL_PIN) {
            String target = (String) params.get(0);
            if (params.get(1) instanceof Boolean) {
                Boolean value = (Boolean) params.get(1);
                writeToGpio(target, value);
            } else if (params.get(1) instanceof Short) {
                Short value = (Short) params.get(1);
                writeToPwm(target, value);
            }
        } else if (command.getCommandType() == CommandType.WRITE_TO_FUNCTION) {
            String key = (String) params.get(0);
            if (!receivers.containsKey(key)) {
                Log.d(TAG, "No receiver for key " + key);
                return;
            }
            params.remove(0);
            callUserFunction(key, params);
        }
        command.recycle();
    }

    void registerReceiver(String key, ReceiveFunction receiveFunction) {
        this.receivers.put(key, receiveFunction);
    }

    void unregisterReceiver(String key) {
        if (this.receivers.containsKey(key)) {
            this.receivers.remove(key);
        }
    }

    void reset() {
        for (Runnable runnable : this.runnables) {
            this.userFunctionHandler.removeCallbacks(runnable);
        }
        this.runnables.clear();

        for (Gpio gpio : this.gpioMap.values()) {
            try {
                gpio.close();
            } catch (IOException e) {
                // ignore
                Log.d(TAG, "Error closing gpio: " + e.toString());
            }
        }
        this.gpioMap.clear();

        for (Pwm pwm : this.pwmMap.values()) {
            try {
                pwm.close();
            } catch (IOException e) {
                // ignore
                Log.d(TAG, "Error closing pwm: " + e.toString());
            }
        }
        this.pwmMap.clear();
    }

    private void writeToPwm(String target, Short value) throws IOException {
        Pwm pwm;
        if (!this.pwmMap.containsKey(target)) {
            pwm = this.peripheralManager.openPwm(target);
            pwm.setEnabled(false);
            pwm.setPwmFrequencyHz(500);
            pwm.setEnabled(true);
            this.pwmMap.put(target, pwm);
        } else {
            pwm = this.pwmMap.get(target);
        }
        pwm.setEnabled(true);
        pwm.setPwmDutyCycle(value);
    }

    private void writeToGpio(String target, Boolean value) throws IOException {
        Gpio gpio;
        if (!this.gpioMap.containsKey(target)) {
            gpio = this.peripheralManager.openGpio(target);
            gpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            this.gpioMap.put(target, gpio);
        } else {
            gpio = this.gpioMap.get(target);
        }
        gpio.setValue(value);
    }

    private void callUserFunction(final String key, final List<Object> params) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ReceiveFunction receiveFunction = receivers.get(key);
                receiveFunction.onReceive(new ParameterParser(params));
            }
        };
        this.runnables.add(runnable);
        this.userFunctionHandler.post(runnable);
    }
}
