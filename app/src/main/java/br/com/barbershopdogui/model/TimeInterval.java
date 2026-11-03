package br.com.barbershopdogui.model;

import java.time.LocalDateTime;

/** Intervalo semiaberto [start, end). Dois intervalos que apenas se tocam NÃO se sobrepõem. */
public record TimeInterval(LocalDateTime start, LocalDateTime end) {
    public TimeInterval {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Intervalo inválido: o fim deve ser posterior ao início.");
        }
    }

    public boolean overlaps(TimeInterval other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
