package br.com.barbershopdogui.web;

import br.com.barbershopdogui.service.BookingService;
import br.com.barbershopdogui.service.CustomerAccessService;
import br.com.barbershopdogui.service.RateLimiter;
import br.com.barbershopdogui.web.Dtos.CustomerBooking;
import br.com.barbershopdogui.web.Dtos.TokenRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Área do cliente: sessão criada somente após consumir um link de uso único. */
@RestController
@RequestMapping("/api/cliente")
public class CustomerController {
    private static final String SESSION_KEY = "clienteId";

    private final CustomerAccessService access;
    private final BookingService bookings;
    private final RateLimiter limiter;

    public CustomerController(CustomerAccessService access, BookingService bookings, RateLimiter limiter) {
        this.access = access;
        this.bookings = bookings;
        this.limiter = limiter;
    }

    @PostMapping("/sessao")
    public Map<String, String> openSession(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
        limiter.check("redeem:" + http.getRemoteAddr(), 20, 600);
        Long clientId = access.redeem(request.token());
        if (clientId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Link inválido, expirado ou já utilizado. Peça um novo link.");
        }
        startSession(http, clientId);
        return Map.of("mensagem", "Acesso liberado.");
    }

    /** Abre a sessão do cliente (usada pelo link mágico de acesso e pelo de confirmação). */
    public static void startSession(HttpServletRequest http, long clientId) {
        HttpSession old = http.getSession(false);
        // Só invalida se a sessão anterior era de cliente (evita fixação de sessão e não derruba login admin caso esteja no mesmo navegador)
        if (old != null && old.getAttribute(SESSION_KEY) != null) {
            old.invalidate();
        }
        HttpSession session = http.getSession(true);
        session.setAttribute(SESSION_KEY, clientId);
        session.setMaxInactiveInterval(30 * 60);
    }

    @GetMapping("/perfil")
    public Map<String, Object> profile(HttpServletRequest http) {
        return access.profile(requireClient(http));
    }

    @GetMapping("/agendamentos")
    public List<CustomerBooking> list(HttpServletRequest http) {
        return bookings.customerBookings(requireClient(http));
    }

    @PostMapping("/agendamentos/{id}/cancelar")
    public Map<String, String> cancel(@PathVariable long id, HttpServletRequest http) {
        bookings.cancelByCustomer(requireClient(http), id);
        return Map.of("mensagem", "Agendamento cancelado.");
    }

    @PostMapping("/conta/excluir")
    public Map<String, String> deleteAccount(HttpServletRequest http) {
        String result = access.deleteAccount(requireClient(http), false);
        if (!"OK".equals(result)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CustomerAccessService.deleteMessage(result));
        }
        HttpSession s = http.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        return Map.of("mensagem", "Cadastro excluído. Seus dados pessoais foram removidos.");
    }

    @PostMapping("/sair")
    public Map<String, String> logout(HttpServletRequest http) {
        HttpSession s = http.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        return Map.of("mensagem", "Sessão encerrada.");
    }

    private long requireClient(HttpServletRequest http) {
        HttpSession s = http.getSession(false);
        Object id = s == null ? null : s.getAttribute(SESSION_KEY);
        if (!(id instanceof Long clientId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Peça um novo link de acesso.");
        }
        return clientId;
    }
}
