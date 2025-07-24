package com.davsilvam.bluetooth_control.service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.davsilvam.bluetooth_control.bluetooth.SerialSocket;

import java.io.IOException;
import java.util.ArrayDeque;

public class SerialService extends Service implements SerialListener {

    public class SerialBinder extends Binder {
        public SerialService getService() {
            return SerialService.this;
        }
    }

    private enum QueueType {Connect, ConnectError, Read, IoError}

    private static class QueueItem {
        QueueType type;
        ArrayDeque<byte[]> datas;
        Exception e;

        QueueItem(QueueType type, ArrayDeque<byte[]> datas, Exception e) {
            this.type = type;
            this.datas = datas;
            this.e = e;
        }
    }

    private final Handler mainLooper;
    private final IBinder binder;
    private final ArrayDeque<QueueItem> queue1, queue2;

    private SerialListener listener;
    private SerialSocket socket;
    private boolean connected;

    public SerialService() {
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SerialBinder();
        queue1 = new ArrayDeque<>();
        queue2 = new ArrayDeque<>();
    }

    @Override
    public void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void connect(SerialSocket socket) throws IOException {
        socket.connect(this);
        this.socket = socket;
        connected = true;
    }

    public void disconnect() {
        connected = false;
        if (socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if (!connected)
            throw new IOException("not connected");
        socket.write(data);
    }

    public void attach(SerialListener listener) {
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");

        synchronized (this) {
            this.listener = listener;
        }
        for (QueueItem item : queue1) {
            switch (item.type) {
                case Connect:
                    listener.onSerialConnect();
                    break;
                case ConnectError:
                    listener.onSerialConnectError(item.e);
                    break;
                case Read:
                    listener.onSerialRead(item.datas);
                    break;
                case IoError:
                    listener.onSerialIoError(item.e);
                    break;
            }
        }
        for (QueueItem item : queue2) {
            switch (item.type) {
                case Connect:
                    listener.onSerialConnect();
                    break;
                case ConnectError:
                    listener.onSerialConnectError(item.e);
                    break;
                case Read:
                    listener.onSerialRead(item.datas);
                    break;
                case IoError:
                    listener.onSerialIoError(item.e);
                    break;
            }
        }
        queue1.clear();
        queue2.clear();
    }

    public void detach() {
        listener = null;
    }

    private void queue(QueueType type, ArrayDeque<byte[]> datas, Exception e) {
        synchronized (this) {
            if (listener != null) {
                mainLooper.post(() -> {
                    if (listener != null) {
                        switch (type) {
                            case Connect:
                                listener.onSerialConnect();
                                break;
                            case ConnectError:
                                listener.onSerialConnectError(e);
                                break;
                            case Read:
                                listener.onSerialRead(datas);
                                break;
                            case IoError:
                                listener.onSerialIoError(e);
                                break;
                        }
                    } else {
                        queue1.add(new QueueItem(type, datas, e));
                    }
                });
            } else {
                queue2.add(new QueueItem(type, datas, e));
            }
        }
    }

    @Override
    public void onSerialConnect() {
        if (connected) {
            queue(QueueType.Connect, null, null);
        }
    }

    @Override
    public void onSerialConnectError(Exception e) {
        if (connected) {
            queue(QueueType.ConnectError, null, e);
            disconnect();
        }
    }

    @Override
    public void onSerialRead(byte[] data) {
        if (connected) {
            ArrayDeque<byte[]> datas = new ArrayDeque<>();
            datas.add(data);
            queue(QueueType.Read, datas, null);
        }
    }

    @Override
    public void onSerialRead(ArrayDeque<byte[]> datas) {
        if (connected) {
            queue(QueueType.Read, datas, null);
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        if (connected) {
            queue(QueueType.IoError, null, e);
            disconnect();
        }
    }

    public BluetoothDevice getDevice(String address) {
        return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
    }

    public boolean isConnected() {
        return connected;
    }
}