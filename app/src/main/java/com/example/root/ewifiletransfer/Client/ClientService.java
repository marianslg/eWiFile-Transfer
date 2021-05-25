package com.example.root.ewifiletransfer.Client;

import android.app.IntentService;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import com.example.root.ewifiletransfer.Stats;
import com.example.root.ewifiletransfer.Utils.UtilsSockets;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.xml.parsers.DocumentBuilderFactory;

public class ClientService extends IntentService {
    private String serverIP, clientIP;
    private int serverPort;

    private volatile Stats stats;

    private volatile AtomicLong totalSizeRead = new AtomicLong(0); // Total de bytes leídos.
    private volatile AtomicLong readPerSecond = new AtomicLong(0); // Variable utilizada para almacenar la cantidad de bytes leídos en un segundo.
    private volatile AtomicLong sizeFile = new AtomicLong(0); // Size del archivo que se está descargando
    private volatile AtomicLong remaining = new AtomicLong(0); // Cantidad de bytes que restan por descargar del archivo.

    private volatile Timer timer;

    private volatile Socket socketReceiveFile;
    private volatile Socket socketMsg = null;

    private SecretKey secretKey; // Clave para encriptar/desencriptar.

    private AsyncResponse delegate; // Para avisar a la actividad anterior que ejecutó este proceso que finalizó (bien o mal) la transferencia.

    private String stringKey;
    private int sizeXml;
    private String hashXml;

    private long tiempoTotalTranscurrido;

    // Bind

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    // Random number generator

    public class LocalBinder extends Binder {
        ClientService getService() {
            // Return this instance of LocalService so clients can call public methods
            return ClientService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public ClientService() {
        super("ClientService");
    }

    public interface AsyncResponse {
        void transferFinish(ArrayList<String> _return);

        //void serverNoRespond();
    }

    public void setVarClient(String serverIp, String clientIP, int serverPort, Stats stats, SecretKey secretKey, AsyncResponse delegate, int sizeXml, String hashXml) {
        this.serverIP = serverIp;
        this.clientIP = clientIP;
        this.serverPort = serverPort;
        this.stats = stats;
        this.secretKey = secretKey;
        this.delegate = delegate;
        this.sizeXml = sizeXml;
        this.hashXml = hashXml;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        stringKey = UtilsSockets.byteToStringBASE64(secretKey.getEncoded());

        try {
            Log.i("Client.doInBackground()", "Init client...");

            // Me conecto al servidor.
            socketMsg = connectServer(secretKey);

            if (socketMsg == null) {
                throw new Exception("socket is null");
            }

            boolean integrityXML = false;
            String path_data_files_xml = "";

            while (!integrityXML) {
                // Recibo el archivo xml.
                path_data_files_xml = receiveFile("data_files.xml", sizeXml, secretKey, false, true);

                if (path_data_files_xml == null) {
                    throw new Exception("path_data_files_xml is null");
                }

                // Genero un hash MD5 del archivo xml recibido.
                String localHashXml = UtilsSockets.getHashMD5FromFile(path_data_files_xml);

                // Verifico la integridad del archivo XML.
                if (resolveIntegrity(localHashXml, hashXml, stringKey)) {
                    integrityXML = true; // corto el while
                }
            }

            Log.i("Client.doInBackground()", "Ruta XML file: " + path_data_files_xml);

            Log.i("HASH XML ----- ", UtilsSockets.getHashMD5FromFile(path_data_files_xml));

            // Genero una lista de los archivos a recibir a partir del xml.
            ArrayList<FileToDownload> filesToDownload = getListFilesFromXML(path_data_files_xml);

            if (filesToDownload == null) {
                throw new Exception("filesToDownload is null");
            }

            // Verifico que existe la carpeta de descargas.
            File folderDownload = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator);

            if (!folderDownload.exists()) {
                // Si no existe, la creo.
                boolean createFolder = folderDownload.mkdirs();

                if (!createFolder) {
                    throw new Exception("Folder download no created.");
                }
            }

            // Seteo una etiqueta de acción de la vista de transferencia.
            stats.setAction("Descargando...");

            // Inicio el reloj que calcula el tiempo de la descarga.
            startTime();

            for (FileToDownload file : filesToDownload) {
                stats.addCurrentFile();
                stats.setNameFile(file.getName());

                Log.i("Client.doInBackground()", "Descargando " + file.getName() + "...");

                long oldTotalSizeRead = totalSizeRead.get(); // Por si falla la tranferencia, para volver a un estado anterior.

                // Pido al server que me envíe el archivo hasta que lo reciba correctamente.
                while (file.getPath() == null) {

                    // Recibo el archivo
                    String filePath = receiveFile(file.getName(), file.getSize(), secretKey, true, false);

                    if (filePath == null) {
                        throw new Exception("filePath is null");
                    }

                    // Obtengo el hash del archivo recibido
                    String hashLocalFile = UtilsSockets.getHashMD5FromFile(filePath);

                    Log.i("Local HASH: ", hashLocalFile);
                    Log.i("File HASH: ", file.getHash());

                    if (resolveIntegrity(hashLocalFile, file.getHash(), stringKey)) {
                        //Seteo, finalmente, el la ruta del archivo local y rompo el while.
                        file.setPath(filePath);
                    } else {
                        // Vuelvo las estadísticas como estaban antes de completar la tranferencia del archivo
                        // ya que el server me lo reenviará.
                        totalSizeRead.set(oldTotalSizeRead);
                        stats.set_currentSizeTotal(totalSizeRead.get());
                        stats.setProgressFile(0);
                    }
                }
            }

            // Finalizo la tranferencia exitosamente.
            finishTransfer("OK");
        } catch (Exception e) {
            Log.e("Client.doInBackground()", e.getMessage());

            finishTransfer("NO");
        }
    }

    private boolean resolveIntegrity(String realHash, String hashLocal, String stringKey) throws Exception {
        String msg;
        boolean result;

        if (hashLocal != null && hashLocal.equals(realHash)) {
            // Si el hash es el mismo que el del archivo original envío un mensaje al server
            // para que me envíe el siguiente archivo (si hay)
            msg = "NEXT".concat(stringKey);

            result = UtilsSockets.sendMessage(socketMsg, secretKey, msg);

            if (!result) {
                throw new Exception("sendMessage not work");
            }

            return true; // Retorno el resultado de la comparación de HASH.
        } else {
            // Si el hash es distinto que el del archivo original envío un mensaje al server
            // para que me reenvíe el archivo.
            msg = "AGAIN".concat(stringKey);
            //Log.i("Clie MSG after: ", msg);

            result = UtilsSockets.sendMessage(socketMsg, secretKey, msg);

            if (!result) {
                throw new Exception("sendMessage not work");
            }

            return false; // Retorno el resultado de la comparación de HASH.
        }
    }

    private String receiveFile(String nameFile, long size, SecretKey secretKey, boolean calcStas, boolean temporal) {
        File file;

        // Si temporal=true creo un archivo temporal. En cambio, si es false, creo un archivo
        // en el directorio predeterminado de descargas del dispositivo.
        try {
            if (temporal) {
                file = File.createTempFile(nameFile, ".xml");
            } else {
                file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), nameFile);
                Log.e("External", file.getAbsolutePath());
            }
        } catch (IOException e) {
            Log.e("Client.receiveFile()", e.getMessage());
            return null;
        }

