package br.com.sankhya;

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.ws.ServiceContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BotaoEnviaEmail implements AcaoRotinaJava {

    @Override
    public void doAction(ContextoAcao ctx) throws Exception {

        JapeSession.SessionHandle hnd = null;
        JdbcWrapper jdbc = null;
        NativeSql sql = null;
        ResultSet rs = null;

        try {
            hnd = JapeSession.open();

            // 1. Validação clara do registro
            Registro envioEmailIns = Arrays.stream(ctx.getLinhas())
                    .findFirst()
                    .orElseThrow(() -> new Exception("Erro: Nenhum registro selecionado para envio."));

            // 2. Coleta de dados com proteção contra nulos
            Object nuEnvioObj = envioEmailIns.getCampo("NUENVIO");
            if (nuEnvioObj == null) {
                throw new Exception("Erro de validação: O campo NUENVIO está nulo no registro selecionado.");
            }
            BigDecimal nuEnvio = (BigDecimal) nuEnvioObj;

            // Coleta os demais campos (usando fallback para evitar erros de banco nas FKs)
            String assunto = (String) envioEmailIns.getCampo("ASUNTO"); // Nota: no banco costuma ser ASSUNTO, verifique seu dicionário
            String corpo = (String) envioEmailIns.getCampo("CORPO");
            String emailDest = (String) envioEmailIns.getCampo("DESTINATARIO");

            if (emailDest == null || emailDest.trim().isEmpty()) {
                throw new Exception("Erro de validação: O destinatário não foi informado.");
            }

            // 3. Busca de Anexos
            List<Anexo> arquivos = new ArrayList<>();
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
                        AnalisadorDeBlob.FileInfo fileInfo = analisador.analisar(arquivo);
                        arquivos.add(new Anexo(fileInfo, arquivo));
                    }
                }
            } catch (Exception e) {
                throw new Exception("Falha ao consultar ou extrair anexos da tabela AD_TCPANX (NUENVIO: " + nuEnvio + "). Detalhes: " + e.getMessage(), e);
            }

            // 4. Criação da Fila de Mensagens
            DynamicVO filaMensagemVO = null;
            try {
                JapeWrapper filaMensagemDAO = JapeFactory.dao(DynamicEntityNames.FILA_MSG);
                filaMensagemVO = filaMensagemDAO.create()
                        .set("ASSUNTO", assunto != null ? assunto : "Sem Assunto")
                        .set("CODCON", BigDecimal.ZERO)
                        .set("EMAIL", "felipe.lopes@sankhya.com.br")
                        .set("MAXTENTENVIO", new BigDecimal(3))
                        .set("MENSAGEM", corpo != null ? corpo.toCharArray() : new char[0])
                        .set("REENVIAR", "N")
                        .set("STATUS", "Pendente")
                        .set("TIPOENVIO", "E")
                        .set("CODSMTP", new BigDecimal(2))
                        .save();
            } catch (Exception e) {
                throw new Exception("Falha ao gravar registro na Fila de Mensagens (TMDFMG). Detalhes: " + e.getMessage(), e);
            }

            // Inicia montagem da resposta JSON consolidada
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("CODFILA", filaMensagemVO.asBigDecimal("CODFILA"));
            JsonArray jsonArrayAnexos = new JsonArray();

            // 5. Inserção e Vinculação dos Anexos
            for (Anexo arquivo : arquivos) {
                try {
                    // Extrai o Blob e converte todo o seu conteúdo para um array de bytes
                    Blob blob = (Blob) arquivo.getArquivo();
                    byte[] bytesAnexo = blob.getBytes(1, (int) blob.length());

                    JapeWrapper anexoMensagemDAO = JapeFactory.dao(DynamicEntityNames.ANEXO_MENSAGEM);
                    DynamicVO anexoMensagemVO = anexoMensagemDAO.create()
                            .set("ANEXO", bytesAnexo) // <-- Passando o byte[] exigido
                            .set("NOMEARQUIVO", arquivo.getFileInfo().getNome())
                            .set("TIPO", arquivo.getFileInfo().getMimeType())
                            .save();

                    JapeWrapper anexoPorMensagemDAO = JapeFactory.dao(DynamicEntityNames.ANEXO_POR_MENSAGEM);
                    anexoPorMensagemDAO.create()
                            .set("NUANEXO", anexoMensagemVO.asBigDecimal("NUANEXO"))
                            .set("CODFILA", filaMensagemVO.asBigDecimal("CODFILA"))
                            .save();

                    // Adiciona as IDs criadas ao array da resposta JSON
                    JsonObject jsonAnexoMsg = new JsonObject();
                    jsonAnexoMsg.addProperty("NUANEXO", anexoMensagemVO.asBigDecimal("NUANEXO").toString());
                    jsonArrayAnexos.add(jsonAnexoMsg);

                } catch (Exception e) {
                    String nomeArquivo = arquivo.getFileInfo() != null ? arquivo.getFileInfo().getNome() : "Desconhecido";
                    throw new Exception("Falha ao gravar o anexo '" + nomeArquivo + "' ou vinculá-lo à Fila de Mensagens. Detalhes: " + e.getMessage(), e);
                }
            }

            // 6. Configura o JSON Response apenas uma vez, no final da transação
            ServiceContext serviceCtx = ServiceContext.getCurrent();
            if (serviceCtx != null) {
                jsonResponse.add("ANEXOS_CRIADOS", jsonArrayAnexos);
                serviceCtx.setJsonResponse(jsonResponse);
            }

            // Exibe mensagem de sucesso nativa na tela do Sankhya
            ctx.setMensagemRetorno("E-mail preparado com sucesso! Cód. Fila: " + filaMensagemVO.asBigDecimal("CODFILA"));

        } catch (Exception e) {
            e.printStackTrace(); // Loga no standalone.log do JBoss
            throw new Exception(e.getMessage()); // Exibe no balão de erro da UI

        } finally {
            // Fechamento de recursos com proteção contra nulls
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (sql != null) NativeSql.releaseResources(sql); } catch (Exception ignored) {}
            try { if (jdbc != null) JdbcWrapper.closeSession(jdbc); } catch (Exception ignored) {}
            try { JapeSession.close(hnd); } catch (Exception ignored) {}
        }
    }
}