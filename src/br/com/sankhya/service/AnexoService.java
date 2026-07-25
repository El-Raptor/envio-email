package br.com.sankhya.service;

import br.com.sankhya.model.Anexo;
import br.com.sankhya.util.AnalisadorDeBlob;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço responsável pela gestão de anexos no processo de envio de e-mails.
 *
 * Esta classe atua na camada de negócios orquestrando a busca de arquivos anexados
 * em tabelas customizadas (AD_TCPANX), a extração de seus metadados e o salvamento
 * (já limpos de cabeçalhos indevidos) nas tabelas nativas de anexos de mensagens do Sankhya.
 *
 * @author Felipe Salles Lopes <felipe.lopes@sankhya.com.br>
 */
public class AnexoService {

    private final BlobService blobService;

    /**
     * Construtor padrão da classe.
     * Inicializa as dependências de serviços auxiliares, como o BlobService.
     */
    public AnexoService() {
        this.blobService = new BlobService();
    }

    /**
     * Realiza a busca de arquivos anexados atrelados a um registro específico de envio.
     * Consulta diretamente a tabela customizada AD_TCPANX via SQL Nativo (NativeSql).
     *
     * Durante a extração, utiliza o {@link AnalisadorDeBlob} para identificar
     * o nome original do arquivo e seu MIME Type a partir do cabeçalho do BLOB.
     *
     * @param nuEnvio O Número Único (ID) do registro de envio cujos anexos devem ser buscados.
     * @return Uma lista de objetos {@link Anexo}, contendo os metadados mapeados e a referência bruta ao objeto Blob do banco.
     * @throws Exception Em caso de erro na execução da consulta SQL ou falha na leitura inicial dos objetos BLOB.
     */
    public List<Anexo> buscarAnexos(BigDecimal nuEnvio) throws Exception {
        List<Anexo> arquivos = new ArrayList<>();
        JdbcWrapper jdbc = null;
        NativeSql sql = null;
        ResultSet rs = null;

        try {
            EntityFacade entityFacade = EntityFacadeFactory.getDWFFacade();
            jdbc = entityFacade.getJdbcWrapper();
            jdbc.openSession();

            // Consulta os anexos da tabela customizada
            sql = new NativeSql(jdbc);
            sql.appendSql("SELECT ANEXO FROM AD_TCPANX WHERE NUENVIO = :NUENVIO");
            sql.setNamedParameter("NUENVIO", nuEnvio);

            rs = sql.executeQuery();
            AnalisadorDeBlob analisador = new AnalisadorDeBlob();

            // Itera sobre o ResultSet e extrai os metadados de cada BLOB encontrado
            while (rs.next()) {
                Blob arquivo = rs.getBlob("ANEXO");
                if (arquivo != null) {
                    arquivos.add(new Anexo(analisador.analisar(arquivo), arquivo));
                }
            }
        } catch (Exception e) {
            throw new Exception("Falha ao consultar anexos da tabela AD_TCPANX (NUENVIO: " + nuEnvio + "). Detalhes: " + e.getMessage(), e);
        } finally {
            // Libera os recursos de banco de dados
            if (rs != null) rs.close();
            if (sql != null) NativeSql.releaseResources(sql);
            if (jdbc != null) JdbcWrapper.closeSession(jdbc);
        }
        return arquivos;
    }

    /**
     * Processa os arquivos em memória (limpando cabeçalhos de metadados), os grava
     * fisicamente na tabela de anexos nativa (TMDFMA) e os vincula ao e-mail criado
     * na Fila de Mensagens (TMDFMG).
     *
     * Utiliza o {@link BlobService} para garantir que os bytes extraídos não corrompam o arquivo final.
     *
     * @param arquivos A lista de objetos {@link Anexo} carregados pelo método {@link #buscarAnexos(BigDecimal)}.
     * @param codFila O código (CODFILA) do registro do e-mail na Fila de Mensagens.
     * @return Um JsonArray contendo os IDs (NUANEXO) dos anexos recém-criados, útil para mapeamento no frontend.
     * @throws Exception Em caso de falha no processamento dos bytes ou erro de persistência nas tabelas do Sankhya via JAPE.
     */
    public JsonArray processarEVincularAnexos(List<Anexo> arquivos, BigDecimal codFila) throws Exception {
        JsonArray jsonArrayAnexos = new JsonArray();

        for (Anexo arquivo : arquivos) {
            try {
                // Remove o cabeçalho customizado e obtém apenas os bytes reais do arquivo
                byte[] bytesLimpos = blobService.extrairBytesLimpos(arquivo.getArquivo());

                // 1. Grava o anexo na tabela de Anexos de Mensagem (TMDFMA)
                JapeWrapper anexoMensagemDAO = JapeFactory.dao(DynamicEntityNames.ANEXO_MENSAGEM);
                DynamicVO anexoMensagemVO = anexoMensagemDAO.create()
                        .set("ANEXO", bytesLimpos) // Array limpo
                        .set("NOMEARQUIVO", arquivo.getFileInfo().getNome())
                        .set("TIPO", arquivo.getFileInfo().getMimeType())
                        .save();

                // 2. Vincula o anexo salvo à Fila de Mensagem (TSIAXM)
                JapeWrapper anexoPorMensagemDAO = JapeFactory.dao(DynamicEntityNames.ANEXO_POR_MENSAGEM);
                anexoPorMensagemDAO.create()
                        .set("NUANEXO", anexoMensagemVO.asBigDecimal("NUANEXO"))
                        .set("CODFILA", codFila)
                        .save();

                // 3. Registra o ID gerado para ser retornado na resposta da requisição
                JsonObject jsonAnexoMsg = new JsonObject();
                jsonAnexoMsg.addProperty("NUANEXO", anexoMensagemVO.asBigDecimal("NUANEXO").toString());
                jsonArrayAnexos.add(jsonAnexoMsg);

            } catch (Exception e) {
                String nomeArquivo = arquivo.getFileInfo() != null ? arquivo.getFileInfo().getNome() : "Desconhecido";
                throw new Exception("Falha ao processar e gravar o anexo '" + nomeArquivo + "'. Detalhes: " + e.getMessage(), e);
            }
        }
        return jsonArrayAnexos;
    }
}