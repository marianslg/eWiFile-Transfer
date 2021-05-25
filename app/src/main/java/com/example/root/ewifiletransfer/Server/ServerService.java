package com.example.root.ewifiletransfer.Server;

import android.app.IntentService;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.example.root.ewifiletransfer.Stats;
import com.example.root.ewifiletransfer.Utils.UtilsSockets;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;

public class ServerService extends IntentService {
    private File fileXML;
    private ArrayList<File> filesToSend;

    private volatile Stats stats;

    private volatile AtomicLong totalSizeRead = new AtomicLong(0); // Total de bytes leídos.
    private volatile AtomicLong readPerSecond = new AtomicLong(0); // Variable utilizada para almacenar la cantidad de bytes leídos en un segundo.
    private volatile AtomicLong sizeFile = new AtomicLong(0); // Size del archivo que se está descargando
    private volatile AtomicLong currentlFileSizeRead = new AtomicLong(0); // Variable utilizada para almecenar la cantidad de bytes enviados de un archivo en especifico.

    private volatile Socket socketSendFile; // Estructura utilizada para enviar archvios.
    private volatile Socket socketMsg;      // Estructura utilizada para enviar y recibir mensajes.
    private volatile ServerSocket serverSocket;

    private volatile Timer timer;

    private SecretKey secretKey;
    private String stringKey;

    private AsyncResponse delegate;

    private long tiempoTotalTranscurrido;

    // Binder
    private final IBinder mBinder = new LocalBinder();

    // Para que la actividad pueda acceder al objeto y utilizar sus funciones.
    public class LocalBinder extends Binder {
        ServerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return ServerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public ServerService() {
        super("ServerService");
    }

    public interface AsyncResponse {
        void clientConnected(boolean on);

        void transferFinish(ArrayList<String> _return);
    }

    public void setVarServer(File fileXML, ServerSocket serverSocket, ArrayList<File> filesToSend, Stats stats, SecretKey secretKey, AsyncResponse delegate) {
        this.fileXML = fileXML;
        this.serverSocket = serverSocket;
        this.filesToSend = filesToSend;
        this.stats = stats;

        this.secretKey = secretKey;

        this.delegate = delegate;
    }

    protected void onHandleIntent(Intent intent) {
        stringKey = UtilsSockets.byteToStringBASE64(secretKey.getEncoded());

        try {
            Log.i("Server.doInBackground()", "Init server...");

            // Acepto clientes que cumplan con el protocolo.
            socketMsg = acceptClient(secretKey);

            if (socketMsg == null) {
                throw new Exception("socket is null");
            }

            // Aviso que se conectó un cliente para que la app oculte el código QR y abra la vista de la tranferencia.
            delegate.clientConnected(true);

            boolean sendXML = false;

            while (!sendXML) {
                // Envío el archivo .xml
                boolean resultSendFile = sendFile(fileXML, 0, secretKey, false);

                if (!resultSendFile) {
                    throw new Exception("sendFile(xml) fail");
                }

                // Verifico la integridad del archivo XML.
                if (resolveIntegrity(stringKey)) {
                    sendXML = true; // corto el while
                }
            }

            Log.i("HASH XML ----- ", UtilsSockets.getHashMD5FromFile(fileXML.getPath()));

            // Seteo una etiqueta de acción de la vista de transferencia.
            stats.setAction("Enviando archivos...");

            long sizeFile;

            // Inicio el contador de tiempo de la tranferencia.
            startTime();

            for (File file : filesToSend) {
                stats.addCurrentFile(); // Aumento en 1 y seteo el contador del total de archivos enviados.
                stats.setNameFile(file.getName()); // Seteo el nombre del archivo que se va por enviar a continuación.

                boolean result = false;
                long oldTotalSizeRead = totalSizeRead.get(); // Por si falla la tranferencia, para volver a un estado anterior.

                while (!result) {
                    sizeFile = new byte[(int) file.length()].length; // del archivo que se va por enviar a continuación.

                    boolean result2 = sendFile(file, sizeFile, secretKey, true);

                    if (!result2) {
                        throw new Exception("sendFile not work");
                    }

                    if (resolveIntegrity(stringKey)) {
                        result = true; // Corto el while.
                    } else {
                        // Vuelvo las estadísticas como estaban antes de completar la tranferencia del archivo
                        // ya que el server lo reenviará.
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

    private boolean resolveIntegrity(String stringKey) throws Exception {
        // Recibo el mensaje con el resultado de la verificación de la integridad del cliente.
        String msg = UtilsSockets.receiveMessage(socketMsg, secretKey);

        if (msg == null) {
            throw new Exception("receiveMessage not work");
        }

        // Elimino la clave del mensaje recibido para que me quede solo la IP.
        String resultFile = msg.replace(stringKey, "");

        // Comparo la IP original con la recibida dentro del mensaje.
        // Corto el while.

        return resultFile.equals("NEXT");
    }

    private boolean sendFile(File file, long sizeFile, SecretKey secretKey, boolean calcStas) {
        try {
            socketSendFile = acceptClient(secretKey);

            if (socketSendFile == null) {
                throw new IOException("socket null");
            }

            // Creo estructura para leer stream de archivos locales.
            DataInputStream dis = new DataInputStream(new FileInputStream(file));

            Cipher cipher = UtilsSockets.getCipher(secretKey);

            // Creo estructura para encriptar los streams que se enviarán al cliente.
            CipherOutputStream cipherOutputStream = new CipherOutputStream(new DataOutputStream(
                    socketSendFile.getOutputStream()), cipher);

            byte[] buffer = new byte[1024 * 8];

            int read; // Variable utilizada para almacenar la cantidad de bytes enviados.

            currentlFileSizeRead.set(0); // Seteo en 0 la cantidad de bytes enviados.
            this.sizeFile.set(sizeFile); // Seteo el tamaño del archivo a enviar.

            // Leo el contenido del archivo y lo almaceno en el buffer.
            while ((read = dis.read(buffer)) > 0) {
                // LLeno la estructura para encriptar y enviar streams con el buffer.
                cipherOutputStream.write(buffer);

                // Envío los datos.
                cipherOutputStream.flush();

                if (calcStas) {
                    currentlFileSizeRead.getAndAdd(read); // Para calcular el progreso de tranferencia de un archivo

                    totalSizeRead.getAndAdd(read);
                    readPerSecond.getAndAdd(read);
                }
            }

            dis.close();
            cipherOutputStream.close(); // Tambien se cierra el socket.

            return true;
        } catch (IOException e) {
            //socketSendFile.close();

            Log.e("Server.sendFile()", e.getMessage());

            return false;
        }
    }

    private void startTime() {
        tiempoTotalTranscurrido = System.currentTimeMillis();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            long bps;

            @Override
            public void run() {
                stats.set_currentSizeTotal(totalSizeRead.get());

                bps = readPerSecond.getAndSet(0);  // Obtengo y Reinicio el contrador de bytes leídos por segundo

                stats.setBytesPerSecond(bps);

                if (sizeFile.get() > 0) {
                    stats.setProgressFile((currentlFileSizeRead.get() * 100) / sizeFile.get());
                }

                stats.addTime(1000);

                /*if (bps == 0) {
                    try {
                        if (!socketMsg.getInetAddress().isReachable(1000)) {
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

    private void closeConn() {
        try {
            if (socketSendFile != null) socketSendFile.close();
            if (socketMsg != null) socketMsg.close();
            if (serverSocket != null) serverSocket.close();

            if (timer != null) {
                timer.cancel();
            }
        } catch (Exception ignored) {
        }
    }

    private Socket acceptClient(SecretKey secretKey) {
        Socket socket;

        // Bucle que acepta conexiones hasta que se cumplan las condiciones o el usuario cancele.
        while (true) {
            try {
                socket = serverSocket.accept(); // Acepto conexión.

                // Recibo el mensaje.
                String msg = UtilsSockets.receiveMessage(socket, secretKey);

                Log.i("Msg recibido:", msg);

                // Obtengo la IP de la conexión aceptada anteriormente
                InetSocketAddress socketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
                String clientIP = socketAddress.getAddress().getHostAddress();

                // Elimino la clave del mensaje recibido para que me quede solo la IP.
                assert msg != null;
                String ipReceived = msg.replace(stringKey, "");

                // Comparo la IP original con la recibida dentro del mensaje.
                if (ipReceived.equals(clientIP)) {
                    return socket; // Retorno la conexión si las direcciones IP coinciden.
                } else {
                    socket.close();
                }
            } catch (IOException e) {
                Log.e("Server.acceptClient()", e.getMessage());

                //return null;
            }
        }
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
}