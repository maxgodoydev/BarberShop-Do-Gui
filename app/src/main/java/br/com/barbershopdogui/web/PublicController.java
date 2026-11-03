package br.com.barbershopdogui.web;

import br.com.barbershopdogui.service.AvailabilityService;
import br.com.barbershopdogui.service.BookingService;
import br.com.barbershopdogui.service.CatalogService;
import br.com.barbershopdogui.service.CustomerAccessService;
import br.com.barbershopdogui.service.RateLimiter;
import br.com.barbershopdogui.web.Dtos.BookingResult;
import br.com.barbershopdogui.web.Dtos.EmailRequest;
import br.com.barbershopdogui.web.Dtos.HoldRequest;
import br.com.barbershopdogui.web.Dtos.HoldResponse;
import br.com.barbershopdogui.web.Dtos.ProductView;
import br.com.barbershopdogui.web.Dtos.ProfessionalView;
import br.com.barbershopdogui.web.Dtos.ReleaseRequest;
import br.com.barbershopdogui.web.Dtos.TokenRequest;
import br.com.barbershopdogui.web.Dtos.ServiceView;
import br.com.barbershopdogui.web.Dtos.SlotGroup;
import br.com.barbershopdogui.web.Dtos.SubmitRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class PublicController {
    private final CatalogService catalog;
    private final AvailabilityService availability;
    private final BookingService bookings;
    private final CustomerAccessService access;
    private final RateLimiter limiter;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Map<String, String> shop = new LinkedHashMap<>();

    public PublicController(CatalogService catalog, AvailabilityService availability, BookingService bookings, CustomerAccessService access,
                            RateLimiter limiter, JdbcTemplate jdbc, Clock clock,
                            @Value("${app.shop.name:BarberShop do Gui}") String name, @Value("${app.shop.address:}") String address,
                            @Value("${app.shop.phone:}") String phone, @Value("${app.shop.whatsapp-url:}") String whatsapp,
                            @Value("${app.shop.maps-url:}") String maps, @Value("${app.shop.hours-text:}") String hours,
                            @Value("${app.shop.instagram-url:}") String instagram) {
        this.catalog = catalog;
        this.availability = availability;
        this.bookings = bookings;
        this.access = access;
        this.limiter = limiter;
        this.jdbc = jdbc;
        this.clock = clock;
        shop.put("nome", name);
        shop.put("endereco", address);
        shop.put("telefone", phone);
        shop.put("whatsappUrl", whatsapp);
        shop.put("mapsUrl", maps);
        shop.put("horarios", hours);
        shop.put("instagramUrl", instagram);
    }

    /** Token CSRF para o front-end estático (cookie XSRF-TOKEN + cabeçalho X-XSRF-TOKEN). */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/public/config")
    public Map<String, String> config() {
        Map<String, String> visible = new LinkedHashMap<>();
        shop.forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                visible.put(k, v);
            }
        });
        return visible;
    }

    @GetMapping("/public/services")
    public List<ServiceView> services() {
        return catalog.activeServices();
    }

    @GetMapping("/public/professionals")
    public List<ProfessionalView> professionals() {
        return catalog.professionals(true);
    }

    @GetMapping("/public/availability")
    public List<SlotGroup> availability(@RequestParam List<Long> servicoIds, @RequestParam(required = false) Long profissionalId, @RequestParam LocalDate data) {
        if (servicoIds.isEmpty() || servicoIds.size() > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha de 1 a 3 serviços.");
        }
        return availability.slots(servicoIds, profissionalId, data, LocalDateTime.now(clock));
    }

    @GetMapping("/public/products")
    public List<ProductView> products() {
        return jdbc.query("SELECT id,nome,marca,preco,estoque,ilustrativo FROM produtos WHERE ativo=TRUE ORDER BY nome",
                (rs, n) -> new ProductView(rs.getLong("id"), rs.getString("nome"), rs.getString("marca"), rs.getBigDecimal("preco"),
                        rs.getInt("estoque") > 0, rs.getBoolean("ilustrativo")));
    }

    /** Regras de combinação para o pop-up desabilitar opções (o servidor valida de novo). */
    @GetMapping("/public/booking-rules")
    public Map<String, Object> bookingRules() {
        Map<Long, List<Long>> excludes = new LinkedHashMap<>();
        jdbc.query("SELECT servico_id,exclui_id FROM servico_exclusoes ORDER BY servico_id,exclui_id",
                rs -> { excludes.computeIfAbsent(rs.getLong(1), k -> new java.util.ArrayList<>()).add(rs.getLong(2)); });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("maxServicos", br.com.barbershopdogui.model.ServiceSelectionRules.MAX_SERVICES);
        out.put("maxMinutos", br.com.barbershopdogui.model.ServiceSelectionRules.MAX_MINUTES);
        out.put("exclusoes", excludes);
        return out;
    }

    /** Cliente clicou no link mágico do e-mail. */
    @PostMapping("/public/confirm")
    public BookingResult confirm(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
        limiter.check("confirm:" + http.getRemoteAddr(), 20, 600);
        BookingService.Confirmation c = bookings.confirmByLink(request.token());
        CustomerController.startSession(http, c.clientId()); // o mesmo link já leva o cliente ao espaço dele, sem senha
        return c.result();
    }

    @PostMapping("/public/holds")
    public HoldResponse hold(@Valid @RequestBody HoldRequest request, HttpServletRequest http) {
        limiter.check("hold:" + http.getRemoteAddr(), 30, 600);
        return bookings.createHold(request);
    }

    @PostMapping("/public/holds/release")
    public Map<String, String> release(@Valid @RequestBody ReleaseRequest request, HttpServletRequest http) {
        limiter.check("release:" + http.getRemoteAddr(), 60, 600);
        bookings.releaseHold(request.reservaId(), request.token());
        return Map.of("mensagem", "Horário liberado.");
    }

    @PostMapping("/public/bookings")
    public BookingResult submit(@Valid @RequestBody SubmitRequest request, HttpServletRequest http) {
        limiter.check("submit:" + http.getRemoteAddr(), 15, 600);
        return bookings.submit(request);
    }

    @PostMapping("/public/access-link")
    public Map<String, String> accessLink(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        limiter.check("link-ip:" + http.getRemoteAddr(), 10, 3600);
        limiter.check("link-mail:" + request.email().trim().toLowerCase(), 3, 3600);
        access.requestLink(request.email());
        return Map.of("mensagem", "Se existir um agendamento para esse e-mail, enviamos um link de acesso válido por pouco tempo.");
    }

    @GetMapping("/public/health")
    public Map<String, Object> health() {
        try {
            Integer v = jdbc.queryForObject("SELECT 1", Integer.class);
            if (v == null || v != 1) {
                throw new IllegalStateException();
            }
            return Map.of("status", "ok", "database", "connected");
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "A aplicação está no ar, mas o banco não respondeu.");
        }
    }
}
