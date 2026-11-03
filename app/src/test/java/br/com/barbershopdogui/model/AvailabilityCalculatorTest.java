package br.com.barbershopdogui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);          // terça-feira
    private static final LocalDateTime NOW = LocalDate.of(2026, 10, 24).atTime(12, 0);
    private static final List<WorkWindow> SHIFT = List.of(new WorkWindow(LocalTime.of(9, 0), LocalTime.of(12, 0)),
            new WorkWindow(LocalTime.of(13, 0), LocalTime.of(18, 0)));

    private static TimeInterval iv(int h1, int m1, int h2, int m2) {
        return new TimeInterval(DAY.atTime(h1, m1), DAY.atTime(h2, m2));
    }

    private List<LocalTime> starts(int duration, List<TimeInterval> busy) {
        return AvailabilityCalculator.freeStarts(DAY, duration, SHIFT, busy, NOW, 30, 0);
    }

    @Test
    void servicoNaoPodeAtravessarPausaNemFimDoExpediente() {
        List<LocalTime> s = starts(60, List.of());
        assertTrue(s.contains(LocalTime.of(11, 0)));          // 11:00-12:00 cabe
        assertFalse(s.contains(LocalTime.of(11, 30)));        // atravessaria o almoço
        assertTrue(s.contains(LocalTime.of(17, 0)));
        assertFalse(s.contains(LocalTime.of(17, 30)));        // passaria das 18:00
    }

    @Test
    void sobreposicaoDeIntervalosEhBloqueadaNaoApenasHorarioIdentico() {
        // Já existe um serviço de 50 min às 10:00 (10:00-10:50). Um corte de 30 min às 10:30 deve ser recusado.
        List<LocalTime> s = starts(30, List.of(iv(10, 0, 10, 50)));
        assertFalse(s.contains(LocalTime.of(10, 0)));
        assertFalse(s.contains(LocalTime.of(10, 30)));
        assertTrue(s.contains(LocalTime.of(9, 30)));          // 09:30-10:00 encosta mas não sobrepõe
        assertTrue(s.contains(LocalTime.of(11, 0)));
    }

    @Test
    void servicoLongoQueTerminariaDepoisDoInicioDeOutroEhRecusado() {
        // Luzes (120 min) às 9:00 colidiria com um agendamento às 10:30.
        List<LocalTime> s = starts(120, List.of(iv(10, 30, 11, 0)));
        assertFalse(s.contains(LocalTime.of(9, 0)));
    }

    @Test
    void bloqueioRemoveHorarios() {
        List<LocalTime> s = starts(30, List.of(iv(14, 0, 16, 0)));
        assertFalse(s.contains(LocalTime.of(14, 0)));
        assertFalse(s.contains(LocalTime.of(15, 30)));
        assertTrue(s.contains(LocalTime.of(16, 0)));
        assertTrue(s.contains(LocalTime.of(13, 30)));
    }

    @Test
    void antecedenciaMinimaEhRespeitada() {
        LocalDateTime now = DAY.atTime(9, 10);
        List<LocalTime> s = AvailabilityCalculator.freeStarts(DAY, 30, SHIFT, List.of(), now, 30, 60);
        assertEquals(LocalTime.of(10, 30), s.get(0));         // 09:10 + 60 min = 10:10 -> próximo passo é 10:30
    }

    @Test
    void diaSemJanelasNaoTemHorarios() {
        assertTrue(AvailabilityCalculator.freeStarts(DAY, 30, List.of(), List.of(), NOW, 30, 0).isEmpty());
    }

    @Test
    void intervalosQueSoSeTocamNaoSeSobrepoem() {
        assertFalse(iv(10, 0, 10, 30).overlaps(iv(10, 30, 11, 0)));
        assertTrue(iv(10, 0, 10, 31).overlaps(iv(10, 30, 11, 0)));
    }
}
