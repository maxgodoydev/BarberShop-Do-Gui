package br.com.barbershopdogui.service;

import br.com.barbershopdogui.model.WorkWindow;
import br.com.barbershopdogui.web.Dtos.BlockInput;
import br.com.barbershopdogui.web.Dtos.BlockResult;
import br.com.barbershopdogui.web.Dtos.BlockView;
import br.com.barbershopdogui.web.Dtos.WindowInput;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Horários de atendimento (janelas semanais) e bloqueios (férias, folgas). */
@Service
public class ScheduleService {
    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final EmailSender emailSender;

    public ScheduleService(JdbcTemplate jdbc, Clock clock, EmailSender emailSender) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.emailSender = emailSender;
    }

    public List<WindowInput> windows(long professionalId) {
        return jdbc.query("SELECT dia_semana,hora_inicio,hora_fim FROM horarios_atendimento WHERE profissional_id=? ORDER BY dia_semana,hora_inicio",
                (rs, n) -> new WindowInput(rs.getInt("dia_semana"), rs.getObject("hora_inicio", java.time.LocalTime.class),
                        rs.getObject("hora_fim", java.time.LocalTime.class)), professionalId);
    }

    /** Substitui todas as janelas do profissional. Janelas do mesmo dia não podem se sobrepor. */
    @Transactional
    public List<WindowInput> replaceWindows(long professionalId, List<WindowInput> input) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM profissionais WHERE id=?", Integer.class, professionalId);
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profissional não encontrado.");
        }
        for (int day = 1; day <= 7; day++) {
            final int d = day;
            List<WindowInput> sameDay = input.stream().filter(w -> w.diaSemana() == d).sorted(Comparator.comparing(WindowInput::inicio)).toList();
            for (int i = 0; i < sameDay.size(); i++) {
                if (!sameDay.get(i).fim().isAfter(sameDay.get(i).inicio())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Em cada janela, o fim deve ser posterior ao início.");
                }
                if (i > 0 && sameDay.get(i).inicio().isBefore(sameDay.get(i - 1).fim())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Há janelas sobrepostas no mesmo dia.");
                }
            }
        }
        jdbc.update("DELETE FROM horarios_atendimento WHERE profissional_id=?", professionalId);
        for (WindowInput w : input) {
            jdbc.update("INSERT INTO horarios_atendimento(profissional_id,dia_semana,hora_inicio,hora_fim) VALUES(?,?,?,?)",
                    professionalId, w.diaSemana(), w.inicio(), w.fim());
        }
        return windows(professionalId);
    }

    public List<WorkWindow> workWindows(long professionalId, int isoDay) {
        List<WorkWindow> result = new ArrayList<>();
        jdbc.query("SELECT hora_inicio,hora_fim FROM horarios_atendimento WHERE profissional_id=? AND dia_semana=? ORDER BY hora_inicio",
                rs -> {
                    result.add(new WorkWindow(rs.getObject("hora_inicio", java.time.LocalTime.class), rs.getObject("hora_fim", java.time.LocalTime.class)));
                }, professionalId, isoDay);
        return result;
    }

    public List<BlockView> blocks(boolean onlyFuture) {
        String where = onlyFuture ? " WHERE b.fim > ?" : "";
        Object[] args = onlyFuture ? new Object[] {LocalDateTime.now(clock)} : new Object[0];
        return jdbc.query("SELECT b.id,b.profissional_id,p.nome,b.inicio,b.fim,b.motivo FROM bloqueios b LEFT JOIN profissionais p ON p.id=b.profissional_id"
                        + where + " ORDER BY b.inicio",
                (rs, n) -> new BlockView(rs.getLong("id"), (Long) rs.getObject("profissional_id"), rs.getString("nome"),
                        rs.getObject("inicio", LocalDateTime.class), rs.getObject("fim", LocalDateTime.class), rs.getString("motivo")), args);
    }

    /** Cria o bloqueio, cancela os agendamentos ativos no período e notifica os clientes por e-mail. */
    @Transactional
    public BlockResult createBlock(BlockInput in) {
        if (!in.fim().isAfter(in.inicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O fim do bloqueio deve ser posterior ao início.");
        }
        Long id = jdbc.queryForObject("INSERT INTO bloqueios(profissional_id,inicio,fim,motivo,criado_em) VALUES(?,?,?,?,?) RETURNING id", Long.class,
                in.profissionalId(), in.inicio(), in.fim(), in.motivo() == null || in.motivo().isBlank() ? null : in.motivo().trim(), LocalDateTime.now(clock));

        // Busca agendamentos ativos afetados pelo bloqueio para cancelar e notificar
        String selectSql = in.profissionalId() == null
                ? "SELECT a.id, a.inicio, c.email, c.nome FROM agendamentos a JOIN clientes c ON c.id = a.cliente_id"
                  + " WHERE a.status IN ('SOLICITADO','CONFIRMADO') AND a.inicio < ? AND a.fim > ?"
                : "SELECT a.id, a.inicio, c.email, c.nome FROM agendamentos a JOIN clientes c ON c.id = a.cliente_id"
                  + " WHERE a.status IN ('SOLICITADO','CONFIRMADO') AND a.inicio < ? AND a.fim > ? AND a.profissional_id = ?";
        Object[] selectArgs = in.profissionalId() == null
                ? new Object[]{in.fim(), in.inicio()}
                : new Object[]{in.fim(), in.inicio(), in.profissionalId()};

        record AffectedBooking(long id, LocalDateTime inicio, String email, String nomeCliente) {}
        List<AffectedBooking> affected = jdbc.query(selectSql,
                (rs, n) -> new AffectedBooking(
                        rs.getLong("id"),
                        rs.getObject("inicio", LocalDateTime.class),
                        rs.getString("email"),
                        rs.getString("nome")),
                selectArgs);

        if (!affected.isEmpty()) {
            // Cancela todos os agendamentos afetados de uma vez
            String idList = affected.stream().map(b -> String.valueOf(b.id())).collect(Collectors.joining(","));
            jdbc.update("UPDATE agendamentos SET status='CANCELADO', cancelado_por='EQUIPE', atualizado_em=?"
                    + " WHERE id IN (" + idList + ")", LocalDateTime.now(clock));

            // Notifica cada cliente por e-mail (falha de envio não desfaz o cancelamento)
            BlockView view = blocks(false).stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
            String profNome = view.profissional() != null ? view.profissional() : "a barbearia";
            String motivo = in.motivo() != null && !in.motivo().isBlank() ? in.motivo().trim() : "indisponibilidade da agenda";
            for (AffectedBooking booking : affected) {
                try {
                    emailSender.send(
                            booking.email(),
                            "Seu agendamento foi cancelado – Barbearia do Gui",
                            String.format(
                                    "Olá, %s!%n%n"
                                    + "Infelizmente seu agendamento com %s marcado para %s foi cancelado pela equipe.%n"
                                    + "Motivo: %s.%n%n"
                                    + "Pedimos desculpas pelo transtorno. Entre em contato conosco para remarcar.%n%n"
                                    + "Equipe Barbearia do Gui",
                                    booking.nomeCliente(),
                                    profNome,
                                    booking.inicio().toLocalDate(),
                                    motivo));
                } catch (Exception e) {
                    log.warn("Não foi possível enviar e-mail de cancelamento para agendamento {}: {}", booking.id(), e.getClass().getSimpleName()); // getMessage() poderia conter o e-mail do cliente
                }
            }
        }

        BlockView view = blocks(false).stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
        return new BlockResult(view, affected.size());
    }

    public void deleteBlock(long id) {
        if (jdbc.update("DELETE FROM bloqueios WHERE id=?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bloqueio não encontrado.");
        }
    }
}
