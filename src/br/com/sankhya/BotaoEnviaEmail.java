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

/**
 * Classe responsável por intermediar a ação customizada de envio de e-mails
 * disparada através de um Botão de Ação no sistema Sankhya-W.
 *
 * Atua como o Controlador (Controller) da operação, orquestrando as chamadas aos
 * serviços de e-mail e de anexos para montar o registro na Fila de Mensagens (TMDFMG).
 *
 * @author Felipe Salles Lopes (felipe.lopes@sankhya.com.br)
 */
public class BotaoEnviaEmail implements AcaoRotinaJava {

    private final EmailService emailService = new EmailService();
    private final AnexoService anexoService = new AnexoService();

    /**
     * Ponto de entrada da rotina Java invocada pelo botão de ação.
     * Realiza a extração dos dados da tela, validações, busca da conta SMTP,
     * aplicação de modelos de e-mail pré-definidos e delega a criação das entidades
     * no banco de dados.
     *
     * Ao final do processo, injeta um JSON no contexto da requisição com os IDs gerados
     * e retorna uma mensagem de sucesso na interface do usuário.
     *
     * @param ctx O contexto da ação, contendo os registros selecionados na interface (grid/formulário) e ferramentas de query.
     * @throws Exception Em caso de falha de validação dos dados de entrada, erros de banco de dados ou processamento de anexos.
     */
    @Override
    public void doAction(ContextoAcao ctx) throws Exception {
        JapeSession.SessionHandle hnd = null;

        try {
            // Abre uma sessão JAPE para garantir que todas as transações da thread ocorram no mesmo contexto
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

            // 6. Atualiza o contexto web com o JSON de resposta (se aplicável)
            ServiceContext serviceCtx = ServiceContext.getCurrent();
            if (serviceCtx != null) {
                serviceCtx.setJsonResponse(jsonResponse);
            }

            // Envia o feedback visual de sucesso para o usuário do Sankhya
            ctx.setMensagemRetorno("E-mail preparado com sucesso! Cód. Fila: " + codFila);

        } catch (Exception e) {
            e.printStackTrace(); // Registra o log completo no standalone.log do servidor
            throw new Exception(e.getMessage()); // Relança a exceção com a mensagem tratada para a interface
        } finally {
            // Garante o fechamento seguro da sessão JAPE, prevenindo vazamentos de conexão
            JapeSession.close(hnd);
        }
    }
}