package br.com.barbershopdogui.web;

import br.com.barbershopdogui.service.AdminUserService;
import br.com.barbershopdogui.web.Dtos.PasswordChange;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login/logout são tratados pelo Spring Security (/api/auth/login e /api/auth/logout). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AdminUserService users;

    public AuthController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public Map<String, String> me(Authentication auth) {
        String role = auth.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).findFirst().orElse("");
        return Map.of("usuario", auth.getName(), "papel", role);
    }

    @PostMapping("/senha")
    public Map<String, String> changePassword(@Valid @RequestBody PasswordChange in, Principal principal) {
        users.changePassword(principal.getName(), in.senhaAtual(), in.novaSenha());
        return Map.of("mensagem", "Senha alterada.");
    }
}
