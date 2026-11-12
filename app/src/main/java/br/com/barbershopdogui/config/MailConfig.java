package br.com.barbershopdogui.config;

import br.com.barbershopdogui.service.BrevoEmailSender;
import br.com.barbershopdogui.service.DeferredEmailSender;
import br.com.barbershopdogui.service.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * MAIL_MODE: dev (só local; imprime o link no log) | smtp | brevo (API HTTPS; use este no Render Free).
 * Em produção (perfil prod) o modo dev é PROIBIDO: o app não sobe, em vez de mandar links de acesso para o log.
 * smtp e brevo são envolvidos por DeferredEmailSender (envio só depois do commit e fora da thread da requisição).
 */
@Configuration
public class MailConfig {
    private static final Logger LOG = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    EmailSender emailSender(@Value("${app.mail.mode:dev}") String mode, @Value("${app.mail.from:}") String from,
                            @Value("${app.mail.brevo-api-key:}") String brevoKey, Environment env,
                            ObjectProvider<JavaMailSender> senderProvider) {
        if ("brevo".equalsIgnoreCase(mode)) {
            if (brevoKey.isBlank() || from.isBlank()) {
                throw new IllegalStateException("MAIL_MODE=brevo exige BREVO_API_KEY e MAIL_FROM.");
            }
            return new DeferredEmailSender(new BrevoEmailSender(from, brevoKey));
        }
        if ("smtp".equalsIgnoreCase(mode)) {
            JavaMailSender sender = senderProvider.getIfAvailable();
            if (sender == null || from.isBlank()) {
                throw new IllegalStateException("MAIL_MODE=smtp exige spring.mail.host (MAIL_HOST) e MAIL_FROM configurados.");
            }
            return new DeferredEmailSender(new EmailSender() {
                @Override
                public void send(String to, String subject, String body) {
                    SimpleMailMessage msg = new SimpleMailMessage();
                    msg.setFrom(from);
                    msg.setTo(to);
                    msg.setSubject(subject);
                    msg.setText(body);
                    sender.send(msg);
                }

                @Override
                public boolean isRealDelivery() {
                    return true;
                }
            });
        }
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("MAIL_MODE=dev não é permitido com SPRING_PROFILES_ACTIVE=prod: "
                    + "os links de acesso (que dão acesso à conta do cliente) iriam para o log. Use MAIL_MODE=brevo (ou smtp em plano pago).");
        }
        LOG.warn("MODO DE DESENVOLVIMENTO DE E-MAIL ATIVO (MAIL_MODE=dev): nenhuma mensagem será enviada de verdade. "
                + "Os links de acesso aparecem somente neste log local.");
        return new EmailSender() {
            @Override
            public void send(String to, String subject, String body) {
                LOG.warn("[DEV] E-mail NÃO enviado (modo desenvolvimento). Assunto: {}\n{}", subject, body);
            }

            @Override
            public boolean isRealDelivery() {
                return false;
            }
        };
    }
}
