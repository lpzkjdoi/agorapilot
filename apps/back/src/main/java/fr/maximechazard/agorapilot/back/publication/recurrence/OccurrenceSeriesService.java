package fr.maximechazard.agorapilot.back.publication.recurrence;

import fr.maximechazard.agorapilot.back.account.events.SecurityEventService;
import fr.maximechazard.agorapilot.back.account.events.SecurityEventType;
import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrenceService;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrenceStatus;
import fr.maximechazard.agorapilot.back.publication.PublicationService;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import fr.maximechazard.agorapilot.back.publication.exceptions.InvalidScheduleException;
import fr.maximechazard.agorapilot.back.publication.exceptions.OccurrenceNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.OccurrenceNotModifiableException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotVerifiedException;
import fr.maximechazard.agorapilot.back.publication.exceptions.UnsupportedDeliveryChannelException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publication.recurrence.dtos.OccurrenceSeriesDTO;
import fr.maximechazard.agorapilot.back.publication.recurrence.requests.CreateOccurrenceSeriesRequest;
import fr.maximechazard.agorapilot.back.publisher.PublisherRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Programmation par fréquence : création, génération des diffusions et arrêt
 * des séries.
 * <p>
 * Les diffusions ne sont pas créées d'un coup jusqu'à la fin de la règle — elle
 * peut ne pas en avoir —, mais à l'avance sur un horizon borné
 * ({@link RecurrenceProperties}) : à la création de la série, puis chaque nuit.
 * Chacune est une diffusion ordinaire, qui se déplace, s'annule ou se reprend
 * comme les autres ; le curseur de la série garantit qu'une diffusion retirée
 * à la main n'est jamais recréée.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OccurrenceSeriesService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OccurrenceSeriesRepository seriesRepository;
    private final PublicationRepository publicationRepository;
    private final PublicationOccurrenceRepository occurrenceRepository;
    private final PublicationOccurrenceService occurrenceService;
    private final PublisherRegistry publisherRegistry;
    private final RecurrenceProperties properties;
    private final SecurityEventService events;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    /**
     * Crée la série et ses premières diffusions.
     *
     * @throws PublicationNotFoundException        la publication n'existe pas
     * @throws PublicationNotVerifiedException     la publication n'est pas vérifiée
     * @throws UnsupportedDeliveryChannelException un des canaux n'a pas de publisher
     * @throws InvalidScheduleException            règle invalide, départ passé, ou règle qui ne produit aucune date
     */
    @Transactional
    public OccurrenceSeriesDTO create(CreateOccurrenceSeriesRequest request) {
        Publication publication = publicationRepository.findByIdAndArchivedFalse(request.getPublicationId())
                .orElseThrow(() -> new PublicationNotFoundException(
                        "Publication with id " + request.getPublicationId() + " does not exist."));

        if (publication.getStatus() != PublicationStatus.VERIFIED) {
            throw new PublicationNotVerifiedException(publication.getId());
        }

        for (DeliveryChannel channel : request.getChannels()) {
            if (publisherRegistry.forChannel(channel).isEmpty()) {
                throw new UnsupportedDeliveryChannelException("No publisher available for channel " + channel + ".");
            }
        }

        RecurrenceRule rule = RecurrenceRule.parse(request.getRule());
        LocalDate today = LocalDate.now(clock);

        if (request.getStartDate().isBefore(today)) {
            throw new InvalidScheduleException("La répétition ne peut pas commencer dans le passé ("
                    + request.getStartDate().format(DAY) + ").");
        }
        if (rule.until() != null && rule.until().isBefore(request.getStartDate())) {
            throw new InvalidScheduleException("La fin de la répétition (" + rule.until().format(DAY)
                    + ") précède son début (" + request.getStartDate().format(DAY) + ").");
        }
        if (rule.dates(request.getStartDate(), request.getStartDate().plusYears(5)).isEmpty()) {
            throw new InvalidScheduleException("Cette règle ne produit aucune date dans les cinq ans à venir.");
        }

        OccurrenceSeries series = new OccurrenceSeries();
        series.setPublication(publication);
        series.setRule(rule.toString());
        series.setStartDate(request.getStartDate());
        series.setPublishTime(request.getTime());
        series.setChannels(request.getChannels());
        OccurrenceSeries saved = seriesRepository.save(series);

        int created = generate(saved);
        events.recordAction(SecurityEventType.SERIES_CREATED, "Série " + saved.getId() + " de "
                + PublicationService.excerpt(publication) + " : " + saved.getRule() + " à partir du "
                + saved.getStartDate().format(DAY) + " (" + created + " diffusion(s) créée(s))");

        return toDTO(saved);
    }

    /** Les séries d'une publication, la plus récente d'abord. */
    @Transactional(readOnly = true)
    public List<OccurrenceSeriesDTO> findByPublication(Long publicationId) {
        return seriesRepository.findAllByPublicationIdOrderByCreatedAtDesc(publicationId).stream().map(this::toDTO).toList();
    }

    /**
     * Arrête la série à la main : ses diffusions encore programmées sont annulées.
     *
     * @throws OccurrenceNotFoundException      la série n'existe pas
     * @throws OccurrenceNotModifiableException elle n'est plus active, ou une de ses diffusions est imminente
     */
    @Transactional
    public OccurrenceSeriesDTO stop(Long seriesId) {
        OccurrenceSeries series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new OccurrenceNotFoundException("Series with id " + seriesId + " does not exist."));

        if (series.getStatus() != OccurrenceSeriesStatus.ACTIVE) {
            throw new OccurrenceNotModifiableException("La série " + seriesId + " est déjà terminée ou arrêtée.");
        }

        occurrenceService.stopSeries(series, "Arrêtée à la main.");
        return toDTO(series);
    }

    /**
     * Le geste du job quotidien : prolonge chaque série active jusqu'à l'horizon.
     * Une transaction par série, pour qu'une série en erreur n'empêche pas les
     * autres de produire.
     *
     * @return le nombre de diffusions créées
     */
    public int generateAll() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        int created = 0;

        for (OccurrenceSeries listed : seriesRepository.findAllByStatus(OccurrenceSeriesStatus.ACTIVE)) {
            try {
                Integer count = transaction.execute(status -> seriesRepository.findById(listed.getId())
                        .map(this::generate).orElse(0));
                created += count == null ? 0 : count;
            } catch (RuntimeException e) {
                log.error("Génération de la série {} impossible :", listed.getId(), e);
            }
        }

        return created;
    }

    /**
     * Crée les diffusions de la série jusqu'à l'horizon, à partir de son curseur.
     * Les dates déjà passées (job manqué) sont écartées sans être rattrapées : une
     * annonce en retard n'a plus lieu d'être.
     *
     * @return le nombre de diffusions créées
     */
    int generate(OccurrenceSeries series) {
        if (series.getStatus() != OccurrenceSeriesStatus.ACTIVE) {
            return 0;
        }

        Publication publication = series.getPublication();

        if (Boolean.TRUE.equals(publication.getArchived())) {
            series.stop("Publication supprimée.");
            return 0;
        }

        if (publication.getStatus() != PublicationStatus.VERIFIED) {
            // Repassée en brouillon : la série attend, sans rien créer, qu'on la revérifie.
            log.info("Série {} en attente : la publication {} n'est plus vérifiée.", series.getId(), publication.getId());
            return 0;
        }

        RecurrenceRule rule = series.recurrence();
        LocalDate today = LocalDate.now(clock);
        LocalDate horizon = today.plusDays(properties.horizonDays());
        LocalDate cursor = series.getGeneratedThrough();
        long upcoming = upcoming(series);
        int created = 0;

        for (LocalDate date : rule.dates(series.getStartDate(), horizon)) {
            if (cursor != null && !date.isAfter(cursor)) {
                continue;
            }
            if (!date.isBefore(today) && upcoming >= properties.horizonCount()) {
                break;
            }
            if (!date.isBefore(today) && occurrenceService.scheduleFromSeries(series, date)) {
                upcoming++;
                created++;
            }
            cursor = date;
        }

        series.setGeneratedThrough(cursor);

        if (isExhausted(rule, series.getStartDate(), cursor)) {
            series.setStatus(OccurrenceSeriesStatus.COMPLETED);
        }

        seriesRepository.save(series);
        return created;
    }

    /** Une règle bornée (date ou nombre) dont toutes les dates ont été traitées. */
    private static boolean isExhausted(RecurrenceRule rule, LocalDate start, LocalDate cursor) {
        if (rule.count() == null && rule.until() == null) {
            return false;
        }

        LocalDate end = rule.until() != null ? rule.until() : start.plusYears(100);
        List<LocalDate> all = rule.dates(start, end);

        return all.isEmpty() || (cursor != null && !all.getLast().isAfter(cursor));
    }

    private long upcoming(OccurrenceSeries series) {
        return occurrenceRepository.countBySeriesAndStatusAndScheduledAtAfter(
                series, PublicationOccurrenceStatus.SCHEDULED, LocalDateTime.now(clock));
    }

    private OccurrenceSeriesDTO toDTO(OccurrenceSeries series) {
        return new OccurrenceSeriesDTO(
                series.getId(),
                series.getPublication().getId(),
                series.getRule(),
                series.getStartDate(),
                series.getPublishTime(),
                series.channelSet(),
                series.getStatus(),
                series.getStopReason(),
                upcoming(series),
                series.getCreatedBy(),
                series.getCreatedAt()
        );
    }
}
