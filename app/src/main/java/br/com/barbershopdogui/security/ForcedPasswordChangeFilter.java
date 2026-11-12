package br.com.barbershopdogui.security;

import br.com.barbershopdogui.service.AdminUserService;
import br.com.barbershopdogui.service.AdminUserService.AccountState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Revalida a conta a cada requisição administrativa:
 * <ol>
 *   <li>conta desativada (ou removida): encerra a sessão na hora, mesmo que ela ainda não tenha expirado;</li>
 *   <li>senha provisória pendente: só libera o painel, {@code /api/auth/me} e {@code /api/auth/senha} até a troca.</li>
 * </ol>
 * Uma sessão antiga de quem teve a senha redefinida cai no caso 2 (precisa da senha nova para trocar).
 */
public class ForcedPasswordChangeFilter extends OncePerRequestFilter {

    private static final Set<String> ADMIN_ROLES = Set.of("ROLE_PROPRIETARIO", "ROLE_RECEPCAO", "ROLE_TECNICO");
    private static final Set<String> ALLOWED_WHILE_PENDING = Set.of("/admin/painel.html", "/api/auth/me", "/api/auth/senha");
    private static final String LOGIN_PAGE = "/admin-login.html";

    private final AdminUserService users;

    public ForcedPasswordChangeFilter(AdminUserService users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(ADMIN_ROLES::contains);
        if (!admin) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();
        boolean adminArea = path.startsWith("/api/admin/") || path.startsWith("/admin/");
        boolean authArea = path.equals("/api/auth/me") || path.equals("/api/auth/senha");
        if (!adminArea && !authArea) {
            chain.doFilter(request, response);
            return;
        }

        Optional<AccountState> state;
        try {
            state = users.accountState(auth.getName());
        } catch (RuntimeException ex) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Não foi possível validar a situação da conta.");
            return;
        }

        if (state.isEmpty() || !state.get().active()) {
            endSession(request);
            if (path.startsWith("/api/")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sessão encerrada.");
            } else {
                response.sendRedirect(LOGIN_PAGE);
            }
            return;
        }

        if (!state.get().mustChangePassword() || ALLOWED_WHILE_PENDING.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "É necessário alterar a senha provisória antes de continuar.");
        } else {
            response.sendRedirect("/admin/painel.html");
        }
    }

    private static void endSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
