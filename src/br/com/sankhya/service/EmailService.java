package br.com.sankhya.service;

import br.com.sankhya.extensions.actionbutton.QueryExecutor;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;

import java.math.BigDecimal;

public class EmailService {

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