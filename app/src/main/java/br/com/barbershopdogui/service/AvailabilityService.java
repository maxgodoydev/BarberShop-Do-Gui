package br.com.barbershopdogui.service;

import br.com.barbershopdogui.model.AvailabilityCalculator;
import br.com.barbershopdogui.model.TimeInterval;
import br.com.barbershopdogui.web.Dtos.SlotGroup;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Disponibilidade = janelas do profissional − bloqueios − agendamentos ativos − reservas temporárias não expiradas. */
@Service
public class AvailabilityService {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbc;
    private final ScheduleService schedule;
    private final CatalogService catalog;
    private final int stepMinutes;
    private final int minLeadMinutes;

    public AvailabilityService(JdbcTemplate jdbc, ScheduleService schedule, CatalogService catalog,
                               @Value("${app.booking.slot-step-minutes:15}") int stepMinutes,
                               @Value("${app.booking.min-lead-minutes:60}") int minLeadMinutes) {
        this.jdbc = jdbc;
        this.schedule = schedule;
        this.catalog = catalog;
        this.stepMinutes = stepMinutes;
        this.minLeadMinutes = minLeadMinutes;
    }

    public List<LocalTime> freeStarts(long professionalId, int durationMinutes, LocalDate date, LocalDateTime now) {
        return freeStarts(professionalId, durationMinutes, date, now, minLeadMinutes);
    }

    /** leadMinutes = 0 no balcão (cliente presente); o site público usa a antecedência mínima configurada. */
    public List<LocalTime> freeStarts(long professionalId, int durationMinutes, LocalDate date, LocalDateTime now, int leadMinutes) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<TimeInterval> busy = new ArrayList<>();
        jdbc.query("SELECT inicio,fim FROM bloqueios WHERE (profissional_id=? OR profissional_id IS NULL) AND inicio < ? AND fim > ?",
                rs -> {
                    busy.add(new TimeInterval(rs.getObject("inicio", LocalDateTime.class), rs.getObject("fim", LocalDateTime.class)));
                }, professionalId, dayEnd, dayStart);
        jdbc.query("SELECT inicio,fim FROM agendamentos WHERE profissional_id=? AND inicio < ? AND fim > ? "
                        + "AND (status IN ('CONFIRMADO','CONCLUIDO') OR (status='SOLICITADO' AND (expira_em IS NULL OR expira_em > ?)) OR (status='RESERVA_TEMPORARIA' AND expira_em > ?))",
                rs -> {
                    busy.add(new TimeInterval(rs.getObject("inicio", LocalDateTime.class), rs.getObject("fim", LocalDateTime.class)));
                }, professionalId, dayEnd, dayStart, now, now);
        return AvailabilityCalculator.freeStarts(date, durationMinutes, schedule.workWindows(professionalId, date.getDayOfWeek().getValue()),
                busy, now, stepMinutes, leadMinutes);
    }

    /** Horários livres por profissional que executa TODOS os serviços escolhidos; a duração é a soma. */
    public List<SlotGroup> slots(List<Long> serviceIds, Long professionalId, LocalDate date, LocalDateTime now) {
        return slots(serviceIds, professionalId, date, now, minLeadMinutes);
    }

    public List<SlotGroup> slots(List<Long> serviceIds, Long professionalId, LocalDate date, LocalDateTime now, int leadMinutes) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = serviceIds.stream().distinct().toList();
        int duration = ids.stream().mapToInt(id -> catalog.requireActiveService(id).duracaoMinutos()).sum();
        String marks = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(ids);
        args.add(ids.size());
        args.add(professionalId);
        args.add(professionalId);
        List<SlotGroup> groups = new ArrayList<>();
        jdbc.query("SELECT p.id,p.nome FROM profissionais p JOIN profissional_servicos ps ON ps.profissional_id=p.id "
                        + "WHERE p.ativo=TRUE AND ps.servico_id IN (" + marks + ") GROUP BY p.id,p.nome,p.ordem HAVING COUNT(DISTINCT ps.servico_id)=? "
                        + "AND (?::BIGINT IS NULL OR p.id=?) ORDER BY p.ordem,p.nome",
                rs -> {
                    long pid = rs.getLong("id");
                    List<String> times = freeStarts(pid, duration, date, now, leadMinutes).stream().map(t -> t.format(HHMM)).toList();
                    groups.add(new SlotGroup(pid, rs.getString("nome"), times));
                }, args.toArray());
        return groups;
    }
}
