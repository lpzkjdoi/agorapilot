package fr.maximechazard.agorapilot.back.publication.dtos;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vue d'une occurrence exposée au front.
 *
 * <p>{@code deliveries} porte des {@link PublicationDeliveryDTO} et non des
 * entités {@code PublicationDelivery} : l'entité référence en retour son
 * occurrence, et Jackson sérialiserait alors le cycle
 * {@code occurrence → deliveries → occurrence → …} jusqu'au débordement de pile,
 * après avoir déjà commencé à écrire une réponse 200 illisible.
 */
public record PublicationOccurrenceDTO(
        Long id,
        LocalDateTime scheduledAt,
        PublicationDTO publication,
        List<PublicationDeliveryDTO> deliveries
) {
}
