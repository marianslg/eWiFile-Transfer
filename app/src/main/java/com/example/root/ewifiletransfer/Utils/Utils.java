package com.example.root.ewifiletransfer.Utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;


import java.text.DecimalFormat;

public final class Utils {
    /* CONNECTION UTILS */
    public static String[] isConectedToAP(Context context) {
        /*
         * return
         * null: el dispositivo no está conectado a ningún punto de acceso.
         * dataConn[0]: nombre de la red.
         * dataConn[1]: MAC del punto de acceso.
         * dataConn[2]: IP del dispositivo.
         */

        String dataConn[] = null;

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            if (activeNetwork != null) {
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                    dataConn = new String[3];

                    WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                    if (wm != null) {
                        dataConn[0] = wm.getConnectionInfo().getSSID();
                        dataConn[1] = wm.getConnectionInfo().getBSSID();
                        int ipAddress = wm.getConnectionInfo().getIpAddress();

                        // getIpAddress() me da como resultado la dirección IP expresada como un número decimal.
                        // Por lo tanto debo obtener el valor de cada octeto y expresarlo en decimal.

                        dataConn[2] = (ipAddress & 0xff) + "." + (ipAddress >> 8 & 0xff) + "." + (ipAddress >> 16 & 0xff) + "." + (ipAddress >> 24 & 0xff);
                    }
                }
            }
        }

        return dataConn;
    }

    public static String resumeBytes(long bytes) {
        DecimalFormat df = new DecimalFormat("#.00");
        double value;

        if (bytes > 0 && bytes < 1048576) {
            value = (double) bytes / (double) 1024;
            return df.format(value) + " KB";
        } else if (bytes >= 1048576 && bytes < 1073741824) {
            value = (double) bytes / (double) 1048576;
            return df.format(value) + " MB";
        } else if (bytes >= 1073741824 && bytes < 1099511600000L) {
            value = (double) bytes / (double) 1073741824;
            return df.format(value) + " GB";
        } else {
            return "0 KB";
        }
    }

    /* ALERTS UTILS */
    public static void showMessage(final Context context, String title, String message, final boolean exit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);

        String positiveText = context.getString(android.R.string.ok);

        builder.setPositiveButton(positiveText,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (exit) {
                            ((Activity) context).finish();
                        }
                    }
                });

        AlertDialog dialog = builder.create();

        dialog.show();
    }

    public static void showMessageWithOption(final Context context, String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);

        builder.setPositiveButton("Sí", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                ((Activity) context).finish();
            }
        });

        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });

        AlertDialog dialog = builder.create();

        dialog.show();
    }
}
