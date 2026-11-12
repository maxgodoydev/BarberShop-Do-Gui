package br.com.barbershopdogui.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.barbershopdogui.service.AdminUserService;
import br.com.barbershopdogui.service.RateLimiter;
import br.com.barbershopdogui.web.Dtos.PasswordChange;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AuthControllerTest {
    private final AtomicInteger calls = new AtomicInteger();
    /** Falha simulada pelo serviço; {@code null} = troca bem-sucedida. Padrão: senha atual incorreta (403). */
    private final AtomicReference<ResponseStatusException> failure =
            new AtomicReference<>(new ResponseStatusException(HttpStatus.FORBIDDEN, "Senha atual incorreta."));
    private final AdminUserService users = new AdminUserService(null, null, null, null) {
        @Override
        public void changePassword(String username, String current, String next) {
            calls.incrementAndGet();
            ResponseStatusException ex = failure.get();
            if (ex != null) {
                throw ex;
            }
        }
    };
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneId.of("UTC"));
    private final AuthController controller = new AuthController(users, new RateLimiter(clock));
    private final PasswordChange body = new PasswordChange("senha-atual-errada", "NovaSenhaForte#1");

    @Test
    void apos5TentativasATrocaDeSenhaRetorna429SemChamarOServico() {
        Principal joao = () -> "joao";
        for (int i = 0; i < AuthController.PASSWORD_ATTEMPTS; i++) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.changePassword(body, joao));
            assertEquals(403, ex.getStatusCode().value());
        }
        ResponseStatusException blocked = assertThrows(ResponseStatusException.class, () -> controller.changePassword(body, joao));
        assertEquals(429, blocked.getStatusCode().value());
        assertEquals(AuthController.PASSWORD_ATTEMPTS, calls.get(), "a 6ª tentativa não chega à verificação da senha");
    }

    @Test
    void limiteEPorUsuario() {
        Principal joao = () -> "joao";
        Principal maria = () -> "maria";
        for (int i = 0; i < AuthController.PASSWORD_ATTEMPTS; i++) {
            assertThrows(ResponseStatusException.class, () -> controller.changePassword(body, joao));
        }
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.changePassword(body, maria));
        assertEquals(403, ex.getStatusCode().value(), "outro usuário não é afetado pelo limite do João");
    }

    @Test
    void errosDePoliticaDaNovaSenhaNaoConsomemOLimite() {
        Principal ana = () -> "ana";
        failure.set(new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nova senha deve ter ao menos 12 caracteres."));
        for (int i = 0; i < AuthController.PASSWORD_ATTEMPTS * 2; i++) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.changePassword(body, ana));
            assertEquals(400, ex.getStatusCode().value());
        }
        failure.set(null);
        assertEquals("Senha alterada.", controller.changePassword(body, ana).get("mensagem"));
    }

    @Test
    void trocaBemSucedidaZeraAsFalhasAcumuladas() {
        Principal ana = () -> "ana";
        for (int i = 0; i < AuthController.PASSWORD_ATTEMPTS - 1; i++) {
            assertThrows(ResponseStatusException.class, () -> controller.changePassword(body, ana));
        }
        ResponseStatusException forbidden = new ResponseStatusException(HttpStatus.FORBIDDEN, "Senha atual incorreta.");
        failure.set(null);
        controller.changePassword(body, ana);
        failure.set(forbidden);
        for (int i = 0; i < AuthController.PASSWORD_ATTEMPTS; i++) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.changePassword(body, ana));
            assertEquals(403, ex.getStatusCode().value(), "o acerto zerou o contador: ainda há 5 falhas disponíveis");
        }
        ResponseStatusException blocked = assertThrows(ResponseStatusException.class, () -> controller.changePassword(body, ana));
        assertEquals(429, blocked.getStatusCode().value());
    }
}
