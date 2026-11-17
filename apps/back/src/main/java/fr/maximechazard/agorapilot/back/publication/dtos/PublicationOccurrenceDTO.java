package fr.maximechazard.agorapilot.back.publication.dtos;

import fr.maximechazard.agorapilot.back.publication.PublicationOccurrenceStatus;

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
 *
 * <p>{@code held} : la diffusion est passée son heure mais retenue par la
 * suspension d'un canal (cf. {@code OccurrenceHold}). Elle reste
 * {@code SCHEDULED}, et peut encore être déplacée ou annulée.
 */
public record PublicationOccurrenceDTO(
        Long id,
        LocalDateTime scheduledAt,
        boolean pinned,
        PublicationOccurrenceStatus status,
        PublicationDTO publication,
        List<PublicationDeliveryDTO> deliveries,
        String createdBy,
        boolean held
) {
}
