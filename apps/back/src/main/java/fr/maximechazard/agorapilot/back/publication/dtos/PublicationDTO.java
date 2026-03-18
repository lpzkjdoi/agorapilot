package fr.maximechazard.agorapilot.back.publication.dtos;

import fr.maximechazard.agorapilot.back.publication.PublicationStatus;

public record PublicationDTO(
        Long id,
        String content,
        PublicationStatus status
) {
}
