package com.redkea.androidthings;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Iterator;
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

class Network implements Runnable {
    private static String TAG = "REDKEA";

    private static int connectionPort = 5050;
    private final List<Command> commandList = new ArrayList<>();
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private Handler connectHandler = new Handler(Looper.getMainLooper());
    private Manager manager;
    private ConnectionEventListener listener = null;

    Network(Manager manager) throws IOException {
        this.manager = manager;

        this.selector = SelectorProvider.provider().openSelector();

        this.serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);

        serverChannel.socket().bind(new InetSocketAddress(connectionPort));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    void setConnectionEventListener(ConnectionEventListener listener) {
        this.listener = listener;
    }

    void sendCommand(Command command) {
        synchronized (this.commandList) {
            this.commandList.add(command);
        }
        this.selector.wakeup();
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                this.selector.select();

                determineWritableChannels();

                Iterator keys = this.selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = (SelectionKey) keys.next();
                    keys.remove();
                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        accept(key);
                        callOnConnect();
                    } else if (key.isReadable()) {
                        read(key);
                    } else if (key.isWritable()) {
                        write(key);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void accept(SelectionKey key) throws IOException {
        // For an accept to be pending the channel must be a server socket channel.
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        // Accept the connection and make it non-blocking
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);

        // Register the new SocketChannel with our Selector, indicating
        // we'd like to be notified when there's data waiting to be read
        socketChannel.register(this.selector, SelectionKey.OP_READ);
    }

    private void determineWritableChannels() {
        for (SelectionKey k : this.selector.keys()) {
            if (!k.isValid() || k.channel() == serverChannel) {
                continue;
            }
            synchronized (this.commandList) {
                if (!this.commandList.isEmpty()) {
                    k.interestOps(SelectionKey.OP_WRITE);
                }
            }
        }
    }

    private void write(SelectionKey key) throws IOException {
        synchronized (this.commandList) {
            Iterator<Command> it = this.commandList.iterator();
            while (it.hasNext()) {
                Command command = it.next();
                command.writeTo((WritableByteChannel) key.channel());
                it.remove();
                command.recycle();
            }
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    private void read(SelectionKey key) throws IOException {
        Command command = Command.obtain();
        try {
            command.readFrom((SocketChannel) key.channel());
            if (command.getCommandType() == CommandType.PING) {
                command.setCommandType(CommandType.PONG);
                command.writeTo((WritableByteChannel) key.channel());
                command.recycle();
            } else {
                manager.dispatch(command);
            }
        } catch (IOException e) {
            // client closed connection; that's ok
            key.cancel();
            callOnDisconnect();
        }
    }

    private void callOnConnect() {
        if (this.listener != null) {
            this.connectHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onConnect();
                }
            });
        }
    }

    private void callOnDisconnect() {
        if (this.listener != null) {
            this.connectHandler.post(new Runnable() {
                @Override
                public void run() {
                    manager.disconnect();
                    listener.onDisconnect();
                }
            });
        }
    }
}
