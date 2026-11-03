package br.com.barbershopdogui.service;

import br.com.barbershopdogui.model.BookingRules;
import br.com.barbershopdogui.model.BookingStatus;
import br.com.barbershopdogui.web.Dtos.AdminBooking;
import br.com.barbershopdogui.web.Dtos.BookingResult;
import br.com.barbershopdogui.web.Dtos.CounterBookingRequest;
import br.com.barbershopdogui.web.Dtos.CustomerBooking;
import br.com.barbershopdogui.web.Dtos.HoldRequest;
import br.com.barbershopdogui.web.Dtos.HoldResponse;
import br.com.barbershopdogui.model.NoShowPolicy;
import br.com.barbershopdogui.model.ServiceSelectionRules;
import br.com.barbershopdogui.web.Dtos.ProductOrder;
import br.com.barbershopdogui.web.Dtos.SubmitRequest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fluxo: reserva temporária (5 min) -> solicitação enviada (SOLICITADO) -> confirmação pela equipe (CONFIRMADO) -> CONCLUIDO.
 * A exclusão de sobreposição é garantida pelo PostgreSQL (restrição EXCLUDE); a checagem aqui só dá uma resposta amigável.
 */
@Service
public class BookingService {
    private static final Logger LOG = LoggerFactory.getLogger(BookingService.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CatalogService catalog;
    private final AvailabilityService availability;
    private final EmailSender mail;
    private final AuditService audit;
    private final int holdMinutes;
    private final int horizonDays;
    private final int cancelMinHours;
    private final int confirmMinutes;
    private final String baseUrl;

    public BookingService(JdbcTemplate jdbc, Clock clock, CatalogService catalog, AvailabilityService availability, EmailSender mail, AuditService audit,
                          @Value("${app.booking.hold-minutes:10}") int holdMinutes,
                          @Value("${app.booking.horizon-days:60}") int horizonDays,
                          @Value("${app.booking.cancel-min-hours:24}") int cancelMinHours,
                          @Value("${app.booking.confirm-minutes:30}") int confirmMinutes,
                          @Value("${app.public-base-url:http://localhost:8080}") String baseUrl) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.catalog = catalog;
        this.availability = availability;
        this.mail = mail;
        this.audit = audit;
        this.holdMinutes = holdMinutes;
        this.horizonDays = horizonDays;
        this.cancelMinHours = cancelMinHours;
        this.confirmMinutes = confirmMinutes;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    // ------------------------------------------------------------------ reserva temporária
    private record Svc(long id, String nome, BigDecimal preco, int minutos) { }

    private List<Svc> loadServices(List<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        String marks = String.join(",", java.util.Collections.nCopies(distinct.size(), "?"));
        Map<Long, Svc> byId = new HashMap<>();
        jdbc.query("SELECT id,nome,preco,duracao_minutos FROM servicos WHERE ativo=TRUE AND id IN (" + marks + ")",
                rs -> { byId.put(rs.getLong("id"), new Svc(rs.getLong("id"), rs.getString("nome"), rs.getBigDecimal("preco"), rs.getInt("duracao_minutos"))); },
                distinct.toArray());
        if (byId.size() != distinct.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Um dos serviços escolhidos não está disponível.");
        }
        return distinct.stream().map(byId::get).toList();
    }

    /** Aplica as regras: máx. 3 serviços, máx. 200 min, sem repetir nem combinar serviços que se excluem. */
    private void validateSelection(List<Svc> services) {
        Map<Long, Set<Long>> excludes = new HashMap<>();
        String marks = String.join(",", java.util.Collections.nCopies(services.size(), "?"));
        jdbc.query("SELECT servico_id,exclui_id FROM servico_exclusoes WHERE servico_id IN (" + marks + ")",
                rs -> { excludes.computeIfAbsent(rs.getLong(1), k -> new HashSet<>()).add(rs.getLong(2)); },
                services.stream().map(Svc::id).toArray());
        String error = ServiceSelectionRules.validate(services.stream()
                .map(x -> new ServiceSelectionRules.Item(x.id(), x.minutos(), excludes.getOrDefault(x.id(), Set.of()))).toList());
        if (error != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
        }
    }

    /**
     * Fila anti-duplicidade: o bloqueio consultivo (advisory lock) por profissional faz pedidos simultâneos para o mesmo profissional
     * esperarem a vez, dentro da transação. O segundo pedido, ao entrar, já enxerga a reserva do primeiro e recebe "horário indisponível".
     * A restrição EXCLUDE do PostgreSQL continua como garantia final.
     */
    private void requireProfessionalExecutes(Long professionalId, List<Svc> services) {
        Integer executes = jdbc.queryForObject("SELECT COUNT(DISTINCT ps.servico_id) FROM profissionais p JOIN profissional_servicos ps ON ps.profissional_id=p.id "
                + "WHERE p.id=? AND p.ativo=TRUE AND ps.servico_id IN (" + String.join(",", java.util.Collections.nCopies(services.size(), "?")) + ")", Integer.class,
                java.util.stream.Stream.concat(java.util.stream.Stream.of((Object) professionalId), services.stream().map(x -> (Object) x.id())).toArray());
        if (executes == null || executes != services.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este profissional não executa todos os serviços escolhidos.");
        }
    }

    @Transactional
    public HoldResponse createHold(HoldRequest request) {
        LocalDateTime now = now();
        List<Svc> services = loadServices(request.servicoIds());
        validateSelection(services);
        requireProfessionalExecutes(request.profissionalId(), services);
        if (!BookingRules.withinBookingHorizon(request.inicio(), now, horizonDays)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma data futura dentro dos próximos " + horizonDays + " dias.");
        }
        jdbc.execute("SELECT pg_advisory_xact_lock(" + request.profissionalId().longValue() + ")"); // entra na fila deste profissional
        releaseExpired(now, request.profissionalId());
        int minutes = services.stream().mapToInt(Svc::minutos).sum();
        BigDecimal total = services.stream().map(Svc::preco).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime start = request.inicio().withSecond(0).withNano(0);
        if (!availability.freeStarts(request.profissionalId(), minutes, start.toLocalDate(), now).contains(start.toLocalTime())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esse horário não está mais disponível. Escolha outro.");
        }
        String token = Tokens.random();
        LocalDateTime expires = now.plusMinutes(holdMinutes);
        LocalDateTime end = start.plusMinutes(minutes);
        try {
            Long id = jdbc.queryForObject("INSERT INTO agendamentos(servico_id,profissional_id,inicio,fim,duracao_minutos,preco_referencia,status,"
                            + "reserva_token_hash,expira_em,criado_em,atualizado_em) VALUES(?,?,?,?,?,?,'RESERVA_TEMPORARIA',?,?,?,?) RETURNING id", Long.class,
                    services.get(0).id(), request.profissionalId(), start, end, minutes, total, Tokens.sha256(token), expires, now, now);
            int order = 1;
            for (Svc x : services) {
                jdbc.update("INSERT INTO agendamento_servicos(agendamento_id,servico_id,ordem,preco,duracao_minutos) VALUES(?,?,?,?,?)", id, x.id(), order++, x.preco(), x.minutos());
            }
            return new HoldResponse(id, token, start, end, expires, minutes, total);
        } catch (DataAccessException ex) {
            if (isConflict(ex)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Alguém reservou esse horário agora há pouco. Escolha outro.");
            }
            throw ex;
        }
    }

    /** O cliente voltou ou fechou o pop-up: devolve o horário para a agenda sem esperar os minutos da reserva. */
    @Transactional
    public void releaseHold(long reservaId, String token) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT reserva_token_hash FROM agendamentos WHERE id=? AND status='RESERVA_TEMPORARIA' FOR UPDATE", reservaId);
        if (!rows.isEmpty() && Tokens.sameHash(Tokens.sha256(token), (String) rows.get(0).get("reserva_token_hash"))) {
            jdbc.update("UPDATE agendamentos SET status='EXPIRADO', expira_em=NULL, reserva_token_hash=NULL, atualizado_em=? WHERE id=?", now(), reservaId);
        }
    }

