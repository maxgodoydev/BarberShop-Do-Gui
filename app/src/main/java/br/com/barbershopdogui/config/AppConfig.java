package br.com.barbershopdogui.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    /** Relógio único da aplicação, no fuso da barbearia. Todos os TIMESTAMPs do banco usam esse fuso. */
    @Bean
    Clock clock(@Value("${app.timezone:America/Sao_Paulo}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
