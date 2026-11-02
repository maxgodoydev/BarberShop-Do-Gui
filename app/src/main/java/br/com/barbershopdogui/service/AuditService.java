package br.com.barbershopdogui.service;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Trilha de auditoria das ações sensíveis da equipe. Guarda só ids e ações, nunca dados pessoais. */
@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuditService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void log(String user, String action, String detail) {
        jdbc.update("INSERT INTO auditoria(quando,usuario,acao,detalhe) VALUES(?,?,?,?)", LocalDateTime.now(clock).withNano(0),
                user == null ? "sistema" : user, action, detail);
    }
}