        try {
            socketReceiveFile = connectServer(secretKey);

            if (socketReceiveFile == null) {
                throw new IOException("connectServer: socket null");
            }

            // Creo e inicializo estructura para desencriptar.
            Cipher cipher = UtilsSockets.getDecipher(secretKey);

            // Creo las estructuras para desencriptar streams.
            CipherInputStream cipherInputStream = new CipherInputStream(
                    new DataInputStream(socketReceiveFile.getInputStream()), cipher);

            // Creo una estructura para volcar el stram de datos recibido.
            //FileOutputStream fos = new FileOutputStream(file);
            DataOutputStream dos = new DataOutputStream(new FileOutputStream(file));

            // Creo buffer
            byte[] buffer = new byte[1024 * 8];

            int read;               // Variable utilizada para almacenar la cantidad de bytes leídos.
            remaining.set(size);  /* Variable utilizada para almacenar la cantidad de bytes que faltan leer para
            finalizar con el total del archivo. Al iniciar la descarga es igual al tamaño total del archivo
            ya que aun no se han descargado bytes del mismo.*/
            sizeFile.set(size);

            // Leo stream enviado desde servidor.
            while ((read = cipherInputStream.read(buffer, 0, Math.min(buffer.length, (int) remaining.get()))) > 0) {
                dos.write(buffer, 0, read); // Guardo el stream leído en el archivo local creado anteriormente.

                remaining.getAndAdd(-read); // Resto los bytes leídos.

                if (calcStas) {
                    totalSizeRead.getAndAdd(read);
                    readPerSecond.getAndAdd(read);
                }
            }

            dos.close(); // Cierro estructura local que almacenó el stream recibido.
            cipherInputStream.close();

            return file.getPath();
        } catch (IOException e) {
            file.delete();

            Log.e("Client.receiveFile()", e.getMessage());

            return null;
        }
    }

    private ArrayList<FileToDownload> getListFilesFromXML(String tempXMLFilePath) {
        try {
            ArrayList<FileToDownload> filesToDownload;
            File xmlFile = new File(tempXMLFilePath);
            Document doc;

            long totalSize = 0;

            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

            doc = documentBuilderFactory.newDocumentBuilder().parse(xmlFile);

            filesToDownload = new ArrayList<>();

            NodeList nList = doc.getElementsByTagName("file");

            for (int i = 0; i < nList.getLength(); i++) {
                Element element = (Element) nList.item(i);

                String name = element.getElementsByTagName("name").item(0).getTextContent();
                long size = Long.parseLong(element.getElementsByTagName("size").item(0).getTextContent());
                String hash = element.getElementsByTagName("hash").item(0).getTextContent();

                totalSize += size;

                filesToDownload.add(new FileToDownload(name, size, hash));
            }

            stats.set_sizeTotal(totalSize);
            stats.setTotalFiles(filesToDownload.size());

            return filesToDownload;
        } catch (Exception ex) {
            Log.e("Client", "getListFilesFromXML() " + ex.getMessage());

            return null;
        }
    }

    private void startTime() {
        tiempoTotalTranscurrido = System.currentTimeMillis();

        timer = new Timer();
        timer.schedule(new TimerTask() {
            int zeroBps = 0;
            long bps;

            @Override
            public void run() {

                stats.set_currentSizeTotal(totalSizeRead.get());

                bps = readPerSecond.getAndSet(0);

                stats.setBytesPerSecond(bps); // Obtengo y Reinicio el contrador de bytes leídos por segundo

                if (sizeFile.get() > 0) {
                    stats.setProgressFile(100 - ((100 * remaining.get()) / sizeFile.get()));
                }

                stats.addTime(1000);

                /*if (bps == 0) {
                    zeroBps++;

                    if (zeroBps == 5) {
                        Log.e("Entro a delegate", "dsd");
                        delegate.serverNoRespond();
                        zeroBps = 0;
                    }

                    try {
                        if(!socketMsg.getInetAddress().isReachable(1000)){
                            stats.addTime(-1000);

                            finishTransfer("NO");
                        }
                    } catch (IOException e) {
                        stats.addTime(-1000);

                        finishTransfer("NO");
                    }
                }*/
            }
        }, 1000, 1000);
    }

    public void finishTransfer(String finalResultTransfer) {
        closeConn();

        // Seteo el tiempo exacto
        stats.set_time(System.currentTimeMillis() - tiempoTotalTranscurrido);
        /*
         * ArrayList<String> return
         * 0: resultTransfer            --> OK= termino ok. NO= termino con errores.
         * 1: stats.getProgressFile();  --> Cantidad de archivos enviados.
         * 2: stats.getTime()           --> Cantidad de tiempo que duró el proceso.
         * 3: medida de tiempo          --> Si 2 esta expresada en segundos o minutos.
         * 4: stats.getAverageVelocity()--> Velocidad promedio de la transferencia.
         */

        ArrayList<String> _return = new ArrayList<>();

        _return.add(finalResultTransfer);

        if (finalResultTransfer.equals("OK")) {
            _return.add(String.valueOf(stats.getCurrentFile()));
        } else {
            if (stats.getCurrentFile() == 0) {
                _return.add("0");
            } else {
                _return.add(String.valueOf(stats.getCurrentFile() - 1));
            }
        }

        _return.add(stats.getTime());

        if (stats.get_time() >= 60000) { // time esta en milisegundos.
            _return.add("minutos");
        } else {
            _return.add("segundos");
        }

        _return.add(stats.getAverageVelocity());

        _return.add(stats.get_time() + "");

        delegate.transferFinish(_return);
    }

    private Socket connectServer(SecretKey secretKey) {
        try {
            Socket socket = new Socket(serverIP, serverPort);

            String msg = clientIP.concat(stringKey);

            // Envío el hash.
            boolean result = UtilsSockets.sendMessage(socket, secretKey, msg);

            if (result) {
                return socket;
            } else {
                return null; // Cuando el envio no pudo realizarse. Se cancela toda la operación.
            }
        } catch (IOException e) {
            Log.e("Client.connectServer()", e.getMessage());

            return null;
        }
    }

    private void closeConn() {
        try {
            if (socketReceiveFile != null) socketReceiveFile.close();
            if (socketMsg != null) socketMsg.close();

            if (timer != null) {
                timer.cancel();
            }
        } catch (IOException ignored) {
        }
    }
}
