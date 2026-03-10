package fr.maximechazard.agorapilot.back.publication.dtos;

import fr.maximechazard.agorapilot.back.publication.PublicationStatus;

import java.time.LocalDateTime;

public record PublicationDTO(
        Long id,
        String content,
        PublicationStatus status,
        LocalDateTime scheduledAt,
        LocalDateTime publishedAt,
        LocalDateTime endAt
) {
}
