/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.myproject;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.redkea.androidthings.ConnectionEventListener;
import com.redkea.androidthings.Manager;
import com.redkea.androidthings.ParameterParser;
import com.redkea.androidthings.ReceiveFunction;
import com.redkea.androidthings.SendFunction;
import com.redkea.androidthings.Sender;

import java.io.IOException;

/**
 * Skeleton of the main Android Things activity. Implement your device's logic
 * in this class.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 * <p>
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 */

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private final String deviceID = "device-uid-123456791";

    private Manager manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        final TextView textView = (TextView) findViewById(R.id.textView);
        textView.setText("Hello IoT!!!");

        try {
            this.manager = new Manager(deviceID);
            this.manager.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.manager.setConnectionEventListener(new ConnectionEventListener() {
            @Override
            public void onConnect() {
                textView.setText("Connected to App");
            }

            @Override
            public void onDisconnect() {
                textView.setText("Disconnected from App");
            }
        });

        this.manager.registerReceiver("onShake", new ReceiveFunction() {
            @Override
            public void onReceive(ParameterParser parser) {
                textView.setText("Shake detected");
            }
        });

        this.manager.registerReceiver("onSlide", new ReceiveFunction() {
            @Override
            public void onReceive(ParameterParser parser) {
                textView.setText("Slider value: " + String.valueOf(parser.readFromSlider()));
            }
        });

        this.manager.registerReceiver("onTouch", new ReceiveFunction() {
            @Override
            public void onReceive(ParameterParser parser) {
                textView.setText("Touch value: " + String.valueOf(parser.readFromTouch()));
            }
        });

        this.manager.registerSender("onTextUpdate", new SendFunction() {
            int count = 0;

            @Override
            public void onSend(Sender sender) {
                sender.sendToTextOutput("From onTextUpdate " + String.valueOf(count++));
            }
        });
    }
}
