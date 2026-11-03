package br.com.barbershopdogui.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Agenda semanal: só a semana atual abre; a próxima só quando a atual não tem horário livre. */
public final class BookingWeek {
    private BookingWeek() { }

    public static LocalDate mondayOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate targetWeekStart(LocalDate today, boolean currentWeekFull) {
        LocalDate monday = mondayOf(today);
        return currentWeekFull ? monday.plusWeeks(1) : monday;
    }

    public static boolean isBookable(LocalDate date, LocalDate today, boolean currentWeekFull) {
        LocalDate start = targetWeekStart(today, currentWeekFull);
        return !date.isBefore(today) && !date.isBefore(start) && date.isBefore(start.plusWeeks(1));
    }
}
