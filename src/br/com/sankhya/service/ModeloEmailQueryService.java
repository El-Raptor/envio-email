package br.com.sankhya.service;

import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.model.ModeloEmail;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

import java.math.BigDecimal;
import java.sql.ResultSet;

/**
 * Serviço responsável por buscar o modelo de e-mail no banco de dados.
 *
 * <p>Esta classe realiza uma consulta nativa (NativeSql) na tabela de modelos
 * de e-mail (TSIMEM) para recuperar as configurações e o conteúdo da mensagem.</p>
 * <p><strong>Compatibilidade:</strong> Sankhya OM 4.35b128</p>
 *
 * @author Felipe Lopes (felipe.lopes@sankhya.com.br)
 * @since 05/08/2026
 */
public class ModeloEmailQueryService {

    /**
     * Constante que define o código do modelo de e-mail fixo a ser buscado (6).
     */
    public static final BigDecimal COD_MODELO_FIXO = new BigDecimal(6);

    /**
     * Busca o modelo de e-mail fixo ({@link #COD_MODELO_FIXO}).
     *
     * @return Instância de ModeloEmail, ou null se não encontrar.
     * @throws Exception Em caso de erro na consulta.
     */
    public ModeloEmail buscarModelo() throws Exception {
        return buscarModelo(COD_MODELO_FIXO);
    }

    /**
     * Busca um modelo de e-mail na TSIMEM pelo código informado.
     *
     * @param codModelo Código do modelo (CODMODELO).
     * @return Instância de ModeloEmail preenchida com os dados do banco, ou null se não encontrar.
     * @throws Exception Em caso de erro na abertura da sessão, execução do SQL ou manipulação do ResultSet.
     */
    public ModeloEmail buscarModelo(BigDecimal codModelo) throws Exception {
        if (codModelo == null) {
            throw new IllegalArgumentException("CODMODELO não informado.");
        }

        JdbcWrapper jdbc = null;
        NativeSql nativeSql = null;
        ResultSet rset = null;
        JapeSession.SessionHandle sessionHandle = null;

        try {
            sessionHandle = JapeSession.open();
            jdbc = EntityFacadeFactory.getDWFFacade().getJdbcWrapper();
            jdbc.openSession();

            nativeSql = new NativeSql(jdbc);
            nativeSql.appendSql("SELECT ASSUNTO, CONTEUDO, CODSMTP, CODUSUREMET ");
            nativeSql.appendSql("  FROM TSIMEM ");
            nativeSql.appendSql(" WHERE CODMODELO = :CODMODELO");
            nativeSql.setNamedParameter("CODMODELO", codModelo);

            rset = nativeSql.executeQuery();
            if (rset.next()) {
                return new ModeloEmail(
                        rset.getString("ASSUNTO"),
                        rset.getString("CONTEUDO"),
                        rset.getBigDecimal("CODSMTP"),
                        rset.getBigDecimal("CODUSUREMET")
                );
            }
            return null;
        } finally {
            if (rset != null) rset.close();
            if (nativeSql != null) NativeSql.releaseResources(nativeSql);
            if (jdbc != null) JdbcWrapper.closeSession(jdbc);
            JapeSession.close(sessionHandle);
        }
    }
}