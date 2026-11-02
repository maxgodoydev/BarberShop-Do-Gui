package br.com.barbershopdogui.web;

import br.com.barbershopdogui.model.WorkWindow;
import br.com.barbershopdogui.service.AvailabilityService;
import br.com.barbershopdogui.service.ScheduleService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Visão da equipe: quem está disponível no dia e quanto cada profissional atendeu (com os serviços feitos).
 * Valores em reais só aparecem para o proprietário; a recepção vê quantidades.
 */
@RestController
@RequestMapping("/api/admin/equipe")
public class EquipeController {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private final JdbcTemplate jdbc;
    private final ScheduleService schedule;
    private final AvailabilityService availability;
    private final Clock clock;

    public EquipeController(JdbcTemplate jdbc, ScheduleService schedule, AvailabilityService availability, Clock clock) {
        this.jdbc = jdbc;
        this.schedule = schedule;
        this.availability = availability;
        this.clock = clock;
    }

    @GetMapping("/disponibilidade")
    public List<Map<String, Object>> availabilityOfDay(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : jdbc.queryForList("SELECT id,nome FROM profissionais WHERE ativo=TRUE ORDER BY ordem,nome")) {
            long id = ((Number) p.get("id")).longValue();
            List<String> windows = new ArrayList<>();
            for (WorkWindow w : schedule.workWindows(id, data.getDayOfWeek().getValue())) {
                windows.add(w.start().format(HHMM) + "–" + w.end().format(HHMM));
            }
            Integer booked = jdbc.queryForObject("SELECT COUNT(*) FROM agendamentos WHERE profissional_id=? AND inicio >= ? AND inicio < ? AND status IN ('SOLICITADO','CONFIRMADO','CONCLUIDO')",
                    Integer.class, id, data.atStartOfDay(), data.plusDays(1).atStartOfDay());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("profissionalId", id);
            row.put("profissional", p.get("nome"));
            row.put("janelas", windows);
            row.put("agendamentos", booked == null ? 0 : booked);
            row.put("horariosLivres30min", windows.isEmpty() ? 0 : availability.freeStarts(id, 30, data, now, 0).size());
            out.add(row);
        }
        return out;
    }

    @GetMapping("/resumo")
    public List<Map<String, Object>> summary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate, Authentication auth) {
        if (ate.isBefore(de) || de.plusDays(366).isBefore(ate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido (máximo de 1 ano).");
        }
        boolean owner = auth.getAuthorities().stream().anyMatch(a -> "ROLE_PROPRIETARIO".equals(a.getAuthority()));
        LocalDateTime from = de.atStartOfDay(), to = ate.plusDays(1).atStartOfDay();
        List<Map<String, Object>> profs = jdbc.queryForList("SELECT p.id, p.nome, "
                + "COUNT(a.id) FILTER (WHERE a.status='CONCLUIDO') AS concluidos, "
                + "COUNT(a.id) FILTER (WHERE a.status IN ('SOLICITADO','CONFIRMADO')) AS agendados, "
                + "COUNT(a.id) FILTER (WHERE a.status='CANCELADO') AS cancelados, "
                + "COUNT(a.id) FILTER (WHERE a.status='FALTOU') AS faltas, "
                + "COALESCE(SUM(a.preco_referencia) FILTER (WHERE a.status='CONCLUIDO'),0) AS valor "
                + "FROM profissionais p LEFT JOIN agendamentos a ON a.profissional_id=p.id AND a.inicio >= ? AND a.inicio < ? "
                + "GROUP BY p.id,p.nome,p.ordem,p.ativo HAVING p.ativo OR COUNT(a.id) > 0 ORDER BY p.ordem,p.nome", from, to);
        List<Map<String, Object>> services = jdbc.queryForList("SELECT a.profissional_id AS pid, s.nome, COUNT(*) AS quantidade, COALESCE(SUM(x.preco),0) AS valor "
                + "FROM agendamentos a JOIN agendamento_servicos x ON x.agendamento_id=a.id JOIN servicos s ON s.id=x.servico_id "
                + "WHERE a.status='CONCLUIDO' AND a.inicio >= ? AND a.inicio < ? GROUP BY a.profissional_id,s.id,s.nome ORDER BY quantidade DESC,s.nome", from, to);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : profs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("profissionalId", p.get("id"));
            row.put("profissional", p.get("nome"));
            row.put("concluidos", p.get("concluidos"));
            row.put("agendados", p.get("agendados"));
            row.put("cancelados", p.get("cancelados"));
            row.put("faltas", p.get("faltas"));
            if (owner) {
                row.put("valorAtendido", p.get("valor"));
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map<String, Object> s : services) {
                if (((Number) s.get("pid")).longValue() == ((Number) p.get("id")).longValue()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("servico", s.get("nome"));
                    item.put("quantidade", s.get("quantidade"));
                    if (owner) {
                        item.put("valor", s.get("valor"));
                    }
                    list.add(item);
                }
            }
            row.put("servicos", list);
            out.add(row);
        }
        return out;
    }
}
