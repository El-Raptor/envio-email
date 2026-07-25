package br.com.sankhya.service;

import br.com.sankhya.extensions.actionbutton.QueryExecutor;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;

import java.math.BigDecimal;

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