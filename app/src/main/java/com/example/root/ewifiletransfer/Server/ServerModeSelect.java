package com.example.root.ewifiletransfer.Server;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import com.example.root.ewifiletransfer.R;
import com.example.root.ewifiletransfer.Utils.Utils;

import java.io.File;
import java.util.ArrayList;

public class ServerModeSelect extends Activity {
    private ArrayList<FileToSend> filesToSend;

    private long totalBytesToSend = 0;
    private int totalFilesToSend = 0;

    private TextView textTotalFilesValue, textTotalSizeValue;

    private Button butStartServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.servermodeselect);

        butStartServer = findViewById(R.id.butStartServer);
        textTotalFilesValue = findViewById(R.id.textTotalFilesValue);
        textTotalSizeValue = findViewById(R.id.textTotalSizeValue);

        ArrayList<String> uriFilesToSend = getIntent().getStringArrayListExtra("filesToSend");

        // Creo la lista filesToSend y determino la calculo de bytes a enviar.
        filesToSend = new ArrayList<>();

        File file;
        int sizeFile;

        for (String uri : uriFilesToSend) {
            file = new File(uri);

            sizeFile = new byte[(int) file.length()].length;

            // Si el archivo no está vacío, lo sumo a las estadisticas. Más adelante se desahbilitará por seleccionar ese archivo para enviarlo.
            if (sizeFile > 0) {
                totalFilesToSend++;
                totalBytesToSend += sizeFile;
            }

            filesToSend.add(new FileToSend(file, uri, sizeFile));
        }

        // Seteo las etiquetas de las vistas.
        textTotalFilesValue.setText(String.valueOf(totalFilesToSend));
        textTotalSizeValue.setText(Utils.resumeBytes(totalBytesToSend));

        // Seteo el botón para iniciar el server si cumple con las condiciones.
        setEnabledButton();

        ListView myListView = findViewById(R.id.listFiles);

        DataListAdapter dataListAdapter = new DataListAdapter();

        myListView.setAdapter(dataListAdapter);
    }

    public void setEnabledButton() {
        // Si el total de bytes a enviar o la cantidad de bytes es cero, deshabilito el botón que inicia el server. Si no, lo habilito.
        if (totalBytesToSend == 0 || totalFilesToSend == 0) {
            butStartServer.setEnabled(false);
        } else {
            butStartServer.setEnabled(true);
        }
    }

    // Clase para crear y manejar ítems personalizados en una lista.
    public class DataListAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return filesToSend.size();
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int i, View view, ViewGroup viewGroup) {
            LayoutInflater inflater = getLayoutInflater();

            @SuppressLint("ViewHolder") View row = inflater.inflate(R.layout.fileserver, viewGroup, false);

            TextView textNameFile, textPathFile, ttextTotalSize;
            CheckBox checkbox;

            textNameFile = row.findViewById(R.id.textNameShareFile);
            textPathFile = row.findViewById(R.id.textPathShareFile);
            ttextTotalSize = row.findViewById(R.id.textBytesShareFile);
            checkbox = row.findViewById(R.id.checkBoxShareFile);

            // Seteo todas las etiquetas del ítem i de la lista de archivos a enviar.
            textNameFile.setText(filesToSend.get(i).getFile().getName());
            textPathFile.setText(filesToSend.get(i).getFile().getParent());
            ttextTotalSize.setText(Utils.resumeBytes(filesToSend.get(i).getSize()));

            if (filesToSend.get(i).getSize() > 0) {
                checkbox.setChecked(filesToSend.get(i).isSelected());
            } else {
                checkbox.setEnabled(false);
                checkbox.setChecked(false);
                filesToSend.get(i).setSelected(false);
            }

            // Evento que checkea o descheckea el archivo a enviar y recalcula la cantidad de archivos a enviar y el tamaño total.
            checkbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    filesToSend.get(i).changeSelected();
                    recalculate();
                }
            });

            return (row);
        }
    }

    public void recalculate() {
        // Borro los datos anteriores.
        totalBytesToSend = 0;
        totalFilesToSend = 0;

        for (FileToSend file : filesToSend) {
            // Si el archivo esta preseleccionado para ser enviado y este noe stá vacío, calculo:
            if (file.isSelected() && file.getSize() > 0) {
                totalBytesToSend += file.getSize();
                totalFilesToSend++;
            }
        }

        // Seteo las etiquetas con los nuevos valores.
        textTotalSizeValue.setText(Utils.resumeBytes(totalBytesToSend));
        textTotalFilesValue.setText(String.valueOf(totalFilesToSend));

        setEnabledButton();
    }

    public void startServer(View view) {
        try {
            String macAP, ssidAP, serverIP;

            String data[] = Utils.isConectedToAP(this);

            // Verifico que el discpositivo esté conectado a una red WiFi.
            if (data == null) {
                Utils.showMessage(this, "¡CUIDADO!", "El dispositivo debe estar conectado a una red WiFi para poder enviar archivos.", false);
                return;
            }

            ssidAP = data[0];
            macAP = data[1];

            // Verifico que obtuve correctamente la dirección MAC del router donde está conectado el dispositivo.
            if (TextUtils.isEmpty(macAP)) {
                Utils.showMessage(this, "ERROR", "Hubo un error al intentar obtener la dirección MAC del router. Por favor, asegúrese que esté conectado y vuelva a intentarlo.", true);
                return;
            }

            // Obtengo la dirección IP actual del dispositivo (donde funcionará el server)
            serverIP = data[2];

            ArrayList<String> filesToSendForNextActivity = new ArrayList<>();

            // Creo una nueva lista con las rutas de los archivos seleccionados para enviar.
            for (FileToSend fileToSend : filesToSend) {
                if (fileToSend.isSelected()) {
                    filesToSendForNextActivity.add(fileToSend.getUri());
                }
            }

            Intent intent = new Intent(this, ServerMode.class);

            intent.putStringArrayListExtra("filesToSend", filesToSendForNextActivity);

            intent.putExtra("macAP", macAP);
            intent.putExtra("ssidAP", ssidAP);
            intent.putExtra("serverIP", serverIP);
            intent.putExtra("totalBytesToSend", String.valueOf(totalBytesToSend));

            ServerModeSelect.this.finish();

            startActivity(intent);
        } catch (Exception ex) {
            Log.e("select", ex.getMessage());
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Utils.showMessageWithOption(this,
                    "¡CUIDADO!",
                    "¿Realmente desea salir de la aplicación?");
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }
}
