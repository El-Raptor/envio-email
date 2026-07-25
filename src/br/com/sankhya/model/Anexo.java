package br.com.sankhya.model;

import java.sql.Blob;

public class Anexo {
    private FileInfo fileInfo;
    private Blob arquivo;

    public Anexo(FileInfo fileInfo, Blob arquivo) {
        this.fileInfo = fileInfo;
        this.arquivo = arquivo;
    }

    public FileInfo getFileInfo() { return fileInfo; }
    public Blob getArquivo() { return arquivo; }
}