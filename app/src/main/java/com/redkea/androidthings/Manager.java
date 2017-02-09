package com.redkea.androidthings;

import java.io.IOException;
import java.util.List;

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


public class Manager {
    private DiscoveryReceiver discoveryReceiver;
    private Dispatcher dispatcher;
    private Timers timers;
    private Network network;

    public Manager(String deviceID) throws IOException {
        this.discoveryReceiver = new DiscoveryReceiver(deviceID);
        this.dispatcher = new Dispatcher(this);
        this.network = new Network(this);
        this.timers = new Timers(this.network);
    }

    public void start() {
        this.discoveryReceiver.start();
        new Thread(this.network).start();
    }

    public void registerReceiver(String key, ReceiveFunction receiveFunction) {
        this.dispatcher.registerReceiver(key, receiveFunction);
    }

    public void unregisterReceiver(String key) {
        this.dispatcher.unregisterReceiver(key);
    }

    public void registerSender(String key, SendFunction sendFunction) {
        this.timers.registerSender(key, sendFunction);
    }

    public void unregisterSender(String key) {
        this.timers.unregisterSender(key);
    }

    public void setConnectionEventListener(ConnectionEventListener listener) {
        this.network.setConnectionEventListener(listener);
    }

    void setupTimers(List<Object> params) throws IOException {
        this.timers.setup(params);
    }

    void dispatch(Command command) throws IOException {
        this.dispatcher.dispatch(command);
    }

    void disconnect() {
        this.dispatcher.reset();
        this.timers.closeAllPins();
    }
}
