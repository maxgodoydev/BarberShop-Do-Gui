package br.com.barbershopdogui.model;

import java.time.LocalTime;

/** Janela de trabalho diária de um profissional (uma pausa = duas janelas no mesmo dia). */
public record WorkWindow(LocalTime start, LocalTime end) {
    public WorkWindow {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Janela inválida: o fim deve ser posterior ao início.");
        }
    }
}
