package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.mappers.PublicationOccurrenceMapper;
import fr.maximechazard.agorapilot.back.publication.exceptions.InvalidScheduleException;
import fr.maximechazard.agorapilot.back.publication.exceptions.OccurrenceNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.OccurrenceNotModifiableException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.UnsupportedDeliveryChannelException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationOccurrenceRequest;
import fr.maximechazard.agorapilot.back.publication.requests.RescheduleOccurrenceRequest;
import fr.maximechazard.agorapilot.back.publisher.PublisherRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class PublicationOccurrenceService {
    private static final int WEEK_LENGTH = 7;
    private static final int MAX_RANGE_DAYS = 93;

    /**
     * En deçà, une occurrence n'est plus ni déplacée ni modifiable : l'ordonnanceur
     * peut la prendre en charge à tout instant, la toucher serait une course.
     */
    static final Duration FROZEN_HORIZON = Duration.ofMinutes(5);

    private final PublicationRepository publicationRepository;
    private final PublicationOccurrenceRepository publicationOccurrenceRepository;
    private final PublicationDeliveryRepository publicationDeliveryRepository;
    private final PublisherRegistry publisherRegistry;
    private final PublicationOccurrenceMapper publicationOccurrenceMapper;
    private final SchedulingProperties schedulingProperties;
    private final Clock clock;

    /**
     * Programme une diffusion : une occurrence porteuse d'une livraison
     * {@code PENDING} par canal. L'ordonnanceur la servira le moment venu.
     * <p>
     * Avec une {@code date} seule, l'heure est choisie ici : l'occurrence rejoint
     * les diffusions automatiques du jour, et toutes sont réparties à nouveau
     * dans la fenêtre de publication. Avec {@code scheduledAt}, l'heure est
     * épinglée et n'influence pas la répartition des autres.
     * <p>
     * Les canaux sans publisher sont refusés ici plutôt qu'au moment de la
     * diffusion : mieux vaut un 501 immédiat qu'une occurrence qui échouera
     * silencieusement dans trois jours.
     *
     * @throws PublicationNotFoundException        la publication n'existe pas
     * @throws UnsupportedDeliveryChannelException un des canaux n'a pas de publisher
     * @throws InvalidScheduleException            jour passé, ou fenêtre du jour close
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

        if (request.getDate() == null) {
            occurrence.setScheduledAt(request.getScheduledAt());
            occurrence.setPinned(true);
            return publicationOccurrenceMapper.toDTO(publicationOccurrenceRepository.save(occurrence));
        }

        placeLastInWindow(occurrence, request.getDate());
        PublicationOccurrence saved = publicationOccurrenceRepository.save(occurrence);
        redistribute(request.getDate());

        return publicationOccurrenceMapper.toDTO(saved);
    }

    /**
     * Déplace une diffusion vers un autre jour, fixe son heure, ou la rend à la
     * répartition automatique (cf. {@link RescheduleOccurrenceRequest}). Le jour
     * quitté et le jour d'arrivée sont tous deux répartis à nouveau.
     *
     * @throws OccurrenceNotFoundException      l'occurrence n'existe pas
     * @throws OccurrenceNotModifiableException déjà servie, en cours, ou imminente
     * @throws InvalidScheduleException         jour ou heure qui ne peut plus l'accueillir
     */
    @Transactional
    public PublicationOccurrenceDTO reschedule(Long occurrenceId, RescheduleOccurrenceRequest request) {
        PublicationOccurrence occurrence = findModifiable(occurrenceId);
        LocalDate previousDay = occurrence.getScheduledAt().toLocalDate();

        if (request.getTime() == null) {
            placeLastInWindow(occurrence, request.getDate());
        } else {
            LocalDateTime scheduledAt = request.getDate().atTime(request.getTime());
            if (!scheduledAt.isAfter(frozenUntil())) {
                throw new InvalidScheduleException("L'heure demandée (" + scheduledAt
                        + ") est passée ou trop proche : choisir une heure au-delà de "
                        + FROZEN_HORIZON.toMinutes() + " minutes.");
            }
            occurrence.setScheduledAt(scheduledAt);
            occurrence.setPinned(true);
        }

        PublicationOccurrence saved = publicationOccurrenceRepository.save(occurrence);

        redistribute(previousDay);
        if (!previousDay.equals(request.getDate())) {
            redistribute(request.getDate());
        }

        return publicationOccurrenceMapper.toDTO(saved);
    }

    /**
     * Annule une diffusion programmée ; les autres diffusions du jour se
     * répartissent à nouveau dans la fenêtre.
     *
     * @throws OccurrenceNotFoundException      l'occurrence n'existe pas
     * @throws OccurrenceNotModifiableException déjà servie, en cours, ou imminente
     */
    @Transactional
    public void cancel(Long occurrenceId) {
        PublicationOccurrence occurrence = findModifiable(occurrenceId);
        LocalDate day = occurrence.getScheduledAt().toLocalDate();

        publicationOccurrenceRepository.delete(occurrence);
        redistribute(day);
    }

    /** Les occurrences de {@code [from, to)}, dans l'ordre chronologique. */
    @Transactional(readOnly = true)
    public List<PublicationOccurrenceDTO> findBetween(LocalDate from, LocalDate to) {
        if (!from.isBefore(to) || from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new InvalidScheduleException("Intervalle invalide : 'to' doit suivre 'from', sur "
                    + MAX_RANGE_DAYS + " jours au plus.");
        }

        return publicationOccurrenceRepository
                .findAllByScheduledAtGreaterThanEqualAndScheduledAtLessThan(from.atStartOfDay(), to.atStartOfDay())
                .stream()
                .sorted(Comparator.comparing(PublicationOccurrence::getScheduledAt))
                .map(publicationOccurrenceMapper::toDTO)
                .toList();
    }

    /**
     * Répartit uniformément les diffusions automatiques Facebook du jour dans ce
     * qui reste de sa fenêtre de publication.
     * <p>
     * Ne bougent que les occurrences encore {@code SCHEDULED}, non épinglées, et
     * au-delà de l'horizon figé : une occurrence imminente peut être prise en
     * charge par l'ordonnanceur à tout instant, la déplacer serait une course.
     * L'ordre relatif est conservé — une nouvelle venue, placée en fin de
     * fenêtre, prend le dernier créneau.
     */
    private void redistribute(LocalDate day) {
        Optional<Window> window = remainingWindow(day);

        if (window.isEmpty()) {
            return;
        }

        LocalDateTime frozenUntil = frozenUntil();
        List<PublicationOccurrence> movable = publicationOccurrenceRepository
                .findAllByScheduledAtGreaterThanEqualAndScheduledAtLessThan(day.atStartOfDay(), day.plusDays(1).atStartOfDay())
                .stream()
                .filter(occurrence -> occurrence.getStatus() == PublicationOccurrenceStatus.SCHEDULED)
                .filter(occurrence -> !occurrence.isPinned())
                .filter(occurrence -> occurrence.getScheduledAt().isAfter(frozenUntil))
                .filter(PublicationOccurrenceService::goesToFacebook)
                .sorted(Comparator.comparing(PublicationOccurrence::getScheduledAt)
                        .thenComparing(PublicationOccurrence::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (movable.isEmpty()) {
            return;
        }

        List<LocalDateTime> slots = SlotPlanner.plan(window.get().start(), window.get().end(), movable.size());

        for (int i = 0; i < movable.size(); i++) {
            PublicationOccurrence occurrence = movable.get(i);
            if (!occurrence.getScheduledAt().equals(slots.get(i))) {
                occurrence.setScheduledAt(slots.get(i));
                publicationOccurrenceRepository.save(occurrence);
            }
        }
    }

    /**
     * Rend l'occurrence automatique et la place provisoirement en fin de fenêtre :
     * la répartition qui suit lui attribue le dernier créneau du jour.
     *
     * @throws InvalidScheduleException jour passé, ou fenêtre du jour déjà close
     */
    private void placeLastInWindow(PublicationOccurrence occurrence, LocalDate day) {
        Window window = remainingWindow(day).orElseThrow(() -> new InvalidScheduleException(
                "Plus aucune publication possible le " + day + " : la fenêtre "
                        + schedulingProperties.windowStart() + "–" + schedulingProperties.windowEnd()
                        + " est passée. Choisir un autre jour."));

        occurrence.setScheduledAt(window.end());
        occurrence.setPinned(false);
    }

    /**
     * La part de la fenêtre de publication du jour encore utilisable : toute la
     * fenêtre pour un jour à venir, ce qu'il en reste au-delà de l'horizon figé
     * pour aujourd'hui, rien pour un jour passé ou une fenêtre close.
     */
    private Optional<Window> remainingWindow(LocalDate day) {
        LocalDateTime start = day.atTime(schedulingProperties.windowStart());
        LocalDateTime end = day.atTime(schedulingProperties.windowEnd());
        LocalDateTime earliest = SlotPlanner.ceilToGranularity(frozenUntil());

        if (earliest.isAfter(start)) {
            start = earliest;
        }

        return start.isBefore(end) ? Optional.of(new Window(start, end)) : Optional.empty();
    }

    private PublicationOccurrence findModifiable(Long occurrenceId) {
        PublicationOccurrence occurrence = publicationOccurrenceRepository.findById(occurrenceId)
                .orElseThrow(() -> new OccurrenceNotFoundException("Occurrence with id " + occurrenceId + " does not exist."));

        boolean untouched = occurrence.getStatus() == PublicationOccurrenceStatus.SCHEDULED
                && occurrence.getDeliveries().stream().allMatch(delivery -> delivery.getStatus() == DeliveryStatus.PENDING);

        if (!untouched || !occurrence.getScheduledAt().isAfter(frozenUntil())) {
            throw new OccurrenceNotModifiableException("La diffusion " + occurrenceId
                    + " est déjà traitée ou imminente (moins de " + FROZEN_HORIZON.toMinutes()
                    + " minutes) : elle ne peut plus être modifiée.");
        }

        return occurrence;
    }

    private LocalDateTime frozenUntil() {
        return LocalDateTime.now(clock).plus(FROZEN_HORIZON);
    }

    private record Window(LocalDateTime start, LocalDateTime end) {
    }

    /** Les créneaux ne concernent que Facebook. */
    private static boolean goesToFacebook(PublicationOccurrence occurrence) {
        return occurrence.getDeliveries().stream().anyMatch(delivery -> delivery.getChannel() == DeliveryChannel.FACEBOOK);
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
        LocalDate today = LocalDate.now(clock);
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
