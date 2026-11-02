package br.com.barbershopdogui.security;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Bloqueio temporário da conta após falhas consecutivas de senha (padrão: 5 falhas -> 15 minutos). */
@Component
public class LoginAttempts {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int maxFailures;
    private final int lockMinutes;

    public LoginAttempts(JdbcTemplate jdbc, Clock clock, @Value("${app.security.max-login-failures:5}") int maxFailures,
                         @Value("${app.security.lock-minutes:15}") int lockMinutes) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.lockMinutes = lockMinutes;
    }

    public void failure(String username) {
        if (username == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("UPDATE usuarios_admin SET falhas_login = CASE WHEN falhas_login + 1 >= ? THEN 0 ELSE falhas_login + 1 END, "
                + "bloqueado_ate = CASE WHEN falhas_login + 1 >= ? THEN ? ELSE bloqueado_ate END WHERE username=?",
                maxFailures, maxFailures, now.plusMinutes(lockMinutes), username.trim().toLowerCase());
    }

    public void success(String username) {
        jdbc.update("UPDATE usuarios_admin SET falhas_login=0, bloqueado_ate=NULL, ultimo_login_em=? WHERE username=?", LocalDateTime.now(clock), username);
    }
}
