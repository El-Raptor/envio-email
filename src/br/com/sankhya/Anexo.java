package br.com.sankhya;

import java.sql.Blob;

public class Anexo {
    private AnalisadorDeBlob.FileInfo fileInfo;
    private Blob arquivo;

    public Anexo(AnalisadorDeBlob.FileInfo fileInfo, Blob arquivo) {
        this.fileInfo = fileInfo;
        this.arquivo = arquivo;
    }

    public AnalisadorDeBlob.FileInfo getFileInfo() {
        return fileInfo;
    }

    public Blob getArquivo() {
        return arquivo;
    }
}
