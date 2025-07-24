package de.kai_morich.simple_bluetooth_terminal.utils;

import de.kai_morich.simple_bluetooth_terminal.BuildConfig;

public class Constants {
    public static final String INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect";
    public static final String NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID + ".Channel";
    public static final String INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".MainActivity";

    public static final int NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001;

    private Constants() {

    }
}
