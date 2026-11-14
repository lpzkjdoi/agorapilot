package fr.maximechazard.agorapilot.back.publication.events;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.DeliveryStatus;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.PublicationDelivery;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Une occurrence vient de passer en échec : au moins un de ses canaux n'a pas
 * pu être servi.
 * <p>
 * L'événement emporte tout ce qu'il faut pour en parler, copié pendant la
 * transaction : ses écouteurs s'exécutent après validation, sur un autre fil,
 * où les associations paresseuses de l'entité ne seraient plus lisibles.
 */
public record OccurrenceFailedEvent(
        Long occurrenceId,
        Long publicationId,
        String publicationContent,
        String campaignName,
        LocalDateTime scheduledAt,
        List<FailedDelivery> failures
) {

    public record FailedDelivery(DeliveryChannel channel, String errorMessage) {
    }

    public OccurrenceFailedEvent {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public static OccurrenceFailedEvent of(PublicationOccurrence occurrence, List<PublicationDelivery> deliveries) {
        Publication publication = occurrence.getPublication();

        List<FailedDelivery> failures = deliveries.stream()
                .filter(delivery -> delivery.getStatus() == DeliveryStatus.FAILED)
                .map(delivery -> new FailedDelivery(delivery.getChannel(), delivery.getErrorMessage()))
                .toList();

        return new OccurrenceFailedEvent(
                occurrence.getId(),
                publication == null ? null : publication.getId(),
                publication == null ? null : publication.getContent(),
                publication == null || publication.getCampaign() == null ? null : publication.getCampaign().getName(),
                occurrence.getScheduledAt(),
                failures
        );
    }
}
