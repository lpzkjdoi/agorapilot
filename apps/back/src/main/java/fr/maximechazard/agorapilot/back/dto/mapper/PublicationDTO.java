package fr.maximechazard.agorapilot.back.dto.mapper;

import fr.maximechazard.agorapilot.back.PublicationStatus;

import java.time.LocalDateTime;

public record PublicationDTO(
        Long id,
        String content,
        PublicationStatus Status,
        LocalDateTime scheduledAt,
        LocalDateTime publishedAt,
        LocalDateTime endAt
) {
}
