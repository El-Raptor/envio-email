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

public class AnexoService {

    private final BlobService blobService;

    public AnexoService() {
        this.blobService = new BlobService();
    }

    public List<Anexo> buscarAnexos(BigDecimal nuEnvio) throws Exception {
        List<Anexo> arquivos = new ArrayList<>();
        JdbcWrapper jdbc = null;
        NativeSql sql = null;
        ResultSet rs = null;

        try {
            EntityFacade entityFacade = EntityFacadeFactory.getDWFFacade();
            jdbc = entityFacade.getJdbcWrapper();
            jdbc.openSession();

            sql = new NativeSql(jdbc);
            sql.appendSql("SELECT ANEXO FROM AD_TCPANX WHERE NUENVIO = :NUENVIO");
            sql.setNamedParameter("NUENVIO", nuEnvio);

            rs = sql.executeQuery();
            AnalisadorDeBlob analisador = new AnalisadorDeBlob();

            while (rs.next()) {
                Blob arquivo = rs.getBlob("ANEXO");
                if (arquivo != null) {
                    arquivos.add(new Anexo(analisador.analisar(arquivo), arquivo));
                }
            }
        } catch (Exception e) {
            throw new Exception("Falha ao consultar anexos da tabela AD_TCPANX (NUENVIO: " + nuEnvio + "). Detalhes: " + e.getMessage(), e);
        } finally {
            if (rs != null) rs.close();
            if (sql != null) NativeSql.releaseResources(sql);
            if (jdbc != null) JdbcWrapper.closeSession(jdbc);
        }
        return arquivos;
    }

    public JsonArray processarEVincularAnexos(List<Anexo> arquivos, BigDecimal codFila) throws Exception {
        JsonArray jsonArrayAnexos = new JsonArray();

        for (Anexo arquivo : arquivos) {
            try {
                byte[] bytesLimpos = blobService.extrairBytesLimpos(arquivo.getArquivo());

                JapeWrapper anexoMensagemDAO = JapeFactory.dao(DynamicEntityNames.ANEXO_MENSAGEM);
                DynamicVO anexoMensagemVO = anexoMensagemDAO.create()
                        .set("ANEXO", bytesLimpos)
                        .set("NOMEARQUIVO", arquivo.getFileInfo().getNome())
                        .set("TIPO", arquivo.getFileInfo().getMimeType())
                        .save();

                JapeWrapper anexoPorMensagemDAO = JapeFactory.dao(DynamicEntityNames.ANEXO_POR_MENSAGEM);
                anexoPorMensagemDAO.create()
                        .set("NUANEXO", anexoMensagemVO.asBigDecimal("NUANEXO"))
                        .set("CODFILA", codFila)
                        .save();

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