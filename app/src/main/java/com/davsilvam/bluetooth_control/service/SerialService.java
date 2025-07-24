package com.davsilvam.bluetooth_control.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import com.davsilvam.bluetooth_control.R;
import com.davsilvam.bluetooth_control.bluetooth.SerialSocket;
import com.davsilvam.bluetooth_control.utils.Constants;

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

        QueueItem(QueueType type) {
            this.type = type;
            if (type == QueueType.Read) init();
        }

        QueueItem(QueueType type, Exception e) {
            this.type = type;
            this.e = e;
        }

        QueueItem(QueueType type, ArrayDeque<byte[]> datas) {
            this.type = type;
            this.datas = datas;
        }

        void init() {
            datas = new ArrayDeque<>();
        }

        void add(byte[] data) {
            datas.add(data);
        }
    }

    private final Handler mainLooper;
    private final IBinder binder;
    private final ArrayDeque<QueueItem> queue1, queue2;
    private final QueueItem lastRead;

    private SerialSocket socket;
    private final List<SerialListener> listeners; // Alterado para uma lista
    private boolean connected;


    public SerialService() {
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SerialBinder();
        queue1 = new ArrayDeque<>();
        queue2 = new ArrayDeque<>();
        lastRead = new QueueItem(QueueType.Read);
        listeners = new ArrayList<>(); // Inicializa a lista
    }

    @Override
    public void onDestroy() {
        cancelNotification();
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
        cancelNotification();

        if (socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if (!connected) {
            throw new IOException("not connected");
        }

        socket.write(data);
    }

    public void attach(SerialListener listener) {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            throw new IllegalArgumentException("not in main thread");
        }

        initNotification();
        cancelNotification();

        synchronized (this) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
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

        // Limpa as filas apenas se for o primeiro listener a se conectar
        if (listeners.size() == 1) {
            queue1.clear();
            queue2.clear();
        }
    }

    public void detach(SerialListener listener) { // Alterado para receber o listener a ser removido
        if (connected && listeners.isEmpty()) {
            createNotification();
        }
        listeners.remove(listener);
    }

    private void initNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(Constants.NOTIFICATION_CHANNEL, "Background service", NotificationManager.IMPORTANCE_LOW);
            nc.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(nc);
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public boolean areNotificationsEnabled() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel nc = nm.getNotificationChannel(Constants.NOTIFICATION_CHANNEL);
        return nm.areNotificationsEnabled() && nc != null && nc.getImportance() > NotificationManager.IMPORTANCE_NONE;
    }

    private void createNotification() {
        Intent disconnectIntent = new Intent()
                .setPackage(getPackageName())
                .setAction(Constants.INTENT_ACTION_DISCONNECT);
        Intent restartIntent = new Intent()
                .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent disconnectPendingIntent = PendingIntent.getBroadcast(this, 1, disconnectIntent, flags);
        PendingIntent restartPendingIntent = PendingIntent.getActivity(this, 1, restartIntent, flags);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(socket != null ? "Connected to " + socket.getName() : "Background Service")
                .setContentIntent(restartPendingIntent)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action(R.drawable.ic_clear_white_24dp, "Disconnect", disconnectPendingIntent));
        Notification notification = builder.build();
        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification);
    }

    private void cancelNotification() {
        stopForeground(true);
    }

    private void postToListeners(Runnable action) {
        mainLooper.post(() -> {
            if (!listeners.isEmpty()) {
                action.run();
            }
        });
    }

    public void onSerialConnect() {
        if (connected) {
            synchronized (this) {
                if (!listeners.isEmpty()) {
                    postToListeners(() -> {
                        for (SerialListener listener : listeners) {
                            listener.onSerialConnect();
                        }
                    });
                } else {
                    queue1.add(new QueueItem(QueueType.Connect));
                }
            }
        }
    }

    public void onSerialConnectError(Exception e) {
        if (connected) {
            synchronized (this) {
                if (!listeners.isEmpty()) {
                    postToListeners(() -> {
                        for (SerialListener listener : listeners) {
                            listener.onSerialConnectError(e);
                        }
                    });
                } else {
                    queue1.add(new QueueItem(QueueType.ConnectError, e));
                    disconnect();
                }
            }
        }
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        throw new UnsupportedOperationException();
    }

    public void onSerialRead(byte[] data) {
        if (connected) {
            synchronized (this) {
                if (!listeners.isEmpty()) {
                    postToListeners(() -> {
                        ArrayDeque<byte[]> singleData = new ArrayDeque<>();
                        singleData.add(data);
                        for (SerialListener listener : listeners) {
                            listener.onSerialRead(singleData);
                        }
                    });
                } else {
                    if (queue2.isEmpty() || queue2.getLast().type != QueueType.Read)
                        queue2.add(new QueueItem(QueueType.Read));
                    queue2.getLast().add(data);
                }
            }
        }
    }

    public void onSerialIoError(Exception e) {
        if (connected) {
            synchronized (this) {
                if (!listeners.isEmpty()) {
                    postToListeners(() -> {
                        for (SerialListener listener : listeners) {
                            listener.onSerialIoError(e);
                        }
                    });
                } else {
                    queue1.add(new QueueItem(QueueType.IoError, e));
                    disconnect();
                }
            }
        }
    }

    public BluetoothDevice getDevice(String address) {
        return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
    }

    public boolean isConnected() {
        return connected;
    }
}