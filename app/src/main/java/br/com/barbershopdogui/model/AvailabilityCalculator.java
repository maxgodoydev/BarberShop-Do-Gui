package br.com.barbershopdogui.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula horários de início realmente livres. Lógica pura (sem banco), usada pelo serviço de agenda
 * e testada isoladamente. O banco continua sendo a garantia final contra concorrência.
 */
public final class AvailabilityCalculator {
    private AvailabilityCalculator() { }

    /**
     * @param busy        intervalos já ocupados (agendamentos ativos, reservas não expiradas e bloqueios)
     * @param stepMinutes passo entre inícios possíveis (ex.: 15)
     * @param minLeadMinutes antecedência mínima em relação a {@code now}
     */
    public static List<LocalTime> freeStarts(LocalDate date, int durationMinutes, List<WorkWindow> windows,
                                             List<TimeInterval> busy, LocalDateTime now, int stepMinutes, int minLeadMinutes) {
        if (durationMinutes <= 0 || stepMinutes <= 0) {
            throw new IllegalArgumentException("Duração e passo devem ser positivos.");
        }
        List<LocalTime> result = new ArrayList<>();
        LocalDateTime earliest = now.plusMinutes(minLeadMinutes);
        for (WorkWindow w : windows) {
            LocalDateTime windowEnd = date.atTime(w.end());
            for (LocalDateTime start = date.atTime(w.start()); !start.plusMinutes(durationMinutes).isAfter(windowEnd);
                 start = start.plusMinutes(stepMinutes)) {
                if (start.isBefore(earliest)) {
                    continue;
                }
                TimeInterval candidate = new TimeInterval(start, start.plusMinutes(durationMinutes));
                if (busy.stream().noneMatch(candidate::overlaps) && !result.contains(start.toLocalTime())) {
                    result.add(start.toLocalTime());
                }
            }
        }
        result.sort(null);
        return result;
    }
}
