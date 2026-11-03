package br.com.barbershopdogui.service;

/** Envio de e-mail. Implementações: SMTP real ou modo de desenvolvimento (não envia nada). */
public interface EmailSender {
    void send(String to, String subject, String body);

    /** true somente quando o e-mail realmente sai para um servidor SMTP. */
    boolean isRealDelivery();
}
