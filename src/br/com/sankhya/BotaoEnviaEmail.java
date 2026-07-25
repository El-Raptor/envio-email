package br.com.sankhya;

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.model.Anexo;
import br.com.sankhya.service.AnexoService;
import br.com.sankhya.service.EmailService;
import br.com.sankhya.ws.ServiceContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class BotaoEnviaEmail implements AcaoRotinaJava {

    private final EmailService emailService = new EmailService();
    private final AnexoService anexoService = new AnexoService();

    @Override
    public void doAction(ContextoAcao ctx) throws Exception {
        JapeSession.SessionHandle hnd = null;

        try {
            hnd = JapeSession.open();

            // 1. Extração e validação do registro
            Registro envioEmailIns = Arrays.stream(ctx.getLinhas())
                    .findFirst()
                    .orElseThrow(() -> new Exception("Erro: Nenhum registro selecionado para envio."));

            Object nuEnvioObj = envioEmailIns.getCampo("NUENVIO");
            if (nuEnvioObj == null) {
                throw new Exception("Erro de validação: O campo NUENVIO está nulo no registro selecionado.");
            }

            BigDecimal nuEnvio = (BigDecimal) nuEnvioObj;
            String assunto = (String) envioEmailIns.getCampo("ASSUNTO");
            String corpo = (String) envioEmailIns.getCampo("CORPO");
            String emailDest = (String) envioEmailIns.getCampo("DESTINATARIO");
            BigDecimal codModelo = (BigDecimal) envioEmailIns.getCampo("CODMODELO");

            if (emailDest == null || emailDest.trim().isEmpty()) {
                throw new Exception("Erro de validação: O destinatário não foi informado.");
            }

            // 2. Busca Conta SMTP e Modelo de E-mail via Service
            BigDecimal codSMTP = emailService.buscarContaSMTPPadrao(ctx.getQuery());

            if (codModelo != null) {
                String[] modelo = emailService.buscarModeloEmail(ctx.getQuery(), codModelo);
                assunto = modelo[0];
                corpo = modelo[1];
            }

            // 3. Processamento na Fila de Mensagem
            DynamicVO filaMensagemVO = emailService.criarFilaMensagem(assunto, corpo, emailDest, codSMTP);
            BigDecimal codFila = filaMensagemVO.asBigDecimal("CODFILA");

            // 4. Processamento dos Anexos
            List<Anexo> arquivos = anexoService.buscarAnexos(nuEnvio);
            JsonArray jsonArrayAnexos = anexoService.processarEVincularAnexos(arquivos, codFila);

            // 5. Constrói o Response
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("CODFILA", codFila);
            jsonResponse.add("ANEXOS_CRIADOS", jsonArrayAnexos);

            ServiceContext serviceCtx = ServiceContext.getCurrent();
            if (serviceCtx != null) {
                serviceCtx.setJsonResponse(jsonResponse);
            }

            ctx.setMensagemRetorno("E-mail preparado com sucesso! Cód. Fila: " + codFila);

        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(e.getMessage());
        } finally {
            JapeSession.close(hnd);
        }
    }
}