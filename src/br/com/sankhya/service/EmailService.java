package br.com.sankhya.service;

import br.com.sankhya.extensions.actionbutton.QueryExecutor;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.model.Anexo;
import br.com.sankhya.model.ModeloEmail;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.modelcore.util.email.FilaMsgUtil;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço responsável por encapsular as regras de negócio e as interações com o
 * banco de dados relacionadas ao envio de e-mails.
 *
 * Centraliza as consultas de configurações do sistema (Contas SMTP e Modelos de E-mail)
 * e a criação de registros na Fila de Mensagens (TMDFMG) através do framework JAPE.
 *
 * @author Felipe Salles Lopes (felipe.lopes@sankhya.com.br)
 */
public class EmailService {

    private final BlobService blobService = new BlobService();

    /**
     * Envia e-mail via API nativa do Sankhya (FilaMsgUtil), anexando os arquivos
     * já sem o cabeçalho de metadados customizado.
     *
     * @param destinatarios E-mails dos destinatários (vários separados pelo separador aceito pelo FilaMsgUtil).
     * @param modelo        Modelo de e-mail (assunto, conteúdo, SMTP, remetente).
     * @param anexos        Lista de anexos (pode ser nula ou vazia).
     * @throws Exception Em caso de falha na leitura dos anexos ou no envio.
     */
    public void enviarEmail(String destinatarios, ModeloEmail modelo, List<Anexo> anexos) throws Exception {
        if (destinatarios == null || destinatarios.trim().isEmpty())
            throw new Exception("Favor adicionar destinatário.");
        if (modelo == null)
            throw new Exception("Modelo de e-mail não encontrado.");

        FilaMsgUtil.Email email = new FilaMsgUtil.Email();
        email.setAssunto(modelo.getAssunto());
        email.setMensagem(modelo.getConteudo());
        email.setDestinatarios(destinatarios);

        if (modelo.getCodSmtp() != null)
            email.setContaSMTP(modelo.getCodSmtp());

        if (modelo.getCodUsuRemet() != null)
            email.setCodUsuRemet(modelo.getCodUsuRemet());

        // Anexos são opcionais
        if (anexos != null) {
            // for tradicional: lambda não permite propagar a Exception checada do BlobService
            for (Anexo anexo : anexos) {
                if (anexo == null || anexo.getArquivo() == null)
                    continue;

                String nomeArquivo = anexo.getFileInfo() != null ? anexo.getFileInfo().getNome() : "nome_desconhecido";
                String mimeType = anexo.getFileInfo() != null ? anexo.getFileInfo().getMimeType() : "application/octet-stream";

                try {
                    // Blob -> byte[] sem o cabeçalho __start/end_fileinformation__
                    byte[] bytesLimpos = blobService.extrairBytesLimpos(anexo.getArquivo());

                    email.addAnexo(
                            new ByteArrayInputStream(bytesLimpos),
                            nomeArquivo,
                            mimeType
                    );
                } catch (Exception e) {
                    throw new Exception("Falha ao anexar o arquivo '" + nomeArquivo + "'. Detalhes: " + e.getMessage(), e);
                }
            }
        }

        FilaMsgUtil.enviaEmail(EntityFacadeFactory.getDWFFacade(), email);
    }


    /**
     * Busca o código da conta SMTP configurada como padrão no sistema.
     * Consulta realizada na tabela TSISMTP.
     *
     * @param query Instância de QueryExecutor fornecida pelo contexto da ação.
     * @return O código (CODSMTP) da conta padrão. Retorna BigDecimal.ZERO caso não exista uma conta marcada como padrão.
     * @throws Exception Em caso de falha na execução da consulta SQL.
     */
    public BigDecimal buscarContaSMTPPadrao(QueryExecutor query) throws Exception {
        try {
            String select = "SELECT CODSMTP FROM TSISMTP WHERE PADRAO = 'S'";
            query.nativeSelect(select);
            if (query.next()) {
                return query.getBigDecimal("CODSMTP");
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            throw new Exception("Falha ao obter código da conta SMTP padrão.", e);
        } finally {
            if (query != null) query.close();
        }
    }

    /**
     * Busca o assunto e o conteúdo de um modelo de e-mail pré-cadastrado no sistema.
     * Consulta realizada na tabela TSIMEM.
     *
     * @param query Instância de QueryExecutor fornecida pelo contexto da ação.
     * @param codModelo O código identificador do modelo de e-mail (CODMODELO).
     * @return Um array de String contendo duas posições: o índice [0] é o Assunto e o índice [1] é o Corpo do e-mail.
     * @throws Exception Caso o modelo não seja encontrado ou ocorra erro na execução da consulta SQL.
     */
    public String[] buscarModeloEmail(QueryExecutor query, BigDecimal codModelo) throws Exception {
        String[] modelo = new String[2]; // Index 0: Assunto, Index 1: Corpo
        try {
            query.setParam("CODMODELO", codModelo);
            String select = "SELECT ASSUNTO, CONTEUDO FROM TSIMEM WHERE CODMODELO = {CODMODELO}";
            query.nativeSelect(select);

            if (query.next()) {
                modelo[0] = query.getString("ASSUNTO");
                modelo[1] = query.getString("CONTEUDO");
            } else {
                throw new Exception("Modelo de e-mail não encontrado para código modelo " + codModelo + ".");
            }
            return modelo;
        } catch (Exception e) {
            throw new Exception("Falha ao obter modelo de e-mail da tabela TSIMEM (CODMODELO = " + codModelo + "). " + e.getMessage(), e);
        } finally {
            if (query != null) query.close();
        }
    }

    /**
     * Cria um novo registro na Fila de Mensagens do Sankhya (tabela TMDFMG).
     *
     * O e-mail é criado com status "Pendente" para que a rotina nativa do Sankhya
     * realize o envio assíncrono posteriormente.
     *
     * @param assunto O assunto do e-mail. Se nulo, define como "Sem Assunto".
     * @param corpo O conteúdo da mensagem (corpo do e-mail).
     * @param emailDest O endereço de e-mail do destinatário.
     * @param codSMTP O código da conta SMTP que será utilizada para o disparo.
     * @return Um DynamicVO representando o registro recém-criado na fila de mensagens.
     * @throws Exception Em caso de violação de restrições do banco ou erro no framework JAPE durante a persistência.
     */
    public DynamicVO criarFilaMensagem(String assunto, String corpo, String emailDest, BigDecimal codSMTP) throws Exception {
        try {
            JapeWrapper filaMensagemDAO = JapeFactory.dao(DynamicEntityNames.FILA_MSG);
            return filaMensagemDAO.create()
                    .set("ASSUNTO", assunto != null ? assunto : "Sem Assunto")
                    .set("CODCON", BigDecimal.ZERO)
                    .set("EMAIL", emailDest)
                    .set("MAXTENTENVIO", new BigDecimal(3))
                    .set("MENSAGEM", corpo != null ? corpo.toCharArray() : new char[0])
                    .set("REENVIAR", "N")
                    .set("STATUS", "Pendente")
                    .set("TIPOENVIO", "E")
                    .set("CODSMTP", codSMTP)
                    .save();
        } catch (Exception e) {
            throw new Exception("Falha ao gravar registro na Fila de Mensagens (TMDFMG). Detalhes: " + e.getMessage(), e);
        }
    }
}