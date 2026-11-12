package br.com.barbershopdogui.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.barbershopdogui.service.AdminUserService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ForcedPasswordChangeFilterTest {
    private final AtomicInteger lookups = new AtomicInteger();
    private Optional<AdminUserService.AccountState> state = Optional.of(new AdminUserService.AccountState(true, false));
    private RuntimeException lookupFailure;

    /** Serviço de mentira: só responde a situação da conta (o construtor recebe nulos porque nada mais é usado). */
    private final AdminUserService users = new AdminUserService(null, null, null, null) {
        @Override
        public Optional<AccountState> accountState(String username) {
            lookups.incrementAndGet();
            if (lookupFailure != null) {
                throw lookupFailure;
            }
            return state;
        }
    };
    private final ForcedPasswordChangeFilter filter = new ForcedPasswordChangeFilter(users);

    @AfterEach
    void limpa() {
        SecurityContextHolder.clearContext();
    }

    private static void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("joao", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private Result run(String path) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.setServletPath(path);
        MockHttpSession session = new MockHttpSession();
        req.setSession(session);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        return new Result(res, chain.getRequest() != null, session);
    }

    private record Result(MockHttpServletResponse res, boolean passed, MockHttpSession session) { }

    @Test
    void semAutenticacaoOuForaDaAreaAdministrativaNaoConsultaOBanco() throws Exception {
        assertTrue(run("/admin/agenda.html").passed());
        login("RECEPCAO");
        assertTrue(run("/api/public/servicos").passed());
        assertTrue(run("/api/auth/logout").passed(), "sair deve funcionar sempre");
        assertEquals(0, lookups.get());
    }

    @Test
    void contaNormalPassaEmTodaAAreaAdministrativa() throws Exception {
        login("TECNICO");
        assertTrue(run("/api/admin/dashboard").passed());
        assertTrue(run("/admin/bi.html").passed());
        assertTrue(run("/api/auth/me").passed());
    }

    @Test
    void senhaProvisoriaBloqueiaApiAdministrativaComForbidden() throws Exception {
        state = Optional.of(new AdminUserService.AccountState(true, true));
        login("RECEPCAO");
        Result r = run("/api/admin/agendamentos");
        assertFalse(r.passed());
        assertEquals(403, r.res().getStatus());
    }

    @Test
    void senhaProvisoriaRedirecionaPaginasParaOPainel() throws Exception {
        state = Optional.of(new AdminUserService.AccountState(true, true));
        login("RECEPCAO");
        Result r = run("/admin/agenda.html");
        assertFalse(r.passed());
        assertEquals("/admin/painel.html", r.res().getRedirectedUrl());
    }

    @Test
    void senhaProvisoriaLiberaApenasPainelMeESenha() throws Exception {
        state = Optional.of(new AdminUserService.AccountState(true, true));
        login("RECEPCAO");
        assertTrue(run("/admin/painel.html").passed());
        assertTrue(run("/api/auth/me").passed());
        assertTrue(run("/api/auth/senha").passed());
        assertFalse(run("/admin/bi.html").passed());
    }

    @Test
    void contaDesativadaTemASessaoEncerradaNaHora() throws Exception {
        state = Optional.of(new AdminUserService.AccountState(false, false));
        login("TECNICO");
        Result r = run("/api/admin/dashboard");
        assertFalse(r.passed());
        assertEquals(401, r.res().getStatus());
        assertTrue(r.session().isInvalid(), "a sessão existente deve ser invalidada");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void contaDesativadaEmPaginaVaiParaOLogin() throws Exception {
        state = Optional.of(new AdminUserService.AccountState(false, false));
        login("RECEPCAO");
        Result r = run("/admin/painel.html");
        assertFalse(r.passed());
        assertEquals("/admin-login.html", r.res().getRedirectedUrl());
        assertTrue(r.session().isInvalid());
    }

    @Test
    void contaDesativadaNaoAcessaMeNemSenhaMasConsegueSair() throws Exception {
        state = Optional.of(new AdminUserService.AccountState(false, false));
        login("RECEPCAO");
        assertEquals(401, run("/api/auth/me").res().getStatus());
        login("RECEPCAO");
        assertEquals(401, run("/api/auth/senha").res().getStatus());
        login("RECEPCAO");
        assertTrue(run("/api/auth/logout").passed());
    }

    @Test
    void contaRemovidaTratadaComoDesativada() throws Exception {
        state = Optional.empty();
        login("PROPRIETARIO");
        Result r = run("/api/admin/usuarios");
        assertFalse(r.passed());
        assertEquals(401, r.res().getStatus());
    }

    @Test
    void falhaNaConsultaDevolve503ENaoLiberaOAcesso() throws Exception {
        lookupFailure = new IllegalStateException("banco indisponível");
        login("PROPRIETARIO");
        Result r = run("/api/admin/usuarios");
        assertFalse(r.passed());
        assertEquals(503, r.res().getStatus());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication(), "não derruba a sessão por falha temporária");
    }
}
