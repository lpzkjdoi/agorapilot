package fr.maximechazard.agorapilot.back.publication.dtos;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.DeliveryStatus;

import java.time.LocalDateTime;

/**
 * @param errorMessage motif de l'échec quand {@code status} vaut {@code FAILED} —
 *                     refus de Facebook, retard dépassé, diffusion interrompue —
 *                     affiché tel quel dans le calendrier
 */
public record PublicationDeliveryDTO(
        Long id,
        Long occurrenceId,
        DeliveryChannel channel,
        DeliveryStatus status,
        LocalDateTime publishedAt,
        String externalId,
        String errorMessage
) {
}
