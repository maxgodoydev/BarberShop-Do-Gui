package br.com.barbershopdogui.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Indicadores operacionais. Definições em docs/metricas.md (as mesmas do pipeline Python e das consultas SQL). */
@Service
public class DashboardService {
    private static final String PEDIDO = "('SOLICITADO','CONFIRMADO','CONCLUIDO','CANCELADO','FALTOU')";
    private static final String DEMANDA = "('SOLICITADO','CONFIRMADO','CONCLUIDO')";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DashboardService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Map<String, Object> dashboard() {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        Map<String, Object> k = jdbc.queryForMap("SELECT COUNT(*) FILTER (WHERE status IN " + PEDIDO + ") pedidos,"
                + " COUNT(*) FILTER (WHERE status='CONCLUIDO') concluidos, COUNT(*) FILTER (WHERE status='CANCELADO') cancelados,"
                + " COUNT(*) FILTER (WHERE status='FALTOU') faltas,"
                + " COUNT(*) FILTER (WHERE status IN " + PEDIDO + " AND inicio < ?) pedidos_vencidos,"
                + " COUNT(*) FILTER (WHERE status='SOLICITADO' AND inicio >= ?) aguardando_confirmacao,"
                + " COUNT(*) FILTER (WHERE status IN ('SOLICITADO','CONFIRMADO') AND inicio < ?) vencidos_sem_desfecho,"
                + " COUNT(*) FILTER (WHERE status='EXPIRADO') reservas_expiradas FROM agendamentos", now, now, now);
        long pedidos = n(k.get("pedidos")), conc = n(k.get("concluidos")), canc = n(k.get("cancelados")), venc = n(k.get("pedidos_vencidos"));
        long expir = n(k.get("reservas_expiradas"));
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("pedidos", pedidos);
        kpis.put("concluidos", conc);
        kpis.put("cancelados", canc);
        kpis.put("faltas", n(k.get("faltas")));
        kpis.put("aguardandoConfirmacao", n(k.get("aguardando_confirmacao")));
        kpis.put("vencidosSemDesfecho", n(k.get("vencidos_sem_desfecho")));
        kpis.put("taxaCancelamentoPct", pct(canc, pedidos));
        kpis.put("taxaConclusaoPct", pct(conc, venc));
        kpis.put("taxaAbandonoReservaPct", pct(expir, expir + pedidos));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kpis", kpis);
        out.put("porStatus", jdbc.queryForList("SELECT status, COUNT(*) quantidade FROM agendamentos GROUP BY status ORDER BY status"));
        out.put("porServico", jdbc.queryForList("SELECT s.nome servico, COUNT(a.id) quantidade FROM servicos s LEFT JOIN agendamentos a ON a.servico_id=s.id AND a.status IN "
                + DEMANDA + " GROUP BY s.id,s.nome ORDER BY quantidade DESC,s.nome LIMIT 8"));
        out.put("porProfissional", jdbc.queryForList("SELECT p.nome profissional, COUNT(a.id) quantidade FROM profissionais p LEFT JOIN agendamentos a ON a.profissional_id=p.id AND a.status IN "
                + DEMANDA + " GROUP BY p.id,p.nome ORDER BY quantidade DESC,p.nome"));
        out.put("porDiaSemana", jdbc.queryForList("SELECT CAST(EXTRACT(ISODOW FROM inicio) AS INTEGER) dia_semana, COUNT(*) quantidade FROM agendamentos WHERE status IN "
                + DEMANDA + " GROUP BY 1 ORDER BY 1"));
        out.put("volumeDiario", jdbc.queryForList("SELECT CAST(inicio AS DATE) AS dia, COUNT(*) quantidade FROM agendamentos WHERE status IN " + DEMANDA
                + " AND inicio >= ? AND inicio < ? GROUP BY 1 ORDER BY 1", now.toLocalDate().minusDays(29).atStartOfDay(), now.toLocalDate().plusDays(1).atStartOfDay())
                .stream().map(m -> Map.<String, Object>of("dia", String.valueOf(m.get("dia")), "quantidade", m.get("quantidade"))).toList());
        out.put("aviso", "Valores de preço cadastrados são de tabela e NÃO representam receita. Só há receita real quando houver registro de pagamentos (fora do escopo atual).");
        return out;
    }

    /** CSV sem dados pessoais (sem nome, e-mail, telefone ou observação). */
    public byte[] exportCsv() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.id, CAST(a.inicio AS DATE) data_inicio, to_char(a.inicio,'HH24:MI') hora_inicio, s.nome servico, p.nome profissional,"
                + " a.duracao_minutos, a.status, a.cancelado_por, to_char(a.criado_em,'YYYY-MM-DD HH24:MI') criado_em FROM agendamentos a JOIN servicos s ON s.id=a.servico_id"
                + " JOIN profissionais p ON p.id=a.profissional_id ORDER BY a.inicio");
        StringBuilder sb = new StringBuilder("id;data_inicio;hora_inicio;servico;profissional;duracao_minutos;status;cancelado_por;criado_em\n");
        for (Map<String, Object> r : rows) {
            for (String c : List.of("id", "data_inicio", "hora_inicio", "servico", "profissional", "duracao_minutos", "status", "cancelado_por", "criado_em")) {
                sb.append(cell(r.get(c))).append(c.equals("criado_em") ? "\n" : ";");
            }
        }
        return ("\uFEFF" + sb).getBytes(StandardCharsets.UTF_8);
    }

    /** Neutraliza injeção de fórmulas em planilhas (=, +, -, @, tab). */
    static String cell(Object value) {
        if (value == null) {
            return "\"\"";
        }
        String s = String.valueOf(value).replace("\r", " ").replace("\n", " ");
        if (!s.isEmpty() && "=+-@\t".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static long n(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static Double pct(long num, long den) {
        return den == 0 ? null : Math.round(num * 1000.0 / den) / 10.0;
    }
}
