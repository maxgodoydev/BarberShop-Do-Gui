package br.com.barbershopdogui.web;

import br.com.barbershopdogui.service.AuditService;
import br.com.barbershopdogui.service.CustomerAccessService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Clientes na área administrativa (proprietário e equipe técnica).
 * A equipe técnica vê dados mascarados (privacidade), mas pode bloquear, desbloquear e excluir.
 */
@RestController
@RequestMapping("/api/admin/clientes")
public class AdminCustomerController {
    public record ClientView(Long id, String nome, String email, String telefone, boolean bloqueado,
                             String bloqueioMotivo, LocalDateTime criadoEm, int agendamentosAtivos) { }

    private static final String LIST_SQL = "SELECT c.id, c.nome, c.email, c.telefone, c.bloqueado, c.bloqueio_motivo, c.criado_em, "
            + "(SELECT COUNT(*) FROM agendamentos a WHERE a.cliente_id = c.id AND a.status IN ('SOLICITADO','CONFIRMADO') AND a.fim > ?) "
            + "FROM clientes c WHERE c.excluido_em IS NULL AND (? = '' OR lower(c.nome) LIKE ? OR lower(COALESCE(c.email,'')) LIKE ?) AND (NOT ? OR c.bloqueado) "
            + "ORDER BY c.nome LIMIT 200";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CustomerAccessService access;
    private final AuditService audit;

    public AdminCustomerController(JdbcTemplate jdbc, Clock clock, CustomerAccessService access, AuditService audit) {
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.access = access;
    }

    @GetMapping
    public List<ClientView> list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "false") boolean bloqueados, Authentication auth) {
        String term = q.trim().toLowerCase(Locale.ROOT);
        // Proprietário e recepção (que atende o balcão) veem os dados completos; a equipe técnica vê dados mascarados.
        boolean owner = auth.getAuthorities().stream().anyMatch(a -> "ROLE_PROPRIETARIO".equals(a.getAuthority()) || "ROLE_RECEPCAO".equals(a.getAuthority()));
        return jdbc.query(LIST_SQL, (rs, i) -> new ClientView(rs.getLong(1),
                owner ? rs.getString(2) : mask(rs.getString(2)), owner ? rs.getString(3) : mask(rs.getString(3)),
                owner ? rs.getString(4) : null, rs.getBoolean(5), rs.getString(6),
                rs.getTimestamp(7).toLocalDateTime(), rs.getInt(8)),
                LocalDateTime.now(clock), term, "%" + term + "%", "%" + term + "%", bloqueados);
    }

    /** Quantos clientes estão ativos, bloqueados e quantos excluíram a conta (os dados pessoais dos excluídos são apagados). */
    @GetMapping("/resumo")
    public Map<String, Object> summary() {
        return jdbc.queryForMap("SELECT COUNT(*) FILTER (WHERE excluido_em IS NULL) AS ativos, "
                + "COUNT(*) FILTER (WHERE excluido_em IS NULL AND bloqueado) AS bloqueados, "
                + "COUNT(*) FILTER (WHERE excluido_em IS NOT NULL) AS excluidos, "
                + "COUNT(*) FILTER (WHERE excluido_em >= ?) AS excluidos30dias FROM clientes", LocalDateTime.now(clock).minusDays(30));
    }

    @PostMapping("/{id}/bloquear")
    public Map<String, String> block(@PathVariable long id, java.security.Principal principal) {
        audit.log(principal.getName(), "CLIENTE_BLOQUEAR", "cliente " + id);
        int n = jdbc.update("UPDATE clientes SET bloqueado = TRUE, bloqueado_em = ?, bloqueio_motivo = 'Bloqueio manual pela equipe' "
                + "WHERE id = ? AND excluido_em IS NULL", LocalDateTime.now(clock), id);
        return done(n, "Cliente bloqueado.");
    }

    @PostMapping("/{id}/desbloquear")
    public Map<String, String> unblock(@PathVariable long id, java.security.Principal principal) {
        audit.log(principal.getName(), "CLIENTE_DESBLOQUEAR", "cliente " + id);
        int n = jdbc.update("UPDATE clientes SET bloqueado = FALSE, bloqueado_em = NULL, bloqueio_motivo = NULL "
                + "WHERE id = ? AND excluido_em IS NULL", id);
        return done(n, "Cliente liberado.");
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable long id, java.security.Principal principal) {
        audit.log(principal.getName(), "CLIENTE_EXCLUIR", "cliente " + id);
        String result = access.deleteAccount(id, true);
        if (!"OK".equals(result)) {
            throw new ResponseStatusException("NAO_ENCONTRADO".equals(result) ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                    CustomerAccessService.deleteMessage(result));
        }
        return Map.of("mensagem", "Cadastro excluído e dados pessoais removidos.");
    }

    private static Map<String, String> done(int updated, String message) {
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado.");
        }
        return Map.of("mensagem", message);
    }

    /** "Maria Silva" -> "M*** S***"; "maria@x.com" -> "m***@x.com". */
    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int at = value.indexOf('@');
        if (at > 0) {
            return value.charAt(0) + "***" + value.substring(at);
        }
        StringBuilder sb = new StringBuilder();
        for (String part : value.trim().split("\\s+")) {
            sb.append(sb.length() > 0 ? " " : "").append(part.charAt(0)).append("***");
        }
        return sb.toString();
    }
}