    /** Libera reservas vencidas e pedidos que não foram confirmados pelo link a tempo (do profissional, ou de todos se null). */
    private void releaseExpired(LocalDateTime now, Long professionalId) {
        jdbc.update("UPDATE agendamentos SET status='EXPIRADO', expira_em=NULL, reserva_token_hash=NULL, atualizado_em=? "
                + "WHERE ((status='RESERVA_TEMPORARIA') OR (status='SOLICITADO' AND confirmado_via_link_em IS NULL)) "
                + "AND expira_em IS NOT NULL AND expira_em <= ? AND (?::BIGINT IS NULL OR profissional_id=?)", now, now, professionalId, professionalId);
    }

    // ------------------------------------------------------------------ envio da solicitação + link mágico
    @Transactional
    public BookingResult submit(SubmitRequest request) {
        LocalDateTime now = now();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status,expira_em,reserva_token_hash,inicio,profissional_id FROM agendamentos WHERE id=? FOR UPDATE", request.reservaId());
        String generic = "Reserva não encontrada. Escolha o horário novamente.";
        if (rows.isEmpty() || !Tokens.sameHash(Tokens.sha256(request.token()), (String) rows.get(0).get("reserva_token_hash"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, generic);
        }
        BookingStatus status = BookingStatus.parse((String) rows.get(0).get("status"));
        LocalDateTime expires = (LocalDateTime) toLocal(rows.get(0).get("expira_em"));
        if (status != BookingStatus.RESERVA_TEMPORARIA || expires == null || !expires.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "O tempo da reserva terminou. Escolha o horário novamente.");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String fullName = (request.nome().trim() + " " + request.sobrenome().trim()).trim();
        String phone = request.telefone() == null || request.telefone().isBlank() ? null : request.telefone().trim();
        String note = request.observacao() == null || request.observacao().isBlank() ? null : request.observacao().trim();
        String allergies = request.alergias() == null || request.alergias().isBlank() ? null : request.alergias().trim();
        Long clientId = jdbc.queryForObject("INSERT INTO clientes(nome,email,telefone,criado_em) VALUES(?,?,?,?) "
                        + "ON CONFLICT ((lower(email))) DO UPDATE SET telefone=COALESCE(clientes.telefone, EXCLUDED.telefone) RETURNING id", Long.class,
                fullName, email, phone, now);
        saveProducts(request.reservaId(), request.produtos());
        LocalDateTime confirmUntil = now.plusMinutes(confirmMinutes);
        try {
            jdbc.update("UPDATE agendamentos SET cliente_id=?,status='SOLICITADO',observacao=?,alergias=?,expira_em=?,reserva_token_hash=NULL,atualizado_em=? WHERE id=?",
                    clientId, note, allergies, confirmUntil, now, request.reservaId());
        } catch (DataAccessException ex) {
            if (isSqlState(ex, "P0001")) { // gatilho: cadastro bloqueado
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível concluir o agendamento online. Por favor, entre em contato diretamente com a barbearia.");
            }
            throw ex;
        }
        String link = issueConfirmationLink(clientId, request.reservaId(), now, confirmUntil);
        sendConfirmationMail(email, request.nome().trim(), request.reservaId(), link);
        return new BookingResult(request.reservaId(), BookingStatus.SOLICITADO.name(),
                "Quase lá! Enviamos um link de confirmação para " + email + ". Abra-o em até " + confirmMinutes
                        + " minutos para confirmar o horário e entrar no seu espaço, sem senha; sem a confirmação, o horário é liberado.");
    }

    private void saveProducts(long bookingId, List<ProductOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        for (ProductOrder o : orders) {
            if (!seen.add(o.produtoId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto repetido na lista.");
            }
            List<Map<String, Object>> p = jdbc.queryForList("SELECT p.preco, p.estoque - COALESCE((SELECT SUM(ap.quantidade) FROM agendamento_produtos ap "
                    + "JOIN agendamentos a ON a.id = ap.agendamento_id WHERE ap.produto_id = p.id AND a.status IN ('SOLICITADO','CONFIRMADO') AND a.id <> ?), 0) AS disponivel "
                    + "FROM produtos p WHERE p.id=? AND p.ativo=TRUE FOR UPDATE OF p", bookingId, o.produtoId());
            if (p.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto indisponível.");
            }
            int disponivel = ((Number) p.get(0).get("disponivel")).intValue();
            if (disponivel < o.quantidade()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Estoque insuficiente para um dos produtos escolhidos.");
            }
            jdbc.update("INSERT INTO agendamento_produtos(agendamento_id,produto_id,quantidade,preco_unitario) VALUES(?,?,?,?)",
                    bookingId, o.produtoId(), o.quantidade(), p.get(0).get("preco"));
        }
    }

    /** Link mágico: o token vai no fragmento (#), que o navegador não envia ao servidor. Só o hash fica no banco. */
    private String issueConfirmationLink(long clientId, long bookingId, LocalDateTime now, LocalDateTime until) {
        String token = Tokens.random();
        jdbc.update("INSERT INTO tokens_acesso(cliente_id,token_hash,criado_em,expira_em,finalidade,agendamento_id) VALUES(?,?,?,?,'CONFIRMACAO',?)",
                clientId, Tokens.sha256(token), now, until, bookingId);
        return baseUrl + "/confirmar.html#token=" + token;
    }

    private void sendConfirmationMail(String email, String firstName, long bookingId, String link) {
        List<Map<String, Object>> r = jdbc.queryForList("SELECT a.inicio, p.nome profissional, "
                + "(SELECT string_agg(s.nome, ' + ' ORDER BY x.ordem) FROM agendamento_servicos x JOIN servicos s ON s.id=x.servico_id WHERE x.agendamento_id=a.id) servicos "
                + "FROM agendamentos a JOIN profissionais p ON p.id=a.profissional_id WHERE a.id=?", bookingId);
        String when = ((LocalDateTime) toLocal(r.get(0).get("inicio"))).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));
        try {
            mail.send(email, "Confirme seu agendamento na BarberShop do Gui",
                    "Olá, " + firstName + "!\n\nRecebemos seu pedido: " + r.get(0).get("servicos") + " com " + r.get(0).get("profissional") + " em " + when + ".\n\n"
                            + "Para confirmar o horário e entrar no seu espaço (sem senha), abra o link (vale " + confirmMinutes + " minutos e só funciona uma vez):\n\n" + link
                            + "\n\nSe não foi você, ignore esta mensagem: nada será confirmado e o horário volta para a agenda.");
        } catch (RuntimeException ex) {
            LOG.warn("Falha ao enviar o link de confirmação ({}).", ex.getClass().getSimpleName());
        }
    }

