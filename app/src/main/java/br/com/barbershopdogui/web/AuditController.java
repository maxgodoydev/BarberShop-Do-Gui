package br.com.barbershopdogui.web;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Últimas ações sensíveis da equipe (somente proprietário, regra em SecurityConfig). */
@RestController
@RequestMapping("/api/admin/auditoria")
public class AuditController {
    private final JdbcTemplate jdbc;

    public AuditController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> latest() {
        return jdbc.queryForList("SELECT quando,usuario,acao,detalhe FROM auditoria ORDER BY id DESC LIMIT 100");
    }
}
