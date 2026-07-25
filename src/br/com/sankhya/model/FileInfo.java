package br.com.sankhya.model;

public class FileInfo {
    private String nome;
    private String mimeType;

    public FileInfo(String nome, String mimeType) {
        this.nome = nome;
        this.mimeType = mimeType;
    }

    public String getNome() { return nome; }
    public String getMimeType() { return mimeType; }
}