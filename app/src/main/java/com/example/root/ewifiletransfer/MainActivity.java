package com.example.root.ewifiletransfer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.root.ewifiletransfer.Client.ClientMode;
import com.example.root.ewifiletransfer.Server.ServerModeSelect;
import com.example.root.ewifiletransfer.Utils.Utils;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {
    private long freeStorage = 0;
    private BroadcastReceiver mNetworkReceiver;

    @SuppressLint("StaticFieldLeak")
    private static TextView textStatusWiFiValue, textStatusWiFiValueSSID;
    @SuppressLint("StaticFieldLeak")
    private static Button butReceiveFiles;

    private IntentIntegrator integrator = null;

    private Intent intent;

    private static String clientIP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textStatusWiFiValueSSID = findViewById(R.id.textStatusWiFiValueSSID);
        textStatusWiFiValue = findViewById(R.id.textStatusWiFiValue);

        butReceiveFiles = findViewById(R.id.butReceiveFiles);

        setTextStatusConnectivy(null); // Seteamos, por defecto, que no está conectado a ninguna red.

        try {
            mNetworkReceiver = new NetworkChangeReceiver();
            registerReceiver(mNetworkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)); // Registro el receiver para que me avise cuando cambia el estado de la conexión WiFi
        } catch (Exception ignored) {
        }

        // GET EL MAXIMO BUFFER PARA ENVIAR Y RECIBIR??!?!w??"w
        //   FileInputStream fileIn=openFileInput("/proc/sys/net/core/rmem_max");

        setTextFreeStorage();

        setTextHowSendFiles();

        intent = getIntent();

        verifyStoragePermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (intent != null) {
            try {
                // Ignoro si hay para enviar texto plano.
                if (!Objects.equals(intent.getType(), "text/plain")) {
                    // Leo los archivos preseleccionados e inicio el server.
                    verifyAndStartServerMode(intent);
                }
            } catch (Exception ignored) {
            }
        }

        intent = null; // Elimino los archivos preseleccionados para que cuando vuelva hacia atras no me los reconozca de nuevo.
    }

    public static void setTextStatusConnectivy(String[] dataConn) {
        if (dataConn != null) {
            textStatusWiFiValue.setText("CONECTADO");
            textStatusWiFiValue.setTextColor(Color.parseColor("#339933"));

            textStatusWiFiValueSSID.setText("a " + dataConn[0]);

            clientIP = dataConn[2];

            butReceiveFiles.setEnabled(true);
        } else {
            Log.i("null", "null");
            textStatusWiFiValue.setText("DESCONECTADO");
            textStatusWiFiValue.setTextColor(Color.parseColor("#FF0000"));

            textStatusWiFiValueSSID.setText("debes conectarte a una red WiFi para poder enviar y recibir archivos");

            butReceiveFiles.setEnabled(false); // Si el dispositivo no esta conectado a ninguna red WiFi deshabilito el botón para recibir archivos.
        }
    }

    public void setTextFreeStorage() {
        TextView textView = findViewById(R.id.textFreeStorageValue);

        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());

        freeStorage = stat.getAvailableBytes(); // Obtengo el espacio libre disponible.

        textView.setText(Utils.resumeBytes(freeStorage));
    }

    public void setTextHowSendFiles() {
        final Context context = this;

        TextView textView = findViewById(R.id.textHowSendFiles);

        textView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Utils.showMessage(context, "", "Para poder enviar archivos:\n" +
                        "  1. Dirígite a cualquier gestor de archivos o galería de fotos/videos de tu telefono.\n" +
                        "  2. Selecciona los archivos que deseas enviar y ve a compartir.\n" +
                        "  3. Elige la opción de compartir vía eWiFile Transfer.\n" +
                        "  4. Finalmente, se abrirá la aplicación con los archivos seleccionados listos para ser enviados!.", false);
            }
        });
    }

    public void verifyStoragePermissions() {
        int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

        ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            // 1 = REQUEST_EXTERNAL_STORAGE
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Utils.showMessage(this, "¡ATENCIÓN!", "Para descargar archivos es necesario que permita almacenar archivos en el dispositivo.", false);
                }
            }
        }
    }

    public void verifyAndStartServerMode(Intent intent) {
        ArrayList<Uri> urisFilesToSend = null;

        // Verifico si tengo archivos a enviar.
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            urisFilesToSend = new ArrayList<>();
            urisFilesToSend.add((Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM)); // Extraigo la URI del Intent - Solo para 1 archivo
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            urisFilesToSend = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM); // Extraigo las URI del Intent
        }

        // Si tengo archivos para enviar, inicio el modo servidor.
        if (urisFilesToSend != null) {
            ArrayList<String> filesToSend = getRealPathFromURI(urisFilesToSend);

            Intent intentServerMode = new Intent(this, ServerModeSelect.class);

            intentServerMode.putStringArrayListExtra("filesToSend", filesToSend);

            startActivity(intentServerMode);
        }
    }

    public ArrayList<String> getRealPathFromURI(ArrayList<Uri> urisFilesToSend) {
        ArrayList<String> filesToSend = new ArrayList<>();

        for (Uri uri : urisFilesToSend) {
            if (uri.getPath().contains("/storage/emulated/")) {
                filesToSend.add(uri.getPath());
            } else {
                Cursor cursor = null;

                try {
                    cursor = getContentResolver().query(uri, new String[]{MediaStore.Images.Media.DATA}, null, null, null);
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    cursor.moveToFirst();

                    filesToSend.add(cursor.getString(column_index));
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        }

        return filesToSend;
    }

    public void runClientMode(View view) {
        // Chequeo que tengo permisos para leer y escribir archivos en el teléfono.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            if (integrator == null) {
                // Creo el lector QR.
                integrator = new IntentIntegrator(this);
                integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
                integrator.setPrompt("Escanea el codigo QR para recibir los archivos.");
                integrator.setCameraId(0);
                integrator.setBeepEnabled(false);
                integrator.setBarcodeImageEnabled(false);
                //integrator.setOrientationLocked(false);
            }

            integrator.initiateScan(); // Inicio el lector QR.
        } else {
            verifyStoragePermissions();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        // Resultado del escaneo del código QR.
        if (result != null) {
            if (result.getContents() != null) {
                decodeCapturedQR(result.getContents());
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void decodeCapturedQR(String xmlText) {
        String ssidAP = null, macAP = null, serverIp = null, key = null, hashXML = null;
        long totalSize = 0;
        int sizeXML = 0, serverPort = 0;

        Document doc = xmlTextToDocument(xmlText);

        if (doc != null) { // Verificar si contenido del código QR es texto XML.
            try { // Verificar si contenido del XML pertenece a eWiFile Transfer.
                ssidAP = doc.getElementsByTagName("ssid").item(0).getTextContent();
                macAP = doc.getElementsByTagName("mac").item(0).getTextContent();
                totalSize = Long.parseLong(doc.getElementsByTagName("size").item(0).getTextContent());
                sizeXML = Integer.parseInt(doc.getElementsByTagName("sizeXML").item(0).getTextContent());
                hashXML = doc.getElementsByTagName("hashXML").item(0).getTextContent();
                serverIp = doc.getElementsByTagName("ip").item(0).getTextContent();
                serverPort = Integer.parseInt(doc.getElementsByTagName("port").item(0).getTextContent());
                key = doc.getElementsByTagName("key").item(0).getTextContent();
            } catch (Exception ex) {
                Utils.showMessage(this, "¡ATENCIÓN!", "El código QR que acabas de escanear no pertenece a ninguna descarga de eWiFile Transfer.", false);
            }

            String[] data = Utils.isConectedToAP(this);

            if (data != null) { // Verificar si el dispositivo esta conectado a una red.
                if (macAP != null) {
                    if (macAP.equals(data[1])) { // Verificar si el dispositivo esta conectado a la misma red que su par.
                        if ((totalSize + (long) sizeXML) < freeStorage) { // Verificar si el dispositivo tiene espacio sufuciente para recibir los archivos.
                            Intent intent = new Intent(this, ClientMode.class);

                            intent.putExtra("serverIp", serverIp);
                            intent.putExtra("clientIp", clientIP);
                            intent.putExtra("sizeXml", String.valueOf(sizeXML));
                            intent.putExtra("hashXML", hashXML);
                            intent.putExtra("serverPort", String.valueOf(serverPort));
                            intent.putExtra("key", key);

                            startActivity(intent);
                        } else {
                            Utils.showMessage(this, "¡ESPACIO INSUFICIENTE!", "La descarga tiene un total de " + Utils.resumeBytes(totalSize) + " y usted posee sólo " + Utils.resumeBytes(freeStorage) + ". Por favor libere espacio e inténtelo de nuevo.", false);
                        }
                    } else {
                        Utils.showMessage(this, "¡ATENCIÓN!", "Para recibir los archivos debes conectarte a la red '" + ssidAP + "'.", false);
                    }
                }
            } else {
                Utils.showMessage(this, "¡ATENCIÓN!", "Su dispositivo no está conectado a ninguna red. Para recibir los archivos debe conectarse a '" + ssidAP + "'.", false);
            }
        } else {
            Utils.showMessage(this, "¡ATENCIÓN!", "El código QR que acabas de escanear no pertenece a ninguna descarga de eWiFile Transfer.", false);
        }
    }

    public static Document xmlTextToDocument(String xmlText) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();

            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            Document doc = dBuilder.parse(new ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8)));

            doc.getDocumentElement().normalize();

            return doc;
        } catch (Exception ex) {
            /* El código QR no contiene lenguaje XML */
            return null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        //* Cancelo registro de Receiver.
        try {
            unregisterReceiver(mNetworkReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        finish();
        System.exit(0);
    }
}