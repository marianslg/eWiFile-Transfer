package com.example.root.ewifiletransfer.Utils;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public final class UtilsSockets {
    private static final IvParameterSpec ivParameterSpec = new IvParameterSpec(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});


    /* SOCKET UTILS */
    public static String receiveMessage(Socket socket, SecretKey secretKey) {
        try {
            // Inicializo estructuras para recibir mensaje.
            BufferedReader decrypterBufferReader = new BufferedReader(new InputStreamReader(new BufferedInputStream(socket.getInputStream())));

            // Leo el mensaje (encriptado) recibido.
            String encryptedMsg = decrypterBufferReader.readLine();

            // Convierto el mensaje de texto a bytes.
            byte[] encryptedMsgBytes = stringToByteBASE64(encryptedMsg);

            // Creo e inicializo el desencriptador.
            Cipher cipher = getDecipher(secretKey);

            // Desencripto el mensaje
            byte[] msgBytes = cipher.doFinal(encryptedMsgBytes);

            // Convierto el mensaje desencriptado de bye a String.
            String msg = new String(msgBytes, "UTF-8");

            Log.i("Utils.receiveMessage()", "Mensaje recibido: " + msg);

            return msg;
        } catch (IOException e) {
            Log.e("Utils.receiveMessage()", e.getMessage());
        } catch (BadPaddingException e) {
            Log.e("Utils.receiveMessage()", e.getMessage());
        } catch (IllegalBlockSizeException e) {
            Log.e("Utils.receiveMessage()", e.getMessage());
        }

        return null;
    }

    public static boolean sendMessage(Socket socket, SecretKey secretKey, String msg) {
        try {
            // Inicializo estructuras para enviar mensaje.
            PrintWriter encryptedPrintWriter = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(socket.getOutputStream())), true);

            // Convierto el texto del mensaje a bytes.
            byte[] msgBytes = msg.getBytes("UTF-8");

            // Creo e inicializo el encriptador.
            Cipher cipher = getCipher(secretKey);

            // Encripto esos bytes.
            byte[] encryptedMsgBytes = cipher.doFinal(msgBytes);

            // Convierto esos bytes encriptados a String para poder enviarlos como texto.
            String encryptedMsg = byteToStringBASE64(encryptedMsgBytes);

            Log.e("Utils.sendMessage()", "Msg encriptado: " + encryptedMsg);

            // Adjunto el mensaje encriptado.
            encryptedPrintWriter.println(encryptedMsg);

            // Envío el mensaje.
            encryptedPrintWriter.flush();

            return true;
        } catch (IOException e) {
            Log.e("Utils.sendMessage()", e.getMessage());
        } catch (BadPaddingException e) {
            Log.e("Utils.sendMessage()", e.getMessage());
        } catch (IllegalBlockSizeException e) {
            Log.e("Utils.sendMessage()", e.getMessage());
        }

        return false;
    }

    private static String byteToHexa(byte[] b) {
        StringBuilder result = new StringBuilder();

        for (byte aB : b) {
            result.append(Integer.toString((aB & 0xff) + 0x100, 16).substring(1));
        }

        return result.toString();
    }

    public static String getHashMD5FromFile(String pathFile) {
        try {
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");

            //Get file input stream for reading the file content
            FileInputStream fis = new FileInputStream(new File(pathFile));

            //Create byte array to read data in chunks
            byte[] byteArray = new byte[1024];
            int bytesCount;

            //Read file data and update in message digest
            while ((bytesCount = fis.read(byteArray)) != -1) {
                md5Digest.update(byteArray, 0, bytesCount);
            }

            //close the stream; We don't need it now.
            fis.close();

            //Get the hash's bytes
            byte[] bytesMD5 = md5Digest.digest();

            String hexaBytes = byteToHexa(bytesMD5);

            return hexaBytes;
        } catch (Exception ex) {
            return "";
        }
    }

    public static Cipher getCipher(SecretKey secretKey) {
        Cipher cipher = null;

        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

        return cipher;
    }

    public static Cipher getDecipher(SecretKey secretKey) {
        Cipher cipher = null;

        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

        return cipher;
    }

    /* BASE 64 UTILS */
    public static String byteToStringBASE64(byte[] bytes) {
        if (bytes != null) {
            // NO_WRAP finaliza la cadena para que no quede basura al final.
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
        } else {
            return null;
        }
    }

    public static byte[] stringToByteBASE64(String cad) {
        if (cad != null) {
            // NO_WRAP finaliza la cadena para que no quede basura al final.
            return android.util.Base64.decode(cad, android.util.Base64.NO_WRAP);
        } else {
            return null;
        }
    }
}
