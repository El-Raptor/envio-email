package br.com.sankhya.util;

import br.com.sankhya.model.FileInfo;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalisadorDeBlob {

    public FileInfo analisar(Blob blob) throws Exception {
        String nomeArquivo = "nome_desconhecido";
        String mimeTypeDetectado = "application/octet-stream";

        try (InputStream is = blob.getBinaryStream()) {
            byte[] buffer = new byte[2048];
            int bytesLidos = is.read(buffer);

            if (bytesLidos > 0) {
                String inicioDoBlob = new String(buffer, 0, bytesLidos, StandardCharsets.UTF_8);

                if (inicioDoBlob.startsWith("__start_fileinformation__")) {
                    Pattern patternName = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher matcherName = patternName.matcher(inicioDoBlob);
                    if (matcherName.find()) {
                        nomeArquivo = matcherName.group(1);
                    }

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