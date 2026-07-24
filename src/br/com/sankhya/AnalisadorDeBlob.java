package br.com.sankhya;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalisadorDeBlob {

    public static class FileInfo {
        private String nome;
        private String mimeType;

        public FileInfo(String nome, String mimeType) {
            this.nome = nome;
            this.mimeType = mimeType;
        }

        public String getNome() { return nome; }
        public String getMimeType() { return mimeType; }
    }

    /**
     * Lê o cabeçalho customizado do BLOB para extrair metadados.
     */
    public FileInfo analisar(Blob blob) throws Exception {
        
        String nomeArquivo = "nome_desconhecido"; 
        String mimeTypeDetectado = "application/octet-stream";

        try (InputStream is = blob.getBinaryStream()) {
            // Cria um buffer pequeno (2048 bytes) para ler apenas o início do arquivo.
            // Isso evita carregar um BLOB gigante inteiro na memória.
            byte[] buffer = new byte[2048];
            int bytesLidos = is.read(buffer);

            if (bytesLidos > 0) {
                // Converte os primeiros bytes para String (assumindo codificação UTF-8)
                String inicioDoBlob = new String(buffer, 0, bytesLidos, StandardCharsets.UTF_8);

                // Verifica se o BLOB realmente começa com a sua tag de metadados
                if (inicioDoBlob.startsWith("__start_fileinformation__")) {
                    
                    // 1. Extrair o 'name' ignorando o 'internalName'
                    // A Regex busca examente: "name":"qualquer_coisa_aqui"
                    Pattern patternName = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher matcherName = patternName.matcher(inicioDoBlob);
                    
                    if (matcherName.find()) {
                        nomeArquivo = matcherName.group(1); // group(1) pega só o que está dentro das aspas
                    }

                    // 2. Extrair o 'type' (MIME Type)
                    Pattern patternType = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher matcherType = patternType.matcher(inicioDoBlob);
                    
                    if (matcherType.find()) {
                        mimeTypeDetectado = matcherType.group(1);
                    }
                }
            }
        }

        return new FileInfo(nomeArquivo, mimeTypeDetectado);
    }
}