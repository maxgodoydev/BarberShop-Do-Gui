package br.com.barbershopdogui.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BookingRulesTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 24, 10, 0);

    @Test
    void transicoesPermitidas() {
        assertTrue(BookingRules.canTransition(BookingStatus.RESERVA_TEMPORARIA, BookingStatus.SOLICITADO));
        assertTrue(BookingRules.canTransition(BookingStatus.RESERVA_TEMPORARIA, BookingStatus.EXPIRADO));
        assertTrue(BookingRules.canTransition(BookingStatus.SOLICITADO, BookingStatus.CONFIRMADO));
        assertTrue(BookingRules.canTransition(BookingStatus.SOLICITADO, BookingStatus.CANCELADO));
        assertTrue(BookingRules.canTransition(BookingStatus.CONFIRMADO, BookingStatus.CONCLUIDO));
        assertTrue(BookingRules.canTransition(BookingStatus.CONFIRMADO, BookingStatus.CANCELADO));
    }

    @Test
    void transicoesProibidas() {
        assertFalse(BookingRules.canTransition(BookingStatus.SOLICITADO, BookingStatus.CONCLUIDO));
        assertFalse(BookingRules.canTransition(BookingStatus.CONCLUIDO, BookingStatus.CONFIRMADO));
        assertFalse(BookingRules.canTransition(BookingStatus.CANCELADO, BookingStatus.CONFIRMADO));
        assertFalse(BookingRules.canTransition(BookingStatus.EXPIRADO, BookingStatus.SOLICITADO));
        assertFalse(BookingRules.canTransition(BookingStatus.RESERVA_TEMPORARIA, BookingStatus.CONFIRMADO));
        assertFalse(BookingRules.canTransition(BookingStatus.SOLICITADO, BookingStatus.SOLICITADO));
    }

    @Test
    void sobAgendaApenasStatusAtivos() {
        assertTrue(BookingStatus.RESERVA_TEMPORARIA.occupiesAgenda());
        assertTrue(BookingStatus.CONCLUIDO.occupiesAgenda());
        assertFalse(BookingStatus.CANCELADO.occupiesAgenda());
        assertFalse(BookingStatus.EXPIRADO.occupiesAgenda());
    }

    @Test
    void baixaESomenteAposInicioEConfirmacaoSomenteAntes() {
        assertTrue(BookingRules.canComplete(now.minusHours(1), now));
        assertFalse(BookingRules.canComplete(now.plusHours(1), now));
        assertTrue(BookingRules.canConfirm(now.plusHours(1), now));
        assertFalse(BookingRules.canConfirm(now.minusMinutes(1), now));
    }

    @Test
    void clienteCancelaComAntecedenciaMinima() {
        assertTrue(BookingRules.canCustomerCancel(BookingStatus.CONFIRMADO, now.plusHours(3), now, 2));
        assertFalse(BookingRules.canCustomerCancel(BookingStatus.CONFIRMADO, now.plusHours(1), now, 2));
        assertFalse(BookingRules.canCustomerCancel(BookingStatus.CONCLUIDO, now.plusDays(2), now, 2));
    }

    @Test
    void horizonteDeAgendamento() {
        assertTrue(BookingRules.withinBookingHorizon(now.plusDays(30), now, 60));
        assertFalse(BookingRules.withinBookingHorizon(now.plusDays(61), now, 60));
        assertFalse(BookingRules.withinBookingHorizon(now.minusMinutes(5), now, 60));
    }

    @Test
    void statusInvalidoLancaErroSemVazarDetalhes() {
        assertThrows(IllegalArgumentException.class, () -> BookingStatus.parse("XYZ"));
        assertThrows(IllegalArgumentException.class, () -> BookingStatus.parse(" "));
    }
}
