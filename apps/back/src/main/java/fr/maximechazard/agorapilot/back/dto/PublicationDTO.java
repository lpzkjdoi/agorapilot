package fr.maximechazard.agorapilot.back.dto;

import fr.maximechazard.agorapilot.back.PublicationStatus;

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
