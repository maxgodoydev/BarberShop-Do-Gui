package br.com.barbershopdogui.service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Limitador simples em memória (janela fixa).
 * <p><b>Nota de operação:</b> O estado é perdido ao reiniciar a aplicação e não é compartilhado
 * entre múltiplas instâncias. Para ambientes com mais de uma instância ou que exijam persistência
 * entre reinicializações, substitua por uma implementação baseada em Redis (ex: Bucket4j + Redis).
 * Ver {@code docs/seguranca.md} para detalhes. */
@Component
public class RateLimiter {
    private record Window(long startedAtMillis, int count) { }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String key, int max, long windowSeconds) {
        long now = clock.millis();
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().startedAtMillis() > 3_600_000);
        }
        Window w = windows.merge(key, new Window(now, 1),
                (old, fresh) -> now - old.startedAtMillis() > windowSeconds * 1000 ? fresh : new Window(old.startedAtMillis(), old.count() + 1));
        if (w.count() > max) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
        }
    }

    /**
     * Bloqueia sem contar: lança 429 se já houve {@code max} falhas registradas na janela.
     * Use com {@link #recordFailure} e {@link #reset} quando só as FALHAS devem consumir o limite
     * (ex.: senha atual errada), para que acertos legítimos não bloqueiem o usuário.
     */
    public void assertBelow(String key, int max, long windowSeconds) {
        Window w = windows.get(key);
        if (w != null && clock.millis() - w.startedAtMillis() <= windowSeconds * 1000 && w.count() >= max) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
        }
    }

    /** Registra uma falha para a chave (inicia nova janela se a anterior expirou). */
    public void recordFailure(String key, long windowSeconds) {
        long now = clock.millis();
        windows.merge(key, new Window(now, 1),
                (old, fresh) -> now - old.startedAtMillis() > windowSeconds * 1000 ? fresh : new Window(old.startedAtMillis(), old.count() + 1));
    }

    /** Zera o contador da chave (chamado após um acerto). */
    public void reset(String key) {
        windows.remove(key);
    }
}
