package br.com.sankhya.service;

import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.util.Arrays;

/**
 * Serviço responsável por manipulações de baixo nível em objetos do tipo BLOB.
 *
 * O objetivo principal desta classe é garantir a integridade dos arquivos (anexos)
 * ao extraí-los do banco de dados, removendo quaisquer cabeçalhos de metadados customizados
 * que possam corromper o arquivo final (ex: PDFs que não abrem devido a texto inserido no início do binário).
 *
 * @author Felipe Salles Lopes (felipe.lopes@sankhya.com.br)
 */
public class BlobService {

    /**
     * Extrai o conteúdo de um objeto BLOB do banco de dados para um array de bytes (byte[]),
     * identificando e removendo o cabeçalho de metadados customizado, se houver.
     *
     * O método lê o início do arquivo (até 2048 bytes) buscando a tag delimitadora
     * "__end_fileinformation__". Caso a encontre, realiza um recorte (substring) cirúrgico
     * considerando a codificação UTF-8, o que garante que caracteres especiais (acentos)
     * no cabeçalho não desalinhem a contagem de bytes do arquivo real.
     *
     * @param blob O objeto java.sql.Blob contendo os dados brutos do arquivo vindos do banco.
     * @return Um array de bytes (byte[]) contendo exclusivamente o conteúdo real do arquivo,
     *         pronto para ser gravado em disco ou enviado como anexo. Caso a tag de metadados
     *         não seja encontrada, retorna os bytes originais integralmente.
     * @throws Exception Em caso de falha de leitura do objeto Blob ou manipulação do array de bytes.
     */
    public byte[] extrairBytesLimpos(Blob blob) throws Exception {
        // Extrai todo o conteúdo do Blob para a memória
        byte[] bytesOriginais = blob.getBytes(1, (int) blob.length());
        byte[] bytesLimpos = bytesOriginais;

        // Lê apenas o começo do arquivo (até 2048 bytes) para otimizar a busca da tag
        int tamanhoBusca = Math.min(bytesOriginais.length, 2048);
        String scanTexto = new String(bytesOriginais, 0, tamanhoBusca, StandardCharsets.UTF_8);

        String tagFim = "__end_fileinformation__";
        int indexFim = scanTexto.indexOf(tagFim);

        // Se encontrar a tag, faz o recorte do cabeçalho
        if (indexFim != -1) {
            // Isola a string exata do cabeçalho
            String cabecalhoStr = scanTexto.substring(0, indexFim + tagFim.length());

            // Calcula o tamanho do cabeçalho estritamente em BYTES (necessário devido a caracteres UTF-8 multi-byte)
            int tamanhoCabecalhoBytes = cabecalhoStr.getBytes(StandardCharsets.UTF_8).length;

            // Cria um novo array apenas com o conteúdo real do arquivo, ignorando a quantidade de bytes do cabeçalho
            bytesLimpos = Arrays.copyOfRange(bytesOriginais, tamanhoCabecalhoBytes, bytesOriginais.length);
        }

        return bytesLimpos;
    }
}