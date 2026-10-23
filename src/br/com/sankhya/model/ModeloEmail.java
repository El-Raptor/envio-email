package br.com.sankhya.model; 

import java.math.BigDecimal; 

/**
 * Classe de modelo responsável por armazenar as informações de configuração e conteúdo
 * de um template de e-mail.
 *
 * <p>Esta classe agrupa os dados necessários para a composição da mensagem,
 * incluindo assunto, corpo do e-mail, código da configuração SMTP e remetente.</p>
 * <p><strong>Compatibilidade:</strong> Sankhya OM 4.35b128</p>
 *
 * @author Felipe Lopes (felipe.lopes@sankhya.com.br)
 * @since 05/08/2026
 */
public class ModeloEmail { 
    private String assunto; 
    private String conteudo; 
    private BigDecimal codSmtp; 
    private BigDecimal codUsuRemet; 

    /**
     * Construtor da classe ModeloEmail.
     *
     * @param assunto     Assunto principal do e-mail.
     * @param conteudo    Corpo de texto ou HTML do e-mail.
     * @param codSmtp     Código de configuração do servidor SMTP.
     * @param codUsuRemet Código numérico do usuário remetente.
     */
    public ModeloEmail(String assunto, String conteudo, BigDecimal codSmtp, BigDecimal codUsuRemet) { 
        this.assunto = assunto; 
        this.conteudo = conteudo; 
        this.codSmtp = codSmtp; 
        this.codUsuRemet = codUsuRemet; 
    }

    /**
     * @return O assunto atual do e-mail.
     */
    public String getAssunto() {
        return assunto;
    } 

    /**
     * @param assunto Define um novo assunto para o e-mail.
     */
    public void setAssunto(String assunto) {
        this.assunto = assunto;
    } 

    /**
     * @return O conteúdo (corpo) do e-mail.
     */
    public String getConteudo() {
        return conteudo;
    } 

    /**
     * @param conteudo Define o novo conteúdo para o e-mail.
     */
    public void setConteudo(String conteudo) {
        this.conteudo = conteudo;
    } 

    /**
     * @return O código numérico da configuração SMTP.
     */
    public BigDecimal getCodSmtp() {
        return codSmtp;
    } 

    /**
     * @return O código numérico do usuário remetente.
     */
    public BigDecimal getCodUsuRemet() {
        return codUsuRemet;
    } 
}