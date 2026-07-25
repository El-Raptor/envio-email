package br.com.sankhya.model;

import java.sql.Blob;

/**
 * Modelo de dados (DTO) que representa um arquivo anexado no sistema.
 *
 * Esta classe atua como um agregador, encapsulando tanto os metadados do arquivo
 * (como nome e tipo MIME, representados pela classe {@link FileInfo}) quanto
 * a referência aos dados binários brutos recuperados do banco de dados (BLOB)[cite: 2].
 *
 * Serve como uma estrutura de transporte consolidada para transitar as informações
 * do arquivo de forma segura entre as camadas de acesso a dados e de serviço
 * ({@link br.com.sankhya.service.AnexoService}).
 *
 * @author Felipe Salles Lopes <felipe.lopes@sankhya.com.br>
 */
public class Anexo {

    private FileInfo fileInfo;
    private Blob arquivo;

    /**
     * Construtor da classe Anexo.
     *
     * @param fileInfo Objeto contendo os metadados extraídos e mapeados do arquivo (nome e MIME type)[cite: 2].
     * @param arquivo Objeto java.sql.Blob contendo a referência direta aos dados binários do arquivo no banco de dados[cite: 2].
     */
    public Anexo(FileInfo fileInfo, Blob arquivo) {
        this.fileInfo = fileInfo;
        this.arquivo = arquivo;
    }

    /**
     * Obtém os metadados do arquivo anexado.
     *
     * @return O objeto {@link FileInfo} associado a este anexo[cite: 2].
     */
    public FileInfo getFileInfo() {
        return fileInfo;
    }

    /**
     * Obtém o conteúdo binário bruto do arquivo.
     *
     * @return O objeto {@link Blob} original proveniente do banco de dados, ainda com seu cabeçalho intacto[cite: 2].
     */
    public Blob getArquivo() {
        return arquivo;
    }
}