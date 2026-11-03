package br.com.barbershopdogui.model;

import java.time.LocalDateTime;

/** Falta ou atraso: após a tolerância, o cadastro do cliente é bloqueado até a equipe liberar. */
public final class NoShowPolicy {
    public static final int GRACE_MINUTES = 30;

    private NoShowPolicy() { }

    /** A equipe só pode registrar falta/atraso depois da tolerância de 30 min contados do horário marcado. */
    public static boolean canMarkNoShow(LocalDateTime start, LocalDateTime now) {
        return start != null && now != null && now.isAfter(start.plusMinutes(GRACE_MINUTES));
    }

    /** @param arrival chegada registrada pela equipe (null = ainda não chegou). */
    public static boolean mustBlock(BookingStatus status, LocalDateTime start, LocalDateTime arrival, LocalDateTime now) {
        if (start == null || now == null || (status != BookingStatus.CONFIRMADO && status != BookingStatus.FALTOU)) {
            return false;
        }
        LocalDateTime limit = start.plusMinutes(GRACE_MINUTES);
        return arrival == null ? now.isAfter(limit) : arrival.isAfter(limit);
    }
}
