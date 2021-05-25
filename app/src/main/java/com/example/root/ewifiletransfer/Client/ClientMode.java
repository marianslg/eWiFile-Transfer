package com.example.root.ewifiletransfer.Client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import com.example.root.ewifiletransfer.Finish;
import com.example.root.ewifiletransfer.R;
import com.example.root.ewifiletransfer.Stats;
import com.example.root.ewifiletransfer.Utils.UtilsSockets;
import com.example.root.ewifiletransfer.databinding.StatstransferBinding;

import java.util.ArrayList;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class ClientMode extends Activity {
    private String serverIp, clientIp, hashXML;
    private int serverPort, sizeXml;

    private SecretKey secretKey;

    private Stats stats = null;

    private ClientService mService;
    private boolean mBound = false;
    private Intent intent;

    public ServiceConnection mConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StatstransferBinding statstransferBinding = DataBindingUtil.setContentView(this, R.layout.statstransfer);

        stats = new Stats();

        statstransferBinding.setStat(stats);

        serverIp = getIntent().getStringExtra("serverIp");
        serverPort = Integer.parseInt(getIntent().getStringExtra("serverPort"));
        clientIp = getIntent().getStringExtra("clientIp");
        sizeXml = Integer.parseInt(getIntent().getStringExtra("sizeXml"));
        hashXML = getIntent().getStringExtra("hashXML");

        byte[] encodedKey = UtilsSockets.stringToByteBASE64(getIntent().getStringExtra("key"));

        secretKey = new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES");

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                // We've bound to LocalService, cast the IBinder and get LocalService instance
                try {
                    ClientService.LocalBinder binder = (ClientService.LocalBinder) service;
                    mService = binder.getService();
                    mBound = true;

                    mService.setVarClient(serverIp, clientIp, serverPort, stats, secretKey, delegate, sizeXml, hashXML);

                    startService(intent);

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName arg0) {
                mBound = false;
            }
        };
    }

    private ClientService.AsyncResponse delegate = new ClientService.AsyncResponse() {
        @Override
        public void transferFinish(ArrayList<String> _return) {
            try {
                Intent intent = new Intent(getApplicationContext(), Finish.class);

                intent.putStringArrayListExtra("return", _return);
                intent.putExtra("textPathDownload", "SI");

                finish();

                startActivity(intent);
            } catch (Exception ex) {
                Log.e("transferFinish", ex.getMessage());
            }
        }

        /*@Override
        public void serverNoRespond() {
            Log.e("Entrorrr", "si");
            buildAndShowAlertDialog("SALIR", "El dispositivo que envía los archivos hace 5 segundos que no responde: ¿desea cancelar la transferencia?", ClientMode.super.getBaseContext());
        }*/
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            /*Utils.showMessageWithOption(this,
                    "¡CUIDADO!",
                    "¿Desea salir de la aplciación? Si lo hace se cancelará la transferencia actual.");*/
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    public void cancelTransfer(View view) {
        buildAndShowAlertDialog("¡CUDIADO!", "¿Está seguro que desea cancelar la tranferencia?", this);
    }

    public void buildAndShowAlertDialog(String tittle, String msg, Context apContext) {
        try {
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            ClientMode.this.finish();
                            mService.finishTransfer("NO");
                            break;
                    }
                }
            };

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(tittle).setMessage(msg)
                    .setPositiveButton("Sí", dialogClickListener)
                    .setNegativeButton("No", dialogClickListener)
                    .show();
        } catch (Exception ex) {
            Log.e("EXXX", ex.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBound) {
            unbindService(mConnection);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        intent = new Intent(this, ClientService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }
}
