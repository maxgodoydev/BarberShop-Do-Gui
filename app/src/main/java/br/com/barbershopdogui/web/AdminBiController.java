package br.com.barbershopdogui.web;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Painel de BI (sigiloso): só proprietário e equipe técnica (ver SecurityConfig).
 * Devolve números agregados, sem dado pessoal. Toda a conta é feita pela função SQL fn_bi_resumo (testada no PostgreSQL).
 */
@RestController
@RequestMapping("/api/admin/bi")
public class AdminBiController {
    private static final Set<Integer> PERIODS = Set.of(30, 90, 180, 365);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AdminBiController(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public String summary(@RequestParam(defaultValue = "90") int dias) {
        if (!PERIODS.contains(dias)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido. Use 30, 90, 180 ou 365 dias.");
        }
        return jdbc.queryForObject("SELECT fn_bi_resumo(?, ?)::text", String.class, dias, LocalDateTime.now(clock));
    }
}
