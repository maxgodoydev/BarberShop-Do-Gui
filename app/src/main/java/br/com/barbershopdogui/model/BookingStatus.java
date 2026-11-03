package br.com.barbershopdogui.model;

/** Estados de um agendamento. Os que "ocupam agenda" bloqueiam o intervalo do profissional. */
public enum BookingStatus {
    RESERVA_TEMPORARIA(true), SOLICITADO(true), CONFIRMADO(true), CONCLUIDO(true), CANCELADO(false), EXPIRADO(false), FALTOU(false);

    private final boolean occupiesAgenda;

    BookingStatus(boolean occupiesAgenda) {
        this.occupiesAgenda = occupiesAgenda;
    }

    public boolean occupiesAgenda() {
        return occupiesAgenda;
    }

    public static BookingStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Informe um status válido.");
        }
        try {
            return BookingStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Status inválido.");
        }
    }
}
