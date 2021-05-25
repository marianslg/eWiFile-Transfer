package com.example.root.ewifiletransfer;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.root.ewifiletransfer.Utils.Utils;

public class NetworkChangeReceiver extends BroadcastReceiver {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String[] data = Utils.isConectedToAP(context);
            MainActivity.setTextStatusConnectivy(data);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }
}