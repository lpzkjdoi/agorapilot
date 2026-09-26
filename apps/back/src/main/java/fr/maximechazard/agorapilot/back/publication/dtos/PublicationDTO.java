package fr.maximechazard.agorapilot.back.publication.dtos;

import fr.maximechazard.agorapilot.back.media.dtos.MediaSummaryDTO;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;

import java.util.List;

/**
 * @param medias   visuels rattachés, dans l'ordre choisi ; liste vide s'il n'y en a aucun
 * @param campaign campagne de rattachement, {@code null} si la publication n'en a pas
 */
public record PublicationDTO(
        Long id,
        String content,
        PublicationStatus status,
        List<MediaSummaryDTO> medias,
        PublicationCampaignDTO campaign
) {
}
