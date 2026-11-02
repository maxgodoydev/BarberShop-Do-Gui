package br.com.barbershopdogui.web;

import br.com.barbershopdogui.service.AdminUserService;
import br.com.barbershopdogui.service.BookingService;
import br.com.barbershopdogui.service.CatalogService;
import br.com.barbershopdogui.service.DashboardService;
import br.com.barbershopdogui.service.ScheduleService;
import br.com.barbershopdogui.web.Dtos.ActiveInput;
import br.com.barbershopdogui.web.Dtos.AdminBooking;
import br.com.barbershopdogui.web.Dtos.AdminUserInput;
import br.com.barbershopdogui.web.Dtos.AdminUserView;
import br.com.barbershopdogui.web.Dtos.BlockInput;
import br.com.barbershopdogui.web.Dtos.BlockResult;
import br.com.barbershopdogui.web.Dtos.BlockView;
import br.com.barbershopdogui.web.Dtos.BookingResult;
import br.com.barbershopdogui.web.Dtos.ProfessionalInput;
import br.com.barbershopdogui.web.Dtos.ProfessionalView;
import br.com.barbershopdogui.web.Dtos.ServiceInput;
import br.com.barbershopdogui.web.Dtos.ServiceView;
import br.com.barbershopdogui.web.Dtos.StatusInput;
import br.com.barbershopdogui.web.Dtos.WindowInput;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API administrativa. Autorização por papel definida em SecurityConfig (técnico: só dashboard/exportação/leitura do catálogo). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final CatalogService catalog;
    private final ScheduleService schedule;
    private final BookingService bookings;
    private final DashboardService dashboard;
    private final AdminUserService users;

    public AdminController(CatalogService catalog, ScheduleService schedule, BookingService bookings, DashboardService dashboard, AdminUserService users) {
        this.catalog = catalog;
        this.schedule = schedule;
        this.bookings = bookings;
        this.dashboard = dashboard;
        this.users = users;
    }

    // ---- indicadores e exportação (proprietário e técnico)
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return dashboard.dashboard();
    }

    @GetMapping("/export/agendamentos.csv")
    public ResponseEntity<byte[]> exportCsv() {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=agendamentos-sem-dados-pessoais.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(dashboard.exportCsv());
    }

    // ---- serviços
    @GetMapping("/servicos")
    public List<ServiceView> services() {
        return catalog.allServices();
    }

    @PostMapping("/servicos")
    public ServiceView createService(@Valid @RequestBody ServiceInput in) {
        return catalog.createService(in);
    }

    @PutMapping("/servicos/{id}")
    public ServiceView updateService(@PathVariable long id, @Valid @RequestBody ServiceInput in) {
        return catalog.updateService(id, in);
    }

    @PatchMapping("/servicos/{id}/ativo")
    public Map<String, String> serviceActive(@PathVariable long id, @RequestBody ActiveInput in) {
        catalog.setServiceActive(id, in.ativo());
        return Map.of("mensagem", "Serviço atualizado.");
    }

    // ---- profissionais, horários e bloqueios
    @GetMapping("/profissionais")
    public List<ProfessionalView> professionals() {
        return catalog.professionals(false);
    }

    @PostMapping("/profissionais")
    public ProfessionalView createProfessional(@Valid @RequestBody ProfessionalInput in) {
        return catalog.createProfessional(in);
    }

    @PutMapping("/profissionais/{id}")
    public ProfessionalView updateProfessional(@PathVariable long id, @Valid @RequestBody ProfessionalInput in) {
        return catalog.updateProfessional(id, in);
    }

    @PatchMapping("/profissionais/{id}/ativo")
    public Map<String, String> professionalActive(@PathVariable long id, @RequestBody ActiveInput in) {
        catalog.setProfessionalActive(id, in.ativo());
        return Map.of("mensagem", "Profissional atualizado.");
    }

    @GetMapping("/profissionais/{id}/horarios")
    public List<WindowInput> windows(@PathVariable long id) {
        return schedule.windows(id);
    }

    @PutMapping("/profissionais/{id}/horarios")
    public List<WindowInput> replaceWindows(@PathVariable long id, @RequestBody List<@Valid WindowInput> windows) {
        return schedule.replaceWindows(id, windows);
    }

    @GetMapping("/bloqueios")
    public List<BlockView> blocks(@RequestParam(defaultValue = "true") boolean somenteFuturos) {
        return schedule.blocks(somenteFuturos);
    }

    @PostMapping("/bloqueios")
    public BlockResult createBlock(@Valid @RequestBody BlockInput in) {
        return schedule.createBlock(in);
    }

    @DeleteMapping("/bloqueios/{id}")
    public Map<String, String> deleteBlock(@PathVariable long id) {
        schedule.deleteBlock(id);
        return Map.of("mensagem", "Bloqueio removido.");
    }

    // ---- agendamentos (contêm dados pessoais: proprietário e recepção)
    @GetMapping("/agendamentos")
    public List<AdminBooking> bookings(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                       @RequestParam(required = false) Long profissionalId, @RequestParam(required = false) Long servicoId,
                                       @RequestParam(required = false) String status) {
        return bookings.adminList(data, ate, profissionalId, servicoId, status);
    }

    @PatchMapping("/agendamentos/{id}/status")
    public BookingResult changeStatus(@PathVariable long id, @Valid @RequestBody StatusInput in, Principal principal) {
        return bookings.changeStatus(id, in.status(), principal.getName());
    }

    // ---- usuários administrativos (somente proprietário)
    @GetMapping("/usuarios")
    public List<AdminUserView> users() {
        return users.list();
    }

    @PostMapping("/usuarios")
    public Map<String, String> createUser(@Valid @RequestBody AdminUserInput in) {
        users.createStaff(in);
        return Map.of("mensagem", "Conta criada. Informe a senha temporária por um canal seguro e peça a troca no primeiro acesso.");
    }

    @PatchMapping("/usuarios/{id}/ativo")
    public Map<String, String> userActive(@PathVariable long id, @RequestBody ActiveInput in, Principal principal) {
        users.setActive(id, in.ativo(), principal.getName());
        return Map.of("mensagem", "Usuário atualizado.");
    }
}
