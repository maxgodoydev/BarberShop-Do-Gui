package br.com.barbershopdogui.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Acesso do cliente por link de uso único enviado ao e-mail. Não há senha de cliente. */
@Service
public class CustomerAccessService {
    private static final Logger LOG = LoggerFactory.getLogger(CustomerAccessService.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final EmailSender mail;
    private final String baseUrl;
    private final int ttlMinutes;

    public CustomerAccessService(JdbcTemplate jdbc, Clock clock, EmailSender mail,
                                 @Value("${app.public-base-url:http://localhost:8080}") String baseUrl,
                                 @Value("${app.access-link.ttl-minutes:15}") int ttlMinutes) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.mail = mail;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.ttlMinutes = ttlMinutes;
    }

    /** Sempre responde igual, exista ou não o e-mail, para não revelar quem é cliente. */
    @Transactional
    public void requestLink(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        List<Long> ids = jdbc.queryForList("SELECT id FROM clientes WHERE lower(email)=?", Long.class, email);
        if (ids.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        jdbc.update("UPDATE tokens_acesso SET usado_em=? WHERE cliente_id=? AND finalidade='CONSULTA' AND usado_em IS NULL", now, ids.get(0)); // invalida só links de acesso anteriores (não o de confirmação pendente)
        String token = Tokens.random();
        jdbc.update("INSERT INTO tokens_acesso(cliente_id,token_hash,criado_em,expira_em) VALUES(?,?,?,?)", ids.get(0), Tokens.sha256(token), now, now.plusMinutes(ttlMinutes));
        // O token vai no fragmento (#), que o navegador não envia ao servidor, e a página o troca por sessão via POST.
        String link = baseUrl + "/consulta.html#token=" + token;
        try {
            mail.send(email, "Seu acesso ao espaço do cliente",
                    "Use este link para entrar no seu espaço na BarberShop do Gui, sem senha: seus dados, agendamentos e produtos. Ele vale por " + ttlMinutes + " minutos e só funciona uma vez:\n\n"
                            + link + "\n\nSe você não pediu este link, ignore esta mensagem.");
        } catch (RuntimeException ex) {
            LOG.warn("Falha ao enviar o link de acesso ({}).", ex.getClass().getSimpleName());
        }
    }

    /** Consome o token de forma atômica: um único UPDATE ... RETURNING garante uso único mesmo com requisições simultâneas. */
    @Transactional
    public Long redeem(String token) {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        List<Long> ids = jdbc.queryForList("UPDATE tokens_acesso SET usado_em=? WHERE token_hash=? AND finalidade='CONSULTA' AND usado_em IS NULL AND expira_em > ? RETURNING cliente_id",
                Long.class, now, Tokens.sha256(token), now);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Dados do próprio cliente para o espaço dele. */
    public Map<String, Object> profile(long clientId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT nome,email,telefone,criado_em FROM clientes WHERE id=? AND excluido_em IS NULL", clientId);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("nome", rows.get(0).get("nome"));
        out.put("email", rows.get(0).get("email"));
        out.put("telefone", rows.get(0).get("telefone"));
        out.put("clienteDesde", String.valueOf(rows.get(0).get("criado_em")));
        return out;
    }

    /** Exclui (anonimiza) o cadastro. A regra fica na função SQL fn_excluir_cliente (testada no PostgreSQL). */
    public String deleteAccount(long clientId, boolean byAdmin) {
        return jdbc.queryForObject("SELECT fn_excluir_cliente(?,?,?)", String.class, clientId, byAdmin, LocalDateTime.now(clock));
    }

    public static String deleteMessage(String result) {
        return switch (result) {
            case "AGENDAMENTOS_ATIVOS" -> "Há agendamentos futuros. Cancele-os (com 1 dia de antecedência) antes de excluir o cadastro.";
            case "BLOQUEADO" -> "Cadastro bloqueado: fale com a barbearia para liberar antes de excluir.";
            default -> "Cadastro não encontrado.";
        };
    }
}
