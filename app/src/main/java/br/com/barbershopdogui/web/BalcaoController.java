package br.com.barbershopdogui.web;

import br.com.barbershopdogui.service.AvailabilityService;
import br.com.barbershopdogui.service.BookingService;
import br.com.barbershopdogui.web.Dtos.BookingResult;
import br.com.barbershopdogui.web.Dtos.CounterBookingRequest;
import br.com.barbershopdogui.web.Dtos.SlotGroup;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Agendamento no balcão (recepção ou proprietário) para clientes sem cadastro ou sem e-mail. */
@RestController
@RequestMapping("/api/admin/balcao")
public class BalcaoController {
    private final BookingService bookings;
    private final AvailabilityService availability;
    private final Clock clock;

    public BalcaoController(BookingService bookings, AvailabilityService availability, Clock clock) {
        this.bookings = bookings;
        this.availability = availability;
        this.clock = clock;
    }

    /** Horários livres sem a antecedência mínima do site (o cliente está na loja). */
    @GetMapping("/horarios")
    public List<SlotGroup> slots(@RequestParam(required = false) List<Long> servicoIds, @RequestParam(required = false) Long profissionalId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        if (servicoIds == null || servicoIds.isEmpty()) {
            return List.of();
        }
        if (servicoIds.size() > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha de 1 a 3 serviços.");
        }
        return availability.slots(servicoIds, profissionalId, data, LocalDateTime.now(clock), 0);
    }

    @PostMapping("/agendamentos")
    public BookingResult create(@Valid @RequestBody CounterBookingRequest in, Principal principal) {
        return bookings.createCounterBooking(in, principal.getName());
    }
}