    public record Confirmation(BookingResult result, long clientId) { }

    /** Cliente clicou no link do e-mail: consome o token (uso único), confirma o agendamento e devolve o cliente para abrir a sessão. */
    @Transactional
    public Confirmation confirmByLink(String token) {
        LocalDateTime now = now();
        List<Map<String, Object>> rows = jdbc.queryForList("UPDATE tokens_acesso SET usado_em=? WHERE token_hash=? AND finalidade='CONFIRMACAO' AND usado_em IS NULL AND expira_em > ? RETURNING agendamento_id,cliente_id",
                now, Tokens.sha256(token), now);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Link inválido, expirado ou já utilizado. Se já confirmou, peça um novo acesso em \"Meu espaço\"; se o prazo passou, faça o agendamento novamente.");
        }
        long bookingId = ((Number) rows.get(0).get("agendamento_id")).longValue();
        long clientId = ((Number) rows.get(0).get("cliente_id")).longValue();
        int n;
        try {
            n = jdbc.update("UPDATE agendamentos SET status='CONFIRMADO',confirmado_via_link_em=?,expira_em=NULL,atualizado_em=? WHERE id=? AND status='SOLICITADO' AND inicio > ?",
                    now, now, bookingId, now);
        } catch (DataAccessException ex) {
            if (isSqlState(ex, "P0001")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cadastro bloqueado por falta ou atraso. Procure a barbearia para liberar.");
            }
            throw ex;
        }
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.GONE, "Este agendamento não pode mais ser confirmado (expirou ou foi cancelado). Faça um novo agendamento.");
        }
        return new Confirmation(new BookingResult(bookingId, BookingStatus.CONFIRMADO.name(), "Horário confirmado! Você já está dentro do seu espaço. Esperamos você na barbearia."), clientId);
    }

    /** Chamado pelo agendador: marca como EXPIRADO as reservas temporárias vencidas. */
    @Transactional
    public int expireHolds() {
        LocalDateTime now = now();
        return jdbc.update("UPDATE agendamentos SET status='EXPIRADO', expira_em=NULL, reserva_token_hash=NULL, atualizado_em=? "
                + "WHERE ((status='RESERVA_TEMPORARIA') OR (status='SOLICITADO' AND confirmado_via_link_em IS NULL)) AND expira_em IS NOT NULL AND expira_em <= ?", now, now);
    }

    // ------------------------------------------------------------------ cliente
    public List<CustomerBooking> customerBookings(long clientId) {
        LocalDateTime now = now();
        return jdbc.query("SELECT a.id,COALESCE((SELECT string_agg(s2.nome, ' + ' ORDER BY x.ordem) FROM agendamento_servicos x JOIN servicos s2 ON s2.id=x.servico_id WHERE x.agendamento_id=a.id), s.nome) servico,"
                        + "p.nome profissional,a.inicio,a.fim,a.status,a.preco_referencia,"
                        + "(SELECT string_agg(pr.nome || ' x' || ap.quantidade, ', ') FROM agendamento_produtos ap JOIN produtos pr ON pr.id=ap.produto_id WHERE ap.agendamento_id=a.id) produtos,a.origem FROM agendamentos a "
                        + "JOIN servicos s ON s.id=a.servico_id JOIN profissionais p ON p.id=a.profissional_id "
                        + "WHERE a.cliente_id=? AND a.status NOT IN ('RESERVA_TEMPORARIA','EXPIRADO') ORDER BY a.inicio DESC LIMIT 100",
                (rs, n) -> {
                    BookingStatus st = BookingStatus.parse(rs.getString("status"));
                    LocalDateTime start = rs.getObject("inicio", LocalDateTime.class);
                    return new CustomerBooking(rs.getLong("id"), rs.getString("servico"), rs.getString("profissional"), start,
                            rs.getObject("fim", LocalDateTime.class), st.name(), describe(st),
                            BookingRules.canCustomerCancel(st, start, now, cancelMinHours), rs.getBigDecimal("preco_referencia"), rs.getString("produtos"), rs.getString("origem"));
                }, clientId);
    }

    @Transactional
    public void cancelByCustomer(long clientId, long bookingId) {
        LocalDateTime now = now();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status,inicio FROM agendamentos WHERE id=? AND cliente_id=? FOR UPDATE", bookingId, clientId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado.");
        }
        BookingStatus status = BookingStatus.parse((String) rows.get(0).get("status"));
        LocalDateTime start = (LocalDateTime) toLocal(rows.get(0).get("inicio"));
        if (!BookingRules.canCustomerCancel(status, start, now, cancelMinHours)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível cancelar online. Faltam menos de " + cancelMinHours + " horas ou o status não permite; fale com a barbearia.");
        }
        jdbc.update("UPDATE agendamentos SET status='CANCELADO',cancelado_por='CLIENTE',atualizado_em=? WHERE id=?", now, bookingId);
    }

    // ------------------------------------------------------------------ administração
    public List<AdminBooking> adminList(LocalDate date, LocalDate until, Long professionalId, Long serviceId, String status) {
        StringBuilder sql = new StringBuilder("SELECT a.id,COALESCE((SELECT string_agg(s2.nome, ' + ' ORDER BY x.ordem) FROM agendamento_servicos x JOIN servicos s2 ON s2.id=x.servico_id WHERE x.agendamento_id=a.id), s.nome) servico,"
                + "p.nome profissional,a.inicio,a.fim,a.status,c.nome cliente_nome,c.email,c.telefone,"
                + "NULLIF(concat_ws(' | ', a.observacao, CASE WHEN a.alergias IS NOT NULL THEN 'ALERGIA: ' || a.alergias END, "
                + "(SELECT 'RETIRAR: ' || string_agg(pr.nome || ' x' || ap.quantidade, ', ') FROM agendamento_produtos ap JOIN produtos pr ON pr.id=ap.produto_id WHERE ap.agendamento_id=a.id)), '') observacao,"
                + "a.criado_em,a.cancelado_por,a.origem FROM agendamentos a JOIN servicos s ON s.id=a.servico_id JOIN profissionais p ON p.id=a.profissional_id "
                + "LEFT JOIN clientes c ON c.id=a.cliente_id WHERE a.status NOT IN ('RESERVA_TEMPORARIA','EXPIRADO')");
        List<Object> args = new ArrayList<>();
        if (date != null) {
            sql.append(" AND a.inicio >= ? AND a.inicio < ?");
            args.add(date.atStartOfDay());
            args.add((until != null && !until.isBefore(date) ? until : date).plusDays(1).atStartOfDay());
        }
        if (professionalId != null) {
            sql.append(" AND a.profissional_id=?");
            args.add(professionalId);
        }
        if (serviceId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM agendamento_servicos xs WHERE xs.agendamento_id=a.id AND xs.servico_id=?)");
            args.add(serviceId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status=?");
            args.add(BookingStatus.parse(status).name());
        }
        sql.append(date != null ? " ORDER BY a.inicio ASC LIMIT 500" : " ORDER BY a.inicio DESC LIMIT 500");
        return jdbc.query(sql.toString(), (rs, n) -> new AdminBooking(rs.getLong("id"), rs.getString("servico"), rs.getString("profissional"),
                rs.getObject("inicio", LocalDateTime.class), rs.getObject("fim", LocalDateTime.class), rs.getString("status"),
                rs.getString("cliente_nome"), rs.getString("email"), rs.getString("telefone"), rs.getString("observacao"),
                rs.getObject("criado_em", LocalDateTime.class), rs.getString("cancelado_por"), rs.getString("origem")), args.toArray());
    }

    @Transactional
    public BookingResult changeStatus(long id, String targetText, String username) {
        LocalDateTime now = now();
        BookingStatus target = BookingStatus.parse(targetText);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.status,a.inicio,c.email,s.nome servico FROM agendamentos a "
                + "LEFT JOIN clientes c ON c.id=a.cliente_id JOIN servicos s ON s.id=a.servico_id WHERE a.id=? FOR UPDATE OF a", id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado.");
        }
        BookingStatus current = BookingStatus.parse((String) rows.get(0).get("status"));
        LocalDateTime start = (LocalDateTime) toLocal(rows.get(0).get("inicio"));
        if (!BookingRules.canTransition(current, target) || current == BookingStatus.RESERVA_TEMPORARIA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transição de status não permitida.");
        }
        if (target == BookingStatus.CONFIRMADO && !BookingRules.canConfirm(start, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível confirmar um horário que já passou.");
        }
        if (target == BookingStatus.CONCLUIDO && !BookingRules.canComplete(start, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Só é possível concluir atendimentos cujo horário já começou.");
        }
        if (target == BookingStatus.FALTOU && !NoShowPolicy.canMarkNoShow(start, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Falta ou atraso só pode ser registrado depois de " + NoShowPolicy.GRACE_MINUTES + " minutos do horário marcado.");
        }
        jdbc.update("UPDATE agendamentos SET status=?,cancelado_por=?,atualizado_em=? WHERE id=?", target.name(),
                target == BookingStatus.CANCELADO ? "EQUIPE" : null, now, id);
        if (target == BookingStatus.CONCLUIDO) { // baixa de estoque dos produtos reservados para retirada
            jdbc.update("UPDATE produtos p SET estoque=GREATEST(p.estoque - ap.quantidade, 0) FROM agendamento_produtos ap WHERE ap.agendamento_id=? AND ap.produto_id=p.id", id);
        }
        audit.log(username, "STATUS_" + target.name(), "agendamento " + id);
        String email = (String) rows.get(0).get("email");
        if (email != null && (target == BookingStatus.CONFIRMADO || target == BookingStatus.CANCELADO)) {
            notifyClient(email, target, (String) rows.get(0).get("servico"), start);
        }
        return new BookingResult(id, target.name(), "Status atualizado.");
    }

    /** Agendamento feito no balcão por recepção ou proprietário: já nasce CONFIRMADO e o cliente pode não ter e-mail. */
    @Transactional
    public BookingResult createCounterBooking(CounterBookingRequest r, String username) {
        LocalDateTime now = now();
        List<Svc> services = loadServices(r.servicoIds());
        validateSelection(services);
        requireProfessionalExecutes(r.profissionalId(), services);
        LocalDateTime start = r.inicio().withSecond(0).withNano(0);
        if (!BookingRules.withinBookingHorizon(start, now, horizonDays)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha um horário futuro dentro dos próximos " + horizonDays + " dias.");
        }
        jdbc.execute("SELECT pg_advisory_xact_lock(" + r.profissionalId().longValue() + ")");
        releaseExpired(now, r.profissionalId());
        int minutes = services.stream().mapToInt(Svc::minutos).sum();
        BigDecimal total = services.stream().map(Svc::preco).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!availability.freeStarts(r.profissionalId(), minutes, start.toLocalDate(), now, 0).contains(start.toLocalTime())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esse horário não está mais disponível. Escolha outro.");
        }
        String name = r.nome().trim();
        String phone = r.telefone() == null || r.telefone().isBlank() ? null : r.telefone().trim();
        String email = r.email() == null || r.email().isBlank() ? null : r.email().trim().toLowerCase(Locale.ROOT);
        String note = r.observacao() == null || r.observacao().isBlank() ? null : r.observacao().trim();
        String allergies = r.alergias() == null || r.alergias().isBlank() ? null : r.alergias().trim();
        Long clientId = email == null
                ? jdbc.queryForObject("INSERT INTO clientes(nome,email,telefone,criado_em) VALUES(?,NULL,?,?) RETURNING id", Long.class, name, phone, now)
                : jdbc.queryForObject("INSERT INTO clientes(nome,email,telefone,criado_em) VALUES(?,?,?,?) "
                        + "ON CONFLICT ((lower(email))) DO UPDATE SET telefone=COALESCE(EXCLUDED.telefone, clientes.telefone) RETURNING id", Long.class, name, email, phone, now);
        try {
            Long id = jdbc.queryForObject("INSERT INTO agendamentos(servico_id,profissional_id,cliente_id,inicio,fim,duracao_minutos,preco_referencia,status,observacao,alergias,origem,criado_por,criado_em,atualizado_em) "
                            + "VALUES(?,?,?,?,?,?,?,'CONFIRMADO',?,?,'BALCAO',?,?,?) RETURNING id", Long.class,
                    services.get(0).id(), r.profissionalId(), clientId, start, start.plusMinutes(minutes), minutes, total, note, allergies, username, now, now);
            int order = 1;
            for (Svc x : services) {
                jdbc.update("INSERT INTO agendamento_servicos(agendamento_id,servico_id,ordem,preco,duracao_minutos) VALUES(?,?,?,?,?)", id, x.id(), order++, x.preco(), x.minutos());
            }
            saveProducts(id, r.produtos());
            audit.log(username, "BALCAO_AGENDAR", "agendamento " + id);
            return new BookingResult(id, BookingStatus.CONFIRMADO.name(), "Agendamento criado no balcão.");
        } catch (DataAccessException ex) {
            if (isSqlState(ex, "P0001")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cadastro deste cliente está bloqueado por falta ou atraso. O proprietário precisa liberar antes.");
            }
            if (isConflict(ex)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Esse horário acabou de ser ocupado. Escolha outro.");
            }
            throw ex;
        }
    }

    private void notifyClient(String email, BookingStatus target, String service, LocalDateTime start) {
        String when = start.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));
        String subject = target == BookingStatus.CONFIRMADO ? "Seu horário foi confirmado" : "Seu agendamento foi cancelado pela barbearia";
        String body = target == BookingStatus.CONFIRMADO
                ? "Seu horário para " + service + " em " + when + " está CONFIRMADO."
                : "Sentimos muito: o agendamento de " + service + " em " + when + " foi cancelado pela barbearia. Você pode escolher outro horário no site.";
        try {
            mail.send(email, subject, body);
        } catch (RuntimeException ex) {
            LOG.warn("Falha ao enviar e-mail de notificação ({}).", ex.getClass().getSimpleName()); // sem dados pessoais no log
        }
    }

    // ------------------------------------------------------------------ utilidades
    static String describe(BookingStatus s) {
        return switch (s) {
            case RESERVA_TEMPORARIA -> "Reserva temporária (aguardando o envio dos seus dados)";
            case SOLICITADO -> "Aguardando você confirmar pelo link enviado ao seu e-mail";
            case CONFIRMADO -> "Horário confirmado";
            case CONCLUIDO -> "Atendimento concluído";
            case CANCELADO -> "Cancelado";
            case EXPIRADO -> "Reserva expirada";
            case FALTOU -> "Falta ou atraso: cadastro bloqueado até liberação da barbearia";
        };
    }

    private static Object toLocal(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return value;
    }

    static boolean isSqlState(Throwable ex, String state) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && state.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    /** 23P01 = violação de restrição de exclusão; 23505 = unicidade. */
    static boolean isConflict(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null && (sql.getSQLState().equals("23P01") || sql.getSQLState().equals("23505"))) {
                return true;
            }
        }
        return false;
    }
}
