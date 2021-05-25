package com.example.root.ewifiletransfer;

import android.databinding.BaseObservable;
import android.databinding.Bindable;

import com.example.root.ewifiletransfer.Utils.Utils;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class Stats extends BaseObservable {

    /*
     * unit:
     *  1: bytes.
     *  1024: Kbytes.
     *  1048576: Mbytes.
     *  1073741824: Gbytes.
     */

    /*
     * _var = utilizado para almacenar valor de variable.
     * var = variable formateada utilizada para mostrar en la vista.
     */

    private long _time;
    private String time;                // Tiempo transcurrido formato ##:##.
    private int progressTotal;          // Progreso total de la tranferencia.
    private String percentageTotal;     // Porcentaje total #.##% total de la tranferencia.
    private String velocity;            // Velocidad de tranferencia.

    private String currentSizeTotal;    // Tamaño total de la tranferencia. "#.##"
    private long _currentSizeTotal = 0;
    private String sizeTotal;           // Tamaño total a descargar/enviar. " de #.## bytes/Kb/Mb/Gb"
    private long _sizeTotal = 0;

    private String nameFile;            // Nombre actual del archivo.
    private int progressFile;           // Progreso transferencia actual archivo.
    private int currentFile = 0;
    private int totalFiles = 0;
    private String currentOverTotalFiles;// (currentTotal/currentTotal).

    private String action;

    private DecimalFormat df = new DecimalFormat("#");

    public Stats() {
        set_time(0);

        setProgressTotal(0);
        setVelocity("0.0");

        setCurrentSizeTotal("0.00");
        setSizeTotal(" de 0.00");

        setNameFile("");
        setProgressFile(0);
        setCurrentOverTotalFiles("(0/0)");

        setAction("");
    }

    /*
     *  Time
     */

    public long get_time() {
        return _time;
    }

    public void set_time(long _time) {
        this._time = _time;
        setTime(this._time);
    }

    public void addTime(long timeAdd) {
        set_time(_time + timeAdd);
    }

    @Bindable
    public String getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = String.format(Locale.US, "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(time),
                TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time)));
        notifyPropertyChanged(BR.time);
    }

    /*
     *  Total percentage
     */

    @Bindable
    public String getPercentageTotal() {
        return percentageTotal;
    }

    private void setPercentageTotal(String percentageTotal) {
        this.percentageTotal = percentageTotal;
        notifyPropertyChanged(BR.percentageTotal);
    }

    /*
     * Progress
     */

    @Bindable
    public int getProgressTotal() {
        return progressTotal;
    }

    private void setProgressTotal(int progressTotal) {
        this.progressTotal = progressTotal;
        notifyPropertyChanged(BR.progressTotal);
    }

    /*
     * Velocity
     */

    public void setBytesPerSecond(long bytesPerSecond) {
        setVelocity(Utils.resumeBytes(bytesPerSecond) + "/s");
    }

    public String getAverageVelocity() {
        if(_time > 0) {
            return Utils.resumeBytes(Math.round((_sizeTotal / _time) * 1000)) + "/s";
        }
        else {
            return "0 KB/s";
        }
    }

    @Bindable
    public String getVelocity() {
        return velocity;
    }

    private void setVelocity(String velocity) {
        this.velocity = velocity;
        notifyPropertyChanged(BR.velocity);
    }

    /*
     * Size
     */

    @Bindable
    public String getCurrentSizeTotal() {
        return currentSizeTotal;
    }

    private void setCurrentSizeTotal(String currentSizeTotal) {
        this.currentSizeTotal = currentSizeTotal;

        /* Calculo de porcentaje de descarga */

        double per = 0;

        try {
            per = (_currentSizeTotal * 100) / _sizeTotal;
        } catch (Exception ignored) {
        }

        setPercentageTotal(df.format(per) + "%");

        /* Calculo de progreso para ProgressBar (int) */

        setProgressTotal(calculateProgress(per));

        notifyPropertyChanged(BR.currentSizeTotal);
    }

    public void set_currentSizeTotal(long _currentSizeTotal) {
        this._currentSizeTotal = _currentSizeTotal;
        setCurrentSizeTotal(Utils.resumeBytes(this._currentSizeTotal));
    }

    @Bindable
    public String getSizeTotal() {
        return sizeTotal;
    }

    private void setSizeTotal(String sizeTotal) {
        this.sizeTotal = " de " + sizeTotal;
        notifyPropertyChanged(BR.sizeTotal);
    }

    public void set_sizeTotal(long _sizeTotal) {
        this._sizeTotal = _sizeTotal;

        setSizeTotal(Utils.resumeBytes(this._sizeTotal));
    }

    /*
     * Name file
     */

    @Bindable
    public String getNameFile() {
        return nameFile;
    }

    public void setNameFile(String nameFile) {
        this.nameFile = nameFile;
        notifyPropertyChanged(BR.nameFile);
    }

    /*
     * Progress file
     */

    @Bindable
    public int getProgressFile() {
        return progressFile;
    }

    public void setProgressFile(long progressFile) {
        this.progressFile = calculateProgress(progressFile);
        notifyPropertyChanged(BR.progressFile);
    }

    /*
     * (Current / Total) file
     */

    public int getCurrentFile() {
        return currentFile;
    }

    public void addCurrentFile() {
        setCurrentFile(currentFile + 1);
    }

    private void setCurrentFile(int currentFile) {
        this.currentFile = currentFile;
        setCurrentOverTotalFiles("(" + currentFile + "/" + getTotalFiles() + ")");
    }

    private int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
        setCurrentOverTotalFiles("(" + getCurrentFile() + "/" + this.totalFiles + ")");
    }

    @Bindable
    public String getCurrentOverTotalFiles() {
        return currentOverTotalFiles;
    }

    private void setCurrentOverTotalFiles(String currentOverTotalFiles) {
        this.currentOverTotalFiles = currentOverTotalFiles;
        notifyPropertyChanged(BR.currentOverTotalFiles);
    }

    private int calculateProgress(double progress) {
        if (progress < 99) {
            return (int) Math.round(progress);
        } else if (progress != 100) {
            return 99;
        } else {
            return 100;
        }
    }

    @Bindable
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
        notifyPropertyChanged(BR.action);
    }
}