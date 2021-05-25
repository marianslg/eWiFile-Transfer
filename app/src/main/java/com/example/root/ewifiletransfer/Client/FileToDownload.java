package com.example.root.ewifiletransfer.Client;

public class FileToDownload {
    private String name;
    private long size; // bytes
    private String path = null;
    private String hash;

    public FileToDownload(String name, long size, String hash) {
        this.name = name;
        this.size = size;
        this.hash = hash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getHash() {
        return hash;
    }
}