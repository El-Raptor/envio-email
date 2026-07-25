package br.com.sankhya.model;

/**
 * Modelo de dados (DTO) utilizado para encapsular os metadados de um arquivo.
 *
 * Esta classe tem como objetivo transportar as informações extraídas do cabeçalho
 * customizado dos BLOBs (como nome e tipo MIME) de forma estruturada entre as camadas
 * de utilitários ({@link br.com.sankhya.util.AnalisadorDeBlob}) e de serviços
 * ({@link br.com.sankhya.service.AnexoService}).
 *
 * @author Felipe Salles Lopes <felipe.lopes@sankhya.com.br>
 */
public class FileInfo {

    private String nome;
    private String mimeType;

    /**
     * Construtor da classe FileInfo.
     *
     * @param nome O nome do arquivo com sua respectiva extensão (ex: "planilha_vendas.xlsx").
     * @param mimeType O tipo MIME que identifica o formato do arquivo (ex: "application/pdf", "image/png").
     */
    public FileInfo(String nome, String mimeType) {
        this.nome = nome;
        this.mimeType = mimeType;
    }

    /**
     * Obtém o nome do arquivo.
     *
     * @return O nome do arquivo extraído ou um valor genérico caso não tenha sido identificado.
     */
    public String getNome() {
        return nome;
    }

    /**
     * Obtém o tipo MIME (formato) do arquivo.
     *
     * @return A string representando o MIME Type do arquivo.
     */
    public String getMimeType() {
        return mimeType;
    }
}