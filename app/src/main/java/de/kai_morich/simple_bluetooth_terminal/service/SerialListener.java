package de.kai_morich.simple_bluetooth_terminal.service;

import java.util.ArrayDeque;

public interface SerialListener {
    void onSerialConnect();

    void onSerialConnectError(Exception e);

    void onSerialRead(byte[] data);

    void onSerialRead(ArrayDeque<byte[]> datas);

    void onSerialIoError(Exception e);
}