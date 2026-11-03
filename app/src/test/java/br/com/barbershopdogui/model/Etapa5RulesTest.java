package br.com.barbershopdogui.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Regra de falta/atraso: só depois de 30 minutos do horário marcado. */
class Etapa5RulesTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 14, 0);

    @Test
    void naoPodeMarcarFaltaAntesDaTolerancia() {
        assertFalse(NoShowPolicy.canMarkNoShow(START, START.minusMinutes(5)));
        assertFalse(NoShowPolicy.canMarkNoShow(START, START.plusMinutes(30)));
    }

    @Test
    void podeMarcarFaltaDepoisDaTolerancia() {
        assertTrue(NoShowPolicy.canMarkNoShow(START, START.plusMinutes(31)));
    }

    @Test
    void entradaNulaNaoMarcaFalta() {
        assertFalse(NoShowPolicy.canMarkNoShow(null, START));
        assertFalse(NoShowPolicy.canMarkNoShow(START, null));
    }
}
