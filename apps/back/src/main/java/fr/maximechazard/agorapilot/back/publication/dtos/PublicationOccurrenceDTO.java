package fr.maximechazard.agorapilot.back.publication.dtos;

import fr.maximechazard.agorapilot.back.publication.PublicationDelivery;

import java.time.LocalDateTime;
import java.util.List;

public record PublicationOccurrenceDTO(
        Long id,
        LocalDateTime scheduledAt,
        PublicationDTO publication,
        List<PublicationDelivery> deliveries
) {
}
