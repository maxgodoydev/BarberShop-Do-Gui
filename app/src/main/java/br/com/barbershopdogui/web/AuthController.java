package br.com.barbershopdogui.web;

import br.com.barbershopdogui.service.AdminUserService;
import br.com.barbershopdogui.service.RateLimiter;
import br.com.barbershopdogui.web.Dtos.PasswordChange;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Login/logout são tratados pelo Spring Security (/api/auth/login e /api/auth/logout). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    /** Falhas de senha atual por usuário numa janela de 15 minutos (freia adivinhação da senha atual com sessão roubada). */
    static final int PASSWORD_ATTEMPTS = 5;
    static final long PASSWORD_WINDOW_SECONDS = 900;

    private final AdminUserService users;
    private final RateLimiter limiter;

    public AuthController(AdminUserService users, RateLimiter limiter) {
        this.users = users;
        this.limiter = limiter;
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        String role = auth.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).findFirst().orElse("");
        boolean exigeTroca = users.requiresPasswordChange(auth.getName());
        return Map.of("usuario", auth.getName(), "papel", role, "exigeTrocaSenha", exigeTroca);
    }

    @PostMapping("/senha")
    public Map<String, String> changePassword(@Valid @RequestBody PasswordChange in, Principal principal) {
        String key = "senha:" + principal.getName();
        // Só a senha atual errada (403) consome o limite: trocas bem-sucedidas e erros de política
        // da nova senha (400) não bloqueiam um usuário legítimo.
        limiter.assertBelow(key, PASSWORD_ATTEMPTS, PASSWORD_WINDOW_SECONDS);
        try {
            users.changePassword(principal.getName(), in.senhaAtual(), in.novaSenha());
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                limiter.recordFailure(key, PASSWORD_WINDOW_SECONDS);
            }
            throw e;
        }
        limiter.reset(key);
        return Map.of("mensagem", "Senha alterada.");
    }
}
