package fr.maximechazard.agorapilot.back.publication.dtos;

import fr.maximechazard.agorapilot.back.media.dtos.MediaSummaryDTO;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param medias   visuels rattachés, dans l'ordre choisi ; liste vide s'il n'y en a aucun
 * @param campaign campagne de rattachement, {@code null} si la publication n'en a pas
 * @param createdBy pseudo de l'auteur, {@code null} pour une publication antérieure à la traçabilité
 * @param updatedBy pseudo du dernier à l'avoir modifiée
 */
public record PublicationDTO(
        Long id,
        String content,
        PublicationStatus status,
        List<MediaSummaryDTO> medias,
        PublicationCampaignDTO campaign,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime updatedAt,
        String updatedBy
) {
}
