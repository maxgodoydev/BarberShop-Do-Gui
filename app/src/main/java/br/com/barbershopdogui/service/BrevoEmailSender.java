package br.com.barbershopdogui.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

/**
 * E-mail por API HTTPS (porta 443). O Render Free bloqueia SMTP (25/465/587) desde 26/09/2025, então MAIL_MODE=smtp não funciona lá.
 * Provedor: Brevo (https://developers.brevo.com). Precisa de BREVO_API_KEY e de um remetente (MAIL_FROM) verificado no provedor.
 * Sem dependências extras (java.net.http). Timeouts curtos: nunca trava a aplicação.
 */
public class BrevoEmailSender implements EmailSender {
    private static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String from;
    private final String apiKey;

    public BrevoEmailSender(String from, String apiKey) {
        this.from = from;
        this.apiKey = apiKey;
    }

    @Override
    public void send(String to, String subject, String body) {
        String json = "{\"sender\":{\"name\":\"BarberShop do Gui\",\"email\":" + q(from) + "},"
                + "\"to\":[{\"email\":" + q(to) + "}],\"subject\":" + q(subject) + ",\"textContent\":" + q(body) + "}";
        HttpRequest req = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(10))
                .header("accept", "application/json").header("content-type", "application/json").header("api-key", apiKey)
                .POST(BodyPublishers.ofString(json)).build();
        try {
            HttpResponse<Void> res = http.send(req, BodyHandlers.discarding());
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("Provedor de e-mail respondeu HTTP " + res.statusCode());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha de rede ao enviar e-mail", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envio de e-mail interrompido", e);
        }
    }

    @Override
    public boolean isRealDelivery() {
        return true;
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
