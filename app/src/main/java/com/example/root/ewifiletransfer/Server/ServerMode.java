package com.example.root.ewifiletransfer.Server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ViewFlipper;

import com.example.root.ewifiletransfer.Finish;
import com.example.root.ewifiletransfer.R;
import com.example.root.ewifiletransfer.Stats;
import com.example.root.ewifiletransfer.Utils.Utils;
import com.example.root.ewifiletransfer.Utils.UtilsSockets;
import com.example.root.ewifiletransfer.databinding.StatstransferBinding;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;

public class ServerMode extends Activity {
    private ViewFlipper viewFlipper;
    private ImageView imageView;

    private File data_files = null;
    private ServerSocket serverSocket = null;
    private ArrayList<File> finalFilesToSend;
    private Stats stats;
    private SecretKey secretKey;

    private String xmlToQR;

    private ServerService mService;
    private boolean mBound = false;
    private Intent intent;

    public ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            try {
                ServerService.LocalBinder binder = (ServerService.LocalBinder) service;
                mService = binder.getService();
                mBound = true;

                mService.setVarServer(data_files, serverSocket, finalFilesToSend, stats, secretKey, delegate);

                startService(intent);

                Bitmap bitmap = encodeQR(xmlToQR);
                imageView.setImageBitmap(bitmap); // Seteo la imagen QR a la vista.
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    private ServerService.AsyncResponse delegate = new ServerService.AsyncResponse() {
        @Override
        public void clientConnected(boolean on) {
            try {
                if (on) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            viewFlipper.setDisplayedChild(0); // Cuando el client se conecta, muestro la vista de estadisticas de descarga.
                        }
                    });
                }
            } catch (Exception ex) {
                Log.e("clientConnected()", ex.getMessage());
            }
        }

        @Override
        public void transferFinish(ArrayList<String> _return) {
            try {
                Intent intent = new Intent(getApplicationContext(), Finish.class);

                intent.putStringArrayListExtra("return", _return);
                intent.putExtra("textPathDownload", "NO");  // Para que no muestre la etiqueta donde explica donde se almacenan las descargas

                finish();

                startActivity(intent);
            } catch (Exception ex) {
                Log.e("transferFinish", ex.getMessage());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Creo estructuras de DataBinding.
        StatstransferBinding statstransferBinding = DataBindingUtil.setContentView(this, R.layout.statstransfer);

        viewFlipper = this.findViewById(R.id.viewFlipper);
        imageView = findViewById(R.id.qrCode);

        ArrayList<String> uriFilesToSend = getIntent().getStringArrayListExtra("filesToSend");

        String macAP = getIntent().getStringExtra("macAP");
        String ssidAP = getIntent().getStringExtra("ssidAP");
        String serverIP = getIntent().getStringExtra("serverIP");
        long totalBytesToSend = Long.valueOf(getIntent().getStringExtra("totalBytesToSend"));

        stats = new Stats();

        int totalFiles= uriFilesToSend.size();

        stats.set_sizeTotal(totalBytesToSend);
        stats.setTotalFiles(totalFiles);

        statstransferBinding.setStat(stats);

        viewFlipper.setDisplayedChild(1); // Muestro, en principio, la vista donde se encuentra el ćodigo QR.

        finalFilesToSend = new ArrayList<>();

        // Creo lista para creacion de QR
        for (String uri : uriFilesToSend) {
            finalFilesToSend.add(new File(uri));
        }

        String temporalXmlFile = createContentTemporalXMLFiles(finalFilesToSend);

        try {
            data_files = File.createTempFile("data_files", ".xml");

            BufferedWriter out = new BufferedWriter(new FileWriter(data_files));

            out.write(temporalXmlFile);

            out.close();
        } catch (Exception exx) {
            Utils.showMessage(this, "ERROR", "Se ha producido un error.", true);
        }

        int port = 0;

        try {
            // port 0: para que se elija un puerto aleatoriamente.
            // backlog 1: cantidad de conexiones a aceptar.
            serverSocket = new ServerSocket(0, 1);

            port = serverSocket.getLocalPort(); // Obtengo el puerto seleccionado.

            Log.i("ServerModeSelect", "Receive buffer size: " + serverSocket.getReceiveBufferSize());
        } catch (IOException e) {
            Utils.showMessage(this, "ERROR", "Se ha producido un error.", true);
        }

        if (port > 0) {
            secretKey = generateKey();

            if (secretKey == null) {
                Utils.showMessage(this, "ERROR", "Se ha producido un error.", true);
            }

            // Creo los datos XML que contendrá el el código QR.
            xmlToQR = "<data_conn><mac>" + macAP + "</mac>" +
                    "<ssid>" + ssidAP + "</ssid>" +
                    "<ip>" + serverIP + "</ip>" +
                    "<port>" + port + "</port>" +
                    "<size>" + totalBytesToSend + "</size>" +
                    "<sizeXML>" + new byte[(int) data_files.length()].length + "</sizeXML>" +
                    "<hashXML>" + UtilsSockets.getHashMD5FromFile(data_files.getAbsolutePath()) + "</hashXML>" +
                    "<key>" + UtilsSockets.byteToStringBASE64(secretKey.getEncoded()) + "</key></data_conn>";

        } else {
            Utils.showMessage(this, "¡CUIDADO!", "Error", true);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.e("onStart", "Entró");



        // Bind to LocalService
        intent = new Intent(this, ServerService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }


    private SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            return keyGen.generateKey();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String createContentTemporalXMLFiles(ArrayList<File> filesToSend) {
        StringBuilder temporalXmlFile = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><data_files>");

        for (File fileToSend : filesToSend) {

            Log.i("Path file hash:", fileToSend.getPath());
            Log.i("CPath file hash:", fileToSend.getAbsolutePath());

            temporalXmlFile.append("<file><name>").
                    append(fileToSend.getName()).
                    append("</name><size>").
                    append(new byte[(int) fileToSend.length()].length).
                    append("</size><hash>").
                    append(UtilsSockets.getHashMD5FromFile(fileToSend.getPath())).
                    append("</hash></file>");
        }

        temporalXmlFile.append("</data_files>");

        return temporalXmlFile.toString();
    }

    public Bitmap encodeQR(String contentQR) throws WriterException {
        BitMatrix result;

        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);

            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L); // Seteo el menor código de recuperación de errores.
            hints.put(EncodeHintType.MARGIN, 1);    // Seteo amrgenes de imágen QR.

            result = new MultiFormatWriter().encode(contentQR, BarcodeFormat.QR_CODE, 400, 400, hints); // Genero el código QR.
        } catch (IllegalArgumentException iae) {
            return null;
        }

        // Genero una imagen a partir del código generado.
        int width = result.getWidth();
        int height = result.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

        return bitmap;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Si el usuario presiona el botón hacia atrás y la vista es la del código QR
        if (keyCode == KeyEvent.KEYCODE_BACK && viewFlipper.getDisplayedChild() == 1) {
            Utils.showMessageWithOption(this, "¡CUIDADO!", "¿Desea salir de la aplciación? Si lo hace se cancelará la transferencia actual.");
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    public void cancelTransfer(View view) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        mService.finishTransfer("NO");
                        ServerMode.this.finish();

                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("¡CUDIADO!").setMessage("¿Está seguro que desea cancelar la tranferencia?")
                .setPositiveButton("Sí", dialogClickListener)
                .setNegativeButton("No", dialogClickListener)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBound) {
            unbindService(mConnection);
            stopService(intent);
        }
    }
}