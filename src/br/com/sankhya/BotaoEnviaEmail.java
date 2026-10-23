package br.com.sankhya;

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.model.Anexo;
import br.com.sankhya.model.ModeloEmail;
import br.com.sankhya.service.AnexoService;
import br.com.sankhya.service.EmailService;
import br.com.sankhya.service.ModeloEmailQueryService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Botão de Ação de envio de e-mail.
 *
 * Orquestra a montagem do {@link ModeloEmail}, a busca dos anexos e o envio
 * através da API nativa do Sankhya ({@code FilaMsgUtil}), via {@link EmailService#enviarEmail}.
 *
 * Regra do modelo:
 * 1. CODMODELO preenchido no registro → assunto/corpo/SMTP/remetente vêm da TSIMEM;
 * 2. CODMODELO vazio → assunto/corpo vêm do próprio registro (ASSUNTO/CORPO);
 * 3. Sem CODSMTP no modelo → usa a conta SMTP padrão (TSISMTP.PADRAO = 'S').
 *
 * @author Felipe Salles Lopes (felipe.lopes@sankhya.com.br)
 */
public class BotaoEnviaEmail implements AcaoRotinaJava {

    private final EmailService emailService = new EmailService();
    private final AnexoService anexoService = new AnexoService();
    private final ModeloEmailQueryService modeloEmailQueryService = new ModeloEmailQueryService();

    @Override
    public void doAction(ContextoAcao ctx) throws Exception {
        JapeSession.SessionHandle hnd = null;

        try {
            hnd = JapeSession.open();

            // 1. Extração e validação do registro
            Registro[] linhas = ctx.getLinhas();
            if (linhas == null || linhas.length == 0) {
                throw new Exception("Erro: Nenhum registro selecionado para envio.");
            }
            Registro envioEmailIns = linhas[0];

            BigDecimal nuEnvio = (BigDecimal) envioEmailIns.getCampo("NUENVIO");
            if (nuEnvio == null) {
                throw new Exception("Erro de validação: O campo NUENVIO está nulo no registro selecionado.");
            }

            String assunto = (String) envioEmailIns.getCampo("ASSUNTO");
            String corpo = (String) envioEmailIns.getCampo("CORPO");
            String destinatarios = (String) envioEmailIns.getCampo("DESTINATARIO");
            BigDecimal codModelo = (BigDecimal) envioEmailIns.getCampo("CODMODELO");

            if (destinatarios == null || destinatarios.trim().isEmpty()) {
                throw new Exception("Erro de validação: O destinatário não foi informado.");
            }

            // 2. Monta o modelo de e-mail
            ModeloEmail modelo = montarModelo(ctx, codModelo, assunto, corpo);

            // 3. Busca os anexos (bytes já carregados em memória)
            List<Anexo> anexos = anexoService.buscarAnexos(nuEnvio);

            // 4. Envia via API nativa (FilaMsgUtil)
            emailService.enviarEmail(destinatarios.trim(), modelo, anexos);

            ctx.setMensagemRetorno("E-mail enviado com sucesso! Anexos: " + anexos.size());

        } catch (Exception e) {
            e.printStackTrace(); // standalone.log
            throw new Exception(e.getMessage());
        } finally {
            JapeSession.close(hnd);
        }
    }

    /**
     * Monta o {@link ModeloEmail} a partir da TSIMEM (se houver CODMODELO) ou dos campos
     * do registro, garantindo uma conta SMTP válida.
     */
    private ModeloEmail montarModelo(ContextoAcao ctx, BigDecimal codModelo,
                                     String assunto, String corpo) throws Exception {
        ModeloEmail modelo;

        if (codModelo != null) {
            modelo = modeloEmailQueryService.buscarModelo(codModelo);
            if (modelo == null) {
                throw new Exception("Modelo de e-mail não encontrado para CODMODELO " + codModelo + ".");
            }
        } else {
            modelo = new ModeloEmail(
                    assunto != null ? assunto : "Sem Assunto",
                    corpo != null ? corpo : "",
                    null,
                    null
            );
        }

        // Fallback: conta SMTP padrão quando o modelo não define uma
        if (modelo.getCodSmtp() == null) {
            BigDecimal codSmtpPadrao = emailService.buscarContaSMTPPadrao(ctx.getQuery());
            if (codSmtpPadrao == null || BigDecimal.ZERO.compareTo(codSmtpPadrao) == 0) {
                throw new Exception("Nenhuma conta SMTP informada no modelo e nenhuma conta padrão (TSISMTP.PADRAO = 'S') cadastrada.");
            }
            modelo = new ModeloEmail(modelo.getAssunto(), modelo.getConteudo(), codSmtpPadrao, modelo.getCodUsuRemet());
        }

        return modelo;
    }
}