package br.com.sankhya.util;

import br.com.sankhya.model.FileInfo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitário responsável por inspecionar objetos BLOB do banco de dados
 * e extrair metadados customizados injetados no início do arquivo.
 *
 * A classe foca na performance ao ler apenas os bytes iniciais estritamente necessários
 * para encontrar a tag de metadados, evitando carregar arquivos grandes (como imagens ou PDFs)
 * inteiros na memória do servidor.
 *
 * @author Felipe Salles Lopes (felipe.lopes@sankhya.com.br)
 */
public class AnalisadorDeBlob {

    /**
     * Inspeciona um BLOB para tentar descobrir seu nome original e formato (MIME Type).
     *
     * O método espera que o arquivo possua um cabeçalho customizado no formato:
     * {@code __start_fileinformation__{"name":"arquivo.txt", "type":"text/plain"}__end_fileinformation__}[cite: 3]
     *
     * O processo de leitura consome no máximo os primeiros 2048 bytes[cite: 3]. Se a tag for encontrada,
     * Expressões Regulares (Regex) são utilizadas para isolar com precisão os valores das
     * chaves "name" e "type"[cite: 3].
     *
     * @param blob O objeto java.sql.Blob proveniente do banco de dados[cite: 3].
     * @return Um objeto FileInfo contendo o nome e o tipo MIME. Caso o cabeçalho não exista,
     *         retorna valores genéricos ("nome_desconhecido" e "application/octet-stream")[cite: 3].
     * @throws Exception Em caso de falhas de I/O ao tentar ler a stream de bytes do banco de dados.
     */
    public FileInfo analisar(Blob blob) throws Exception {

        // Define valores padrão caso o arquivo não possua a tag de metadados[cite: 3]
        String nomeArquivo = "nome_desconhecido";
        String mimeTypeDetectado = "application/octet-stream";

        try (InputStream is = blob.getBinaryStream()) {
            // Cria um buffer pequeno (2048 bytes) para ler apenas o início do arquivo[cite: 3].
            // Isso evita carregar um BLOB gigante inteiro na memória[cite: 3].
            byte[] buffer = new byte[2048];
            int bytesLidos = is.read(buffer);

            if (bytesLidos > 0) {
                // Converte os primeiros bytes para String assumindo codificação UTF-8[cite: 3]
                String inicioDoBlob = new String(buffer, 0, bytesLidos, StandardCharsets.UTF_8);

                // Verifica se o BLOB realmente começa com a tag de metadados[cite: 3]
                if (inicioDoBlob.startsWith("__start_fileinformation__")) {

                    // 1. Extrair o 'name'
                    // A Regex busca exatamente: "name":"valor_do_nome"[cite: 3]
                    Pattern patternName = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher matcherName = patternName.matcher(inicioDoBlob);

                    if (matcherName.find()) {
                        nomeArquivo = matcherName.group(1); // group(1) pega só o que está dentro das aspas do valor[cite: 3]
                    }

                    // 2. Extrair o 'type' (MIME Type)
                    // A Regex busca exatamente: "type":"valor_do_tipo"[cite: 3]
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