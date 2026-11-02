package br.com.barbershopdogui.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Etapa4RulesTest {
    private static ServiceSelectionRules.Item s(long id, int min, Long... ex) { return new ServiceSelectionRules.Item(id, min, Set.of(ex)); }

    @Test void selecaoValida() { assertNull(ServiceSelectionRules.validate(List.of(s(1, 30), s(2, 20)))); }
    @Test void selecaoVaziaOuNula() { assertNotNull(ServiceSelectionRules.validate(List.of())); assertNotNull(ServiceSelectionRules.validate(null)); }
    @Test void maximoDeTresServicos() { assertNotNull(ServiceSelectionRules.validate(List.of(s(1, 10), s(2, 10), s(3, 10), s(4, 10)))); }
    @Test void maximoDe200Minutos() { assertNull(ServiceSelectionRules.validate(List.of(s(1, 100), s(2, 100)))); assertNotNull(ServiceSelectionRules.validate(List.of(s(1, 100), s(2, 101)))); }
    @Test void exclusaoFuncionaNosDoisSentidos() {
        assertNotNull(ServiceSelectionRules.validate(List.of(s(1, 30, 2L), s(2, 20))));
        assertNotNull(ServiceSelectionRules.validate(List.of(s(1, 30), s(2, 20, 1L))));
    }
    @Test void corteInfantilNaoCombinaComBarbaEmNenhumaOrdem() {
        assertNotNull(ServiceSelectionRules.validate(List.of(s(3, 30, 5L), s(5, 20, 3L))));
        assertNotNull(ServiceSelectionRules.validate(List.of(s(5, 20, 3L), s(3, 30, 5L))));
    }

    @Test void servicoRepetidoERecusado() { assertNotNull(ServiceSelectionRules.validate(List.of(s(1, 30), s(1, 30)))); }

    @Test void toleranciaDe30MinutosExata() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 10, 0);
        assertFalse(NoShowPolicy.mustBlock(BookingStatus.CONFIRMADO, start, null, start.plusMinutes(30)));
        assertTrue(NoShowPolicy.mustBlock(BookingStatus.CONFIRMADO, start, null, start.plusMinutes(31)));
        assertTrue(NoShowPolicy.mustBlock(BookingStatus.CONFIRMADO, start, start.plusMinutes(31), start.plusHours(2)));
        assertFalse(NoShowPolicy.mustBlock(BookingStatus.CONFIRMADO, start, start.plusMinutes(10), start.plusHours(2)));
        assertFalse(NoShowPolicy.mustBlock(BookingStatus.CANCELADO, start, null, start.plusHours(5)));
    }

    @Test void semanaAtualOuProximaSeCheia() {
        LocalDate quarta = LocalDate.of(2026, 10, 28); // semana de 26/10
        assertEquals(LocalDate.of(2026, 10, 26), BookingWeek.targetWeekStart(quarta, false));
        assertEquals(LocalDate.of(2026, 11, 2), BookingWeek.targetWeekStart(quarta, true));
        assertTrue(BookingWeek.isBookable(LocalDate.of(2026, 10, 30), quarta, false));
        assertFalse(BookingWeek.isBookable(LocalDate.of(2026, 11, 3), quarta, false));
        assertFalse(BookingWeek.isBookable(LocalDate.of(2026, 10, 27), quarta, false)); // passado
        assertTrue(BookingWeek.isBookable(LocalDate.of(2026, 11, 3), quarta, true));
    }

    @Test void novoFluxoDeStatus() {
        assertTrue(BookingRules.canTransition(BookingStatus.SOLICITADO, BookingStatus.EXPIRADO));
        assertTrue(BookingRules.canTransition(BookingStatus.CONFIRMADO, BookingStatus.FALTOU));
        assertFalse(BookingRules.canTransition(BookingStatus.FALTOU, BookingStatus.CONFIRMADO));
        assertFalse(BookingStatus.FALTOU.occupiesAgenda());
    }
}
