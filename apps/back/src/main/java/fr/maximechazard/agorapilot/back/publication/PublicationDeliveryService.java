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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private final SchedulingProperties schedulingProperties;
    private final PlatformTransactionManager transactionManager;

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
     * Sert les livraisons d'une occurrence due — le geste que répète
     * l'ordonnanceur à chaque balayage.
     * <p>
     * Volontairement sans transaction englobante : chaque étape valide la sienne,
     * pour que la base dise à tout instant ce qui a été tenté.
     * <ol>
     *   <li><b>Prise en charge</b> — les livraisons {@code PENDING} passent
     *   {@code IN_PROGRESS}, et c'est validé <em>avant</em> tout appel distant.
     *   Si le back tombe pendant la diffusion, rien ne reste {@code PENDING} :
     *   aucune republication au redémarrage.</li>
     *   <li><b>Diffusion</b> — une transaction par canal, pour que le résultat
     *   d'un canal soit acquis même si le suivant échoue.</li>
     *   <li><b>Résolution</b> du statut de l'occurrence.</li>
     * </ol>
     * Une livraison {@code FAILED} n'est jamais retentée : le canal distant peut
     * avoir accepté la publication avant de rompre la connexion. La reprise reste
     * un geste explicite.
     * <p>
     * Aucune exception n'est levée quand un canal échoue : l'échec est tracé sur
     * la livraison, et les autres canaux de l'occurrence doivent être servis
     * malgré tout.
     */
    public void publishScheduledOccurrence(Long occurrenceId) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Optional<List<Long>> claimed = transaction.execute(status -> claim(occurrenceId));

        if (claimed == null || claimed.isEmpty()) {
            // Supprimée entre le balayage et sa prise en charge : rien à diffuser.
            log.warn("Scheduled occurrence {} no longer exists, skipped.", occurrenceId);
            return;
        }

        for (Long deliveryId : claimed.get()) {
            try {
                transaction.executeWithoutResult(status -> deliver(deliveryId));
            } catch (RuntimeException e) {
                // Le publisher trace normalement ses échecs lui-même ; une exception
                // qui lui échappe laisserait la livraison IN_PROGRESS jusqu'au
                // balayage suivant. Autant solder tout de suite, avec le vrai motif.
                log.error("Delivery {} of occurrence {} failed:", deliveryId, occurrenceId, e);
                transaction.executeWithoutResult(status -> markFailed(deliveryId, e.getMessage()));
            }
        }

        transaction.executeWithoutResult(status -> publicationOccurrenceRepository.findById(occurrenceId)
                .ifPresent(publicationOccurrenceService::refreshStatus));
    }

    /**
     * Décide du sort de chaque livraison de l'occurrence et renvoie celles à
     * diffuser, désormais {@code IN_PROGRESS}.
     */
    private Optional<List<Long>> claim(Long occurrenceId) {
        Optional<PublicationOccurrence> found = publicationOccurrenceRepository.findById(occurrenceId);

        if (found.isEmpty()) {
            return Optional.empty();
        }

        PublicationOccurrence occurrence = found.get();
        Duration maxLateness = schedulingProperties.maxLateness();
        boolean tooLate = LocalDateTime.now().isAfter(occurrence.getScheduledAt().plus(maxLateness));
        List<Long> claimed = new ArrayList<>();

        for (PublicationDelivery delivery : publicationDeliveryRepository.findAllByOccurrence(occurrence)) {
            switch (delivery.getStatus()) {
                // Les balayages ne se chevauchent pas : une livraison encore
                // IN_PROGRESS au début d'un balayage vient d'une diffusion
                // interrompue (arrêt du back, base perdue après l'appel distant).
                case IN_PROGRESS -> markFailed(delivery,
                        "Delivery interrupted before its outcome was recorded: "
                                + "check " + delivery.getChannel() + " before publishing again.");
                case PENDING -> {
                    if (publisherRegistry.forChannel(delivery.getChannel()).isEmpty()) {
                        // Sans trace d'échec, la livraison resterait PENDING et
                        // l'occurrence reviendrait à chaque balayage.
                        markFailed(delivery, "No publisher available for channel " + delivery.getChannel() + ".");
                    } else if (tooLate) {
                        markFailed(delivery, "Not published: more than " + maxLateness.toMinutes()
                                + " minutes late (scheduled at " + occurrence.getScheduledAt() + ").");
                    } else {
                        delivery.setStatus(DeliveryStatus.IN_PROGRESS);
                        publicationDeliveryRepository.save(delivery);
                        claimed.add(delivery.getId());
                    }
                }
                default -> {
                    // PUBLISHED ou FAILED : déjà tranchée, jamais rejouée.
                }
            }
        }

        return Optional.of(claimed);
    }

    private void deliver(Long deliveryId) {
        PublicationDelivery delivery = publicationDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Delivery " + deliveryId + " no longer exists."));

        Publisher publisher = publisherRegistry.forChannel(delivery.getChannel())
                .orElseThrow(() -> new IllegalStateException(
                        "No publisher available for channel " + delivery.getChannel() + "."));

        publisher.publish(delivery.getOccurrence());
    }

    private void markFailed(Long deliveryId, String errorMessage) {
        publicationDeliveryRepository.findById(deliveryId)
                .ifPresent(delivery -> markFailed(delivery, errorMessage));
    }

    private void markFailed(PublicationDelivery delivery, String errorMessage) {
        log.warn("Delivery {} on {} failed: {}", delivery.getId(), delivery.getChannel(), errorMessage);

        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setErrorMessage(errorMessage);
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
