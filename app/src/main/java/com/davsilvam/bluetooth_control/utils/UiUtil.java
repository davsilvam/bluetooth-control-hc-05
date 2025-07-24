package com.davsilvam.bluetooth_control.utils;

import android.content.Context;
import android.widget.Toast;

public class UiUtil {
    public static void showToast(Context context, int resId) {
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show();
    }

    public static void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }
}
