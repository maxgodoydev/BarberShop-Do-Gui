package br.com.barbershopdogui.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Varre reservas temporárias vencidas. A disponibilidade já ignora reservas vencidas; a varredura mantém o histórico correto. */
@Component
public class HoldExpirationJob {
    private static final Logger LOG = LoggerFactory.getLogger(HoldExpirationJob.class);
    private final BookingService bookings;

    public HoldExpirationJob(BookingService bookings) {
        this.bookings = bookings;
    }

    @Scheduled(fixedDelayString = "${app.booking.expire-sweep-ms:60000}")
    public void sweep() {
        try {
            int n = bookings.expireHolds();
            if (n > 0) {
                LOG.info("{} reserva(s) temporária(s) expirada(s).", n);
            }
        } catch (RuntimeException ex) {
            LOG.warn("Falha na varredura de reservas: {}", ex.getClass().getSimpleName());
        }
    }
}
