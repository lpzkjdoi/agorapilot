package fr.maximechazard.agorapilot.back.publication.recurrence.requests;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Corps de {@code POST /api/occurrence-series} : programmer une publication par
 * fréquence.
 * <p>
 * {@code rule} est une {@code RRULE} (RFC 5545) : {@code FREQ=WEEKLY;INTERVAL=2},
 * avec {@code COUNT} ou {@code UNTIL} pour une fin. {@code time} vide : chaque
 * diffusion prend une heure automatique dans la fenêtre de son jour.
 */
@Getter
@SuppressWarnings("unused")
public class CreateOccurrenceSeriesRequest {

    @NotNull
    private Long publicationId;

    @NotNull
    @NotEmpty
    private Set<DeliveryChannel> channels;

    @NotBlank
    @Size(max = 255)
    private String rule;

    @NotNull
    private LocalDate startDate;

    private LocalTime time;
}
