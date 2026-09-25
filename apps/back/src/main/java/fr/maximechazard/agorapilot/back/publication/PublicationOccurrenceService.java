package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.mappers.PublicationOccurrenceMapper;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.UnsupportedDeliveryChannelException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationOccurrenceRequest;
import fr.maximechazard.agorapilot.back.publisher.PublisherRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class PublicationOccurrenceService {
    private static final int WEEK_LENGTH = 7;

    private final PublicationRepository publicationRepository;
    private final PublicationOccurrenceRepository publicationOccurrenceRepository;
    private final PublicationDeliveryRepository publicationDeliveryRepository;
    private final PublisherRegistry publisherRegistry;
    private final PublicationOccurrenceMapper publicationOccurrenceMapper;

    /**
     * Planifie une diffusion : une occurrence à l'heure demandée, porteuse d'une
     * livraison {@code PENDING} par canal. L'ordonnanceur les servira le moment venu.
     * <p>
     * Les canaux sans publisher sont refusés ici plutôt qu'au moment de la
     * diffusion : mieux vaut un 501 immédiat qu'une occurrence qui échouera
     * silencieusement dans trois jours.
     *
     * @throws PublicationNotFoundException        la publication n'existe pas
     * @throws UnsupportedDeliveryChannelException un des canaux n'a pas de publisher
     */
    @Transactional
    public PublicationOccurrenceDTO create(CreatePublicationOccurrenceRequest request) {
        Publication publication = publicationRepository.findById(request.getPublicationId())
                .orElseThrow(() -> new PublicationNotFoundException("Publication with id " + request.getPublicationId() + " does not exist."));

        PublicationOccurrence occurrence = new PublicationOccurrence();

        for (DeliveryChannel channel : request.getChannels()) {
            if (publisherRegistry.forChannel(channel).isEmpty()) {
                throw new UnsupportedDeliveryChannelException("No publisher available for channel " + channel + ".");
            }

            PublicationDelivery delivery = new PublicationDelivery();
            delivery.setChannel(channel);
            occurrence.addDelivery(delivery);
        }

        occurrence.setPublication(publication);
        occurrence.setScheduledAt(request.getScheduledAt());

        return publicationOccurrenceMapper.toDTO(publicationOccurrenceRepository.save(occurrence));
    }

    /**
     * Aligne le statut de l'occurrence sur celui de ses livraisons.
     * <p>
     * Tant qu'un canal est {@code PENDING} ou {@code IN_PROGRESS}, l'occurrence
     * reste {@code SCHEDULED} et l'ordonnanceur la reprendra — pour servir le
     * premier, ou solder le second s'il reste bloqué après une interruption. Une fois tous les canaux tranchés elle sort
     * de la file : {@code PUBLISHED} si tout est parti, {@code FAILED} dès qu'un
     * canal a échoué — auquel cas la reprise est un geste explicite, pas une
     * boucle automatique.
     */
    @Transactional
    public void refreshStatus(PublicationOccurrence occurrence) {
        List<PublicationDelivery> deliveries = publicationDeliveryRepository.findAllByOccurrence(occurrence);

        if (deliveries.isEmpty() || deliveries.stream().anyMatch(PublicationOccurrenceService::isUnresolved)) {
            return;
        }

        PublicationOccurrenceStatus status = deliveries.stream().anyMatch(delivery -> delivery.getStatus() == DeliveryStatus.FAILED)
                ? PublicationOccurrenceStatus.FAILED
                : PublicationOccurrenceStatus.PUBLISHED;

        if (occurrence.getStatus() != status) {
            occurrence.setStatus(status);
            publicationOccurrenceRepository.save(occurrence);
        }
    }

    /**
     * Les occurrences des sept jours à venir, aujourd'hui compris, rangées par
     * jour. La borne haute est exclusive : minuit du huitième jour.
     */
    public Map<String, List<PublicationOccurrenceDTO>> getWeeklyOccurrences() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(WEEK_LENGTH).atStartOfDay();
        List<PublicationOccurrence> occurrences = publicationOccurrenceRepository
                .findAllByScheduledAtGreaterThanEqualAndScheduledAtLessThan(start, end);
        List<PublicationOccurrenceDTO> occurrencesDto = occurrences.stream().map(publicationOccurrenceMapper::toDTO).toList();
        return createMap(today, occurrencesDto);
    }

    private static boolean isUnresolved(PublicationDelivery delivery) {
        return delivery.getStatus() == DeliveryStatus.PENDING || delivery.getStatus() == DeliveryStatus.IN_PROGRESS;
    }

    private Map<String, List<PublicationOccurrenceDTO>> createMap(LocalDate today, List<PublicationOccurrenceDTO> occurrences) {
        Map<String, List<PublicationOccurrenceDTO>> map = new TreeMap<>();

        for (int i = 0; i < WEEK_LENGTH; i++) {
            LocalDate day = today.plusDays(i);
            List<PublicationOccurrenceDTO> list = occurrences.stream().filter(occurrence -> occurrence.scheduledAt().toLocalDate().equals(day)).toList();
            map.put(day.atStartOfDay().toString(), list);
        }

        return map;
    }
}
