package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDeliveryDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.mappers.PublicationDeliveryMapper;
import fr.maximechazard.agorapilot.back.publication.exceptions.DeliveryFailedException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.UnsupportedDeliveryChannelException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publisher.Publisher;
import fr.maximechazard.agorapilot.back.publisher.PublisherRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Diffusion d'une publication sur un canal.
 * <p>
 * Le modèle métier ne connaît que des livraisons rattachées à une occurrence :
 * publier « maintenant » revient donc à créer une occurrence datée de l'instant
 * présent, porteuse d'une unique livraison, puis à la diffuser immédiatement.
 * La diffusion reste ainsi tracée comme n'importe quelle diffusion planifiée
 * (historique, statut, identifiant distant, calendrier de la semaine).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicationDeliveryService {

    private final PublicationRepository publicationRepository;
    private final PublicationOccurrenceRepository publicationOccurrenceRepository;
    private final PublicationDeliveryRepository publicationDeliveryRepository;
    private final PublisherRegistry publisherRegistry;
    private final PublicationDeliveryMapper publicationDeliveryMapper;

    /**
     * Publie immédiatement une publication sur le canal demandé.
     * <p>
     * {@code noRollbackFor} est essentiel : le publisher enregistre l'échec sur
     * la livraison ({@code FAILED} + message d'erreur), et un rollback effacerait
     * cette trace en même temps qu'il remonterait l'exception à l'appelant.
     *
     * @throws PublicationNotFoundException        la publication n'existe pas
     * @throws UnsupportedDeliveryChannelException aucun publisher pour ce canal
     * @throws DeliveryFailedException             le canal distant a refusé la publication
     */
    @Transactional(noRollbackFor = DeliveryFailedException.class)
    public PublicationDeliveryDTO publishNow(Long publicationId, DeliveryChannel channel) {
        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new PublicationNotFoundException(
                        "Publication with id " + publicationId + " does not exist."));

        Publisher publisher = publisherRegistry.forChannel(channel)
                .orElseThrow(() -> new UnsupportedDeliveryChannelException(
                        "No publisher available for channel " + channel + "."));

        PublicationOccurrence occurrence = createImmediateOccurrence(publication, channel);
        publisher.publish(occurrence);

        PublicationDelivery delivery = publicationDeliveryRepository
                .findByOccurrenceAndChannel(occurrence, channel)
                .orElseThrow(() -> new IllegalStateException(
                        "No " + channel + " delivery on occurrence " + occurrence.getId() + "."));

        if (delivery.getStatus() == DeliveryStatus.FAILED) {
            log.warn("Immediate delivery of publication {} on {} failed: {}",
                    publicationId, channel, delivery.getErrorMessage());
            throw new DeliveryFailedException(delivery.getErrorMessage());
        }

        markOccurrencePublishedIfComplete(occurrence);

        return publicationDeliveryMapper.toDTO(delivery);
    }

    private PublicationOccurrence createImmediateOccurrence(Publication publication, DeliveryChannel channel) {
        PublicationOccurrence occurrence = new PublicationOccurrence();
        occurrence.setPublication(publication);
        occurrence.setScheduledAt(LocalDateTime.now());

        PublicationDelivery delivery = new PublicationDelivery();
        delivery.setChannel(channel);
        occurrence.addDelivery(delivery);

        return publicationOccurrenceRepository.save(occurrence);
    }

    /** Une occurrence n'est publiée que lorsque tous ses canaux ont été servis. */
    private void markOccurrencePublishedIfComplete(PublicationOccurrence occurrence) {
        List<PublicationDelivery> deliveries = publicationDeliveryRepository.findAllByOccurrence(occurrence);
        boolean allPublished = !deliveries.isEmpty() && deliveries.stream()
                .allMatch(delivery -> delivery.getStatus() == DeliveryStatus.PUBLISHED);

        if (allPublished && occurrence.getStatus() != PublicationOccurrenceStatus.PUBLISHED) {
            occurrence.setStatus(PublicationOccurrenceStatus.PUBLISHED);
            publicationOccurrenceRepository.save(occurrence);
        }
    }
}
