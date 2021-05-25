package com.example.root.ewifiletransfer.Server;

import java.io.File;

public class FileToSend {
    private boolean selected;
    private File file;
    private String uri;
    private int size;

    public FileToSend(File file, String uri, int size){
        this.file = file;
        this.uri = uri;
        this.size = size;
        selected = true;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public boolean isSelected() {
        return selected;
    }

    public void changeSelected(){
        selected = !selected;
    }

    public String getUri() {
        return uri;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void setSelected(boolean selected){
        this.selected = selected;
    }
}
