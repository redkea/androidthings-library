package com.redkea.androidthings;

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

public class Sender {
    private Network network;
    private String widgetID;

    Sender(Network network, String widgetID) {
        this.network = network;
        this.widgetID = widgetID;
    }

    public void sendToTextOutput(String text) {
        Command command = Command.obtain();
        command.setCommandType(CommandType.DATA_SEND);
        command.addString(this.widgetID);
        command.addString(text);
        this.network.sendCommand(command);
    }
}
