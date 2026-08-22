package fr.maximechazard.agorapilot.back.publication.dtos;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.DeliveryStatus;

import java.time.LocalDateTime;

public record PublicationDeliveryDTO(
        Long id,
        Long occurrenceId,
        DeliveryChannel channel,
        DeliveryStatus status,
        LocalDateTime publishedAt,
        String externalId
) {
}
