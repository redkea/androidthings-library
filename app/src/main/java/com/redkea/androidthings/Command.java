package com.redkea.androidthings;

import com.google.common.base.Charsets;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
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


class Command {
    static final BiMap<CommandType, Byte> commandTypeMap;
    private static final BiMap<Class, Byte> paramTypeMap;
    private static final int HEADER_SIZE = 3;
    private static final Pools.SynchronizedPool<Command> pool = new Pools.SynchronizedPool<>(10);

    static {
        commandTypeMap = HashBiMap.create();

        commandTypeMap.put(CommandType.WRITE_TO_DIGITAL_PIN, (byte) 0);
        commandTypeMap.put(CommandType.WRITE_TO_ANALOG_PIN, (byte) 1);
        commandTypeMap.put(CommandType.WRITE_TO_FUNCTION, (byte) 2);
        commandTypeMap.put(CommandType.WELCOME, (byte) 3);
        commandTypeMap.put(CommandType.SETUP_TIMERS, (byte) 4);
        commandTypeMap.put(CommandType.PING, (byte) 5);

        commandTypeMap.put(CommandType.READ_FROM_DIGITAL_PIN, (byte) 100);
        commandTypeMap.put(CommandType.READ_FROM_ANALOG_PIN, (byte) 101);
        commandTypeMap.put(CommandType.READ_FROM_FUNCTION, (byte) 102);

        commandTypeMap.put(CommandType.DATA_SEND, (byte) 200);
        commandTypeMap.put(CommandType.PONG, (byte) 201);

        paramTypeMap = HashBiMap.create();
        paramTypeMap.put(Byte.class, (byte) 0);
        paramTypeMap.put(Short.class, (byte) 1);
        paramTypeMap.put(Boolean.class, (byte) 2);
        paramTypeMap.put(Float.class, (byte) 3);
        paramTypeMap.put(String.class, (byte) 4);
    }

    private CommandType commandType;
    private List<Object> params = new ArrayList<>();
    private short paramSize = 0;
    private ByteBuffer headerBuffer = ByteBuffer.allocate(HEADER_SIZE);
    private ByteBuffer buffer = ByteBuffer.allocate(4096);
    private CharsetDecoder charsetDecoder = Charsets.UTF_8.newDecoder();
    private CharBuffer stringOutputBuffer = CharBuffer.allocate(4096);

    private Command() {
        this.commandType = CommandType.UNDEFINED;
        headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    static Command obtain() {
        Command instance = pool.acquire();
        return (instance != null) ? instance : new Command();
    }

    void recycle() {
        reset();
        pool.release(this);
    }

    CommandType getCommandType() {
        return this.commandType;
    }

    void setCommandType(CommandType commandType) {
        this.commandType = commandType;
    }

    void addInt8(byte b) {
        this.params.add(new Byte(b));
        paramSize += 2;
    }

    void addInt16(short s) {
        this.params.add(new Short(s));
        paramSize += 3;
    }

    void addBool(boolean b) {
        this.params.add(new Boolean(b));
        paramSize += 2;
    }

    void addFloat(float f) {
        this.params.add(new Float(f));
        paramSize += 5;
    }

    void addString(String s) {
        this.params.add(s);
        paramSize += (3 + s.length());
    }

    public void addCommandType(CommandType commandType) {
        addInt8(commandTypeMap.get(commandType));
    }

    List<Object> getParams() {
        return this.params;
    }

    void readFrom(ReadableByteChannel rbc) throws IOException {
        int bytesRead = rbc.read(this.headerBuffer);
        if (bytesRead == -1) {
            throw new IOException("ByteChannel has been closed");
        }
        headerBuffer.flip();

        // read header
        byte commandByte = this.headerBuffer.get();
        this.commandType = commandTypeMap.inverse().get(commandByte);
        this.paramSize = this.headerBuffer.getShort();

        // read params
        if (this.paramSize > 0) {
            buffer.limit(paramSize);
            // FIXME: ensure buffer size paramSize
            bytesRead = rbc.read(this.buffer);
            if (bytesRead == -1) {
                throw new IOException("ByteChannel has been closed");
            }
            this.buffer.flip();
            while (this.buffer.hasRemaining()) {
                byte paramTypeByte = this.buffer.get();
                Class paramType = paramTypeMap.inverse().get(paramTypeByte);
                if (paramType == Byte.class) {
                    byte value = this.buffer.get();
                    addInt8(value);
                } else if (paramType == Short.class) {
                    short value = this.buffer.getShort();
                    addInt16(value);
                } else if (paramType == Boolean.class) {
                    boolean value = ((this.buffer.get() & (byte) 0x01) != 0);
                    addBool(value);
                } else if (paramType == Float.class) {
                    float value = this.buffer.getFloat();
                    addFloat(value);
                } else if (paramType == String.class) {
                    short size = this.buffer.getShort();
                    ByteBuffer stringBuffer = this.buffer.slice();
                    stringBuffer.limit(size);
                    charsetDecoder.reset();
                    stringOutputBuffer.clear();
                    charsetDecoder.decode(stringBuffer, stringOutputBuffer, true);
                    charsetDecoder.flush(stringOutputBuffer);
                    stringOutputBuffer.flip();
                    addString(stringOutputBuffer.toString());
                }
            }
        }
    }

    void writeTo(WritableByteChannel wbc) throws IOException {
        // FIXME: ensure buffer size HEADER_SIZE + this.paramSize

        // write header
        byte commandByte = commandTypeMap.get(this.commandType);
        this.buffer.put(commandByte);
        this.buffer.putShort(this.paramSize);

        // write params
        for (Object o : this.params) {
            byte paramTypeByte = paramTypeMap.get(o.getClass());
            this.buffer.put(paramTypeByte);
            if (o instanceof Byte) {
                this.buffer.put((Byte) o);
            } else if (o instanceof Short) {
                this.buffer.putShort((Short) o);
            } else if (o instanceof Boolean) {
                this.buffer.put(((Boolean) o) ? (byte) 0x01 : (byte) 0x00);
            } else if (o instanceof Float) {
                this.buffer.putFloat((Float) o);
            } else if (o instanceof String) {
                String str = (String) o;
                this.buffer.putShort((short) str.length());
                this.buffer.put(str.getBytes());
            }
        }

        this.buffer.flip();
        wbc.write(this.buffer);
    }

    private void reset() {
        this.headerBuffer.clear();
        this.buffer.clear();
        this.paramSize = 0;
        this.commandType = CommandType.UNDEFINED;
        this.params.clear();
    }
}