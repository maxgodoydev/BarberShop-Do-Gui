package br.com.barbershopdogui.config;

import br.com.barbershopdogui.service.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class MailConfig {
    private static final Logger LOG = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    EmailSender emailSender(@Value("${app.mail.mode:dev}") String mode, @Value("${app.mail.from:}") String from,
                            ObjectProvider<JavaMailSender> senderProvider) {
        if ("smtp".equalsIgnoreCase(mode)) {
            JavaMailSender sender = senderProvider.getIfAvailable();
            if (sender == null || from.isBlank()) {
                throw new IllegalStateException("MAIL_MODE=smtp exige spring.mail.host (MAIL_HOST) e MAIL_FROM configurados.");
            }
            return new EmailSender() {
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
            };
        }
        LOG.warn("MODO DE DESENVOLVIMENTO DE E-MAIL ATIVO (MAIL_MODE=dev): nenhuma mensagem será enviada de verdade. "
                + "Os links de acesso aparecem somente neste log local. Nunca use este modo em produção.");
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
