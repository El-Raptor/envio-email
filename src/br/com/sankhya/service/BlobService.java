package br.com.sankhya.service;

import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.util.Arrays;

public class BlobService {

    public byte[] extrairBytesLimpos(Blob blob) throws Exception {
        byte[] bytesOriginais = blob.getBytes(1, (int) blob.length());
        byte[] bytesLimpos = bytesOriginais;

        int tamanhoBusca = Math.min(bytesOriginais.length, 2048);
        String scanTexto = new String(bytesOriginais, 0, tamanhoBusca, StandardCharsets.UTF_8);

        String tagFim = "__end_fileinformation__";
        int indexFim = scanTexto.indexOf(tagFim);

        if (indexFim != -1) {
            String cabecalhoStr = scanTexto.substring(0, indexFim + tagFim.length());
            int tamanhoCabecalhoBytes = cabecalhoStr.getBytes(StandardCharsets.UTF_8).length;
            bytesLimpos = Arrays.copyOfRange(bytesOriginais, tamanhoCabecalhoBytes, bytesOriginais.length);
        }
        return bytesLimpos;
    }
}