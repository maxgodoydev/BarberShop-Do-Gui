package br.com.barbershopdogui.model;

import java.time.Duration;
import java.time.LocalDateTime;

/** Regras de negócio sem dependência de framework. */
public final class BookingRules {
    private BookingRules() { }

    public static boolean canTransition(BookingStatus current, BookingStatus target) {
        if (current == null || target == null || current == target) {
            return false;
        }
        return switch (current) {
            case RESERVA_TEMPORARIA -> target == BookingStatus.SOLICITADO || target == BookingStatus.EXPIRADO;
            case SOLICITADO -> target == BookingStatus.CONFIRMADO || target == BookingStatus.CANCELADO || target == BookingStatus.EXPIRADO;
            case CONFIRMADO -> target == BookingStatus.CONCLUIDO || target == BookingStatus.CANCELADO || target == BookingStatus.FALTOU;
            case CONCLUIDO, CANCELADO, EXPIRADO, FALTOU -> false;
        };
    }

    /** Só é possível dar baixa (concluir) em atendimentos cujo horário já começou. */
    public static boolean canComplete(LocalDateTime start, LocalDateTime now) {
        return start != null && now != null && !start.isAfter(now);
    }

    /** Só é possível confirmar atendimentos que ainda não começaram. */
    public static boolean canConfirm(LocalDateTime start, LocalDateTime now) {
        return start != null && now != null && start.isAfter(now);
    }

    /** Cliente cancela apenas pedidos ativos com antecedência mínima (padrão configurável). */
    public static boolean canCustomerCancel(BookingStatus status, LocalDateTime start, LocalDateTime now, int minHours) {
        if (status != BookingStatus.SOLICITADO && status != BookingStatus.CONFIRMADO) {
            return false;
        }
        return Duration.between(now, start).toMinutes() >= minHours * 60L;
    }

    public static boolean withinBookingHorizon(LocalDateTime start, LocalDateTime now, int horizonDays) {
        return start != null && start.isAfter(now) && !start.isAfter(now.plusDays(horizonDays));
    }
}
