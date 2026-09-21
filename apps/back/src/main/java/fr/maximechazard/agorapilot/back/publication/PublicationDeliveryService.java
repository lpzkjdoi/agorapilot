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
import java.util.Optional;

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
    private final PublicationOccurrenceService publicationOccurrenceService;
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

        publicationOccurrenceService.refreshStatus(occurrence);

        if (delivery.getStatus() == DeliveryStatus.FAILED) {
            log.warn("Immediate delivery of publication {} on {} failed: {}",
                    publicationId, channel, delivery.getErrorMessage());
            throw new DeliveryFailedException(delivery.getErrorMessage());
        }

        return publicationDeliveryMapper.toDTO(delivery);
    }

    /**
     * Sert les livraisons encore en attente d'une occurrence planifiée — le geste
     * que répète l'ordonnanceur à chaque balayage.
     * <p>
     * Seules les livraisons {@code PENDING} sont diffusées. Une livraison déjà
     * {@code FAILED} n'est pas retentée : le canal distant peut avoir accepté la
     * publication avant de rompre la connexion, et une reprise automatique
     * publierait deux fois. La reprise reste donc un geste explicite.
     * <p>
     * Aucune exception n'est levée quand un canal échoue : l'échec est tracé sur
     * la livraison, et les autres canaux de l'occurrence doivent être servis
     * malgré tout.
     */
    @Transactional
    public void publishScheduledOccurrence(Long occurrenceId) {
        Optional<PublicationOccurrence> found = publicationOccurrenceRepository.findById(occurrenceId);

        if (found.isEmpty()) {
            // Supprimée entre le balayage et sa prise en charge : rien à diffuser.
            log.warn("Scheduled occurrence {} no longer exists, skipped.", occurrenceId);
            return;
        }

        PublicationOccurrence occurrence = found.get();

        for (PublicationDelivery delivery : publicationDeliveryRepository.findAllByOccurrence(occurrence)) {
            if (delivery.getStatus() != DeliveryStatus.PENDING) {
                continue;
            }

            publisherRegistry.forChannel(delivery.getChannel()).ifPresentOrElse(
                    publisher -> publisher.publish(occurrence),
                    () -> markUnsupported(delivery));
        }

        publicationOccurrenceService.refreshStatus(occurrence);
    }

    /**
     * Sans publisher, la livraison resterait {@code PENDING} et l'occurrence
     * reviendrait à chaque balayage. L'échec est tracé pour que l'occurrence
     * sorte de la file et que la raison soit lisible.
     */
    private void markUnsupported(PublicationDelivery delivery) {
        log.warn("No publisher available for channel {} on occurrence {}.",
                delivery.getChannel(), delivery.getOccurrence().getId());

        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setErrorMessage("No publisher available for channel " + delivery.getChannel() + ".");
        publicationDeliveryRepository.save(delivery);
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
}
