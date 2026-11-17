package fr.maximechazard.agorapilot.back.publication.recurrence.dtos;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.recurrence.OccurrenceSeriesStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/**
 * Vue d'une série exposée au front.
 *
 * @param publishTime    heure fixe, ou nulle pour une heure automatique
 * @param upcoming       nombre de diffusions de la série encore programmées
 */
public record OccurrenceSeriesDTO(
        Long id,
        Long publicationId,
        String rule,
        LocalDate startDate,
        LocalTime publishTime,
        Set<DeliveryChannel> channels,
        OccurrenceSeriesStatus status,
        String stopReason,
        long upcoming,
        String createdBy,
        LocalDateTime createdAt
) {
}
