package fr.maximechazard.agorapilot.back.publication.recurrence;

import fr.maximechazard.agorapilot.back.campaign.Campaign;
import fr.maximechazard.agorapilot.back.campaign.CampaignRepository;
import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrenceService;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrenceStatus;
import fr.maximechazard.agorapilot.back.publication.PublicationService;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import fr.maximechazard.agorapilot.back.publication.exceptions.InvalidScheduleException;
import fr.maximechazard.agorapilot.back.publication.exceptions.OccurrenceNotModifiableException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publication.recurrence.dtos.OccurrenceSeriesDTO;
import fr.maximechazard.agorapilot.back.publication.recurrence.requests.CreateOccurrenceSeriesRequest;
import fr.maximechazard.agorapilot.back.publication.requests.RescheduleOccurrenceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Programmation par fréquence contre une vraie base : création des diffusions
 * sur l'horizon, curseur (une diffusion retirée n'est pas recréée), fin de la
 * règle, arrêt à la main, avec la campagne ou la suppression de la publication.
 * <p>
 * Horizon réduit pour le test : 30 jours, 5 diffusions à venir par série.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_series;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "agorapilot.recurrence.horizon-days=30",
        "agorapilot.recurrence.horizon-count=5",
        "agorapilot.recurrence.cron=-",
        "spring.jpa.show-sql=false"
})
class OccurrenceSeriesPersistenceTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    /** Vendredi 25/09/2026, 10:00. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 25, 10, 0);
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private Instant instant = Instant.now();

        void set(LocalDateTime now) {
            this.instant = now.atZone(ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Autowired
    private OccurrenceSeriesService seriesService;
    @Autowired
    private OccurrenceSeriesRepository seriesRepository;
    @Autowired
    private PublicationOccurrenceService occurrenceService;
    @Autowired
    private PublicationService publicationService;
    @Autowired
    private PublicationRepository publicationRepository;
    @Autowired
    private PublicationOccurrenceRepository occurrenceRepository;
    @Autowired
    private PublicationDeliveryRepository deliveryRepository;
    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private MutableClock clock;

    private Long publicationId;

    @BeforeEach
    void setUp() {
        deliveryRepository.deleteAll();
        occurrenceRepository.deleteAll();
        seriesRepository.deleteAll();
        publicationRepository.deleteAll();
        publicationId = publicationRepository.save(new Publication("Marché du lundi", PublicationStatus.VERIFIED)).getId();
        clock.set(NOW);
    }

    // ------------------------------------------------------------ helpers

    private OccurrenceSeriesDTO create(String rule, LocalDate start, LocalTime time) {
        CreateOccurrenceSeriesRequest request = new CreateOccurrenceSeriesRequest();
        set(request, "publicationId", publicationId);
        set(request, "channels", Set.of(DeliveryChannel.FACEBOOK));
        set(request, "rule", rule);
        set(request, "startDate", start);
        set(request, "time", time);
        return seriesService.create(request);
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Les diffusions d'une série, dans l'ordre chronologique. */
    private List<PublicationOccurrence> occurrencesOf(Long seriesId) {
        return new TransactionTemplate(transactionManager).execute(status -> occurrenceRepository.findAll().stream()
                .filter(occurrence -> occurrence.getSeries() != null && occurrence.getSeries().getId().equals(seriesId))
                .sorted(Comparator.comparing(PublicationOccurrence::getScheduledAt))
                .toList());
    }

    private List<LocalDate> daysOf(Long seriesId) {
        return occurrencesOf(seriesId).stream().map(occurrence -> occurrence.getScheduledAt().toLocalDate()).toList();
    }

    private OccurrenceSeries series(Long seriesId) {
        return seriesRepository.findById(seriesId).orElseThrow();
    }

    // ------------------------------------------------------------ tests

    @Test
    void creates_the_occurrences_of_the_horizon_linked_to_their_series() {
        OccurrenceSeriesDTO series = create("FREQ=WEEKLY", MONDAY, null);

        assertThat(daysOf(series.id())).containsExactly(
                MONDAY, MONDAY.plusWeeks(1), MONDAY.plusWeeks(2), MONDAY.plusWeeks(3));
        PublicationOccurrence first = occurrencesOf(series.id()).getFirst();
        assertThat(first.getSeriesDate()).isEqualTo(MONDAY);
        assertThat(first.isPinned()).isFalse();
        assertThat(first.getScheduledAt().toLocalTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(series.upcoming()).isEqualTo(4);
        assertThat(series(series.id()).getGeneratedThrough()).isEqualTo(MONDAY.plusWeeks(3));

        List<PublicationOccurrenceDTO> listed = occurrenceService.findBetween(MONDAY, MONDAY.plusDays(1));
        assertThat(listed).singleElement().satisfies(dto -> assertThat(dto.seriesId()).isEqualTo(series.id()));
    }

    @Test
    void pins_every_occurrence_at_the_fixed_time() {
        OccurrenceSeriesDTO series = create("FREQ=WEEKLY;COUNT=2", MONDAY, LocalTime.of(9, 15));

        assertThat(occurrencesOf(series.id())).allSatisfy(occurrence -> {
            assertThat(occurrence.isPinned()).isTrue();
            assertThat(occurrence.getScheduledAt().toLocalTime()).isEqualTo(LocalTime.of(9, 15));
        });
    }

    @Test
    void stops_at_the_horizon_count_then_extends_day_after_day() {
        OccurrenceSeriesDTO series = create("FREQ=DAILY", MONDAY, null);
        assertThat(daysOf(series.id())).hasSize(5).last().isEqualTo(MONDAY.plusDays(4));

        // Lundi passé, le job du soir : la diffusion du lundi est partie (ici : publiée à la main).
        clock.set(MONDAY.atTime(23, 0));
        PublicationOccurrence monday = occurrencesOf(series.id()).getFirst();
        monday.setStatus(PublicationOccurrenceStatus.PUBLISHED);
        occurrenceRepository.save(monday);

        seriesService.generateAll();

        assertThat(daysOf(series.id())).hasSize(6).last().isEqualTo(MONDAY.plusDays(5));
    }

    @Test
    void never_recreates_an_occurrence_cancelled_or_moved_by_hand() {
        OccurrenceSeriesDTO series = create("FREQ=WEEKLY", MONDAY, null);
        List<PublicationOccurrence> occurrences = occurrencesOf(series.id());

        occurrenceService.cancel(occurrences.get(0).getId());
        RescheduleOccurrenceRequest moved = new RescheduleOccurrenceRequest();
        set(moved, "date", MONDAY.plusDays(2));
        set(moved, "time", null);
        occurrenceService.reschedule(occurrences.get(1).getId(), moved);

        seriesService.generateAll();

        assertThat(daysOf(series.id())).containsExactly(
                MONDAY.plusDays(2), MONDAY.plusWeeks(2), MONDAY.plusWeeks(3));
    }

    @Test
    void skips_the_dates_missed_while_the_job_did_not_run() {
        OccurrenceSeriesDTO series = create("FREQ=WEEKLY", MONDAY, null);
        // Rien ne tourne pendant cinq semaines : les quatre diffusions passées restent au test.
        clock.set(MONDAY.plusWeeks(5).atTime(1, 0));

        seriesService.generateAll();

        assertThat(daysOf(series.id())).filteredOn(day -> day.isAfter(MONDAY.plusWeeks(3)))
                // Le lundi manqué (semaine 4) n'est pas rattrapé ; la suite reprend jusqu'à l'horizon.
                .containsExactly(MONDAY.plusWeeks(5), MONDAY.plusWeeks(6), MONDAY.plusWeeks(7), MONDAY.plusWeeks(8),
                        MONDAY.plusWeeks(9));
    }

    @Test
    void completes_a_bounded_rule_once_all_its_dates_are_created() {
        OccurrenceSeriesDTO series = create("FREQ=WEEKLY;COUNT=2", MONDAY, null);

        assertThat(daysOf(series.id())).containsExactly(MONDAY, MONDAY.plusWeeks(1));
        assertThat(series.status()).isEqualTo(OccurrenceSeriesStatus.COMPLETED);
    }

    @Test
    void stopping_a_series_cancels_its_upcoming_occurrences_and_keeps_it_stopped() {
        OccurrenceSeriesDTO series = create("FREQ=WEEKLY", MONDAY, null);

        OccurrenceSeriesDTO stopped = seriesService.stop(series.id());
        seriesService.generateAll();

        assertThat(stopped.status()).isEqualTo(OccurrenceSeriesStatus.STOPPED);
        assertThat(stopped.upcoming()).isZero();
        assertThat(daysOf(series.id())).isEmpty();
        assertThatThrownBy(() -> seriesService.stop(series.id())).isInstanceOf(OccurrenceNotModifiableException.class);
    }

    @Test
    void closing_the_campaign_stops_its_series() {
        Campaign campaign = campaignRepository.save(new Campaign("Fêtes", "", NOW, NOW.plusMonths(3)));
        Publication publication = publicationRepository.findById(publicationId).orElseThrow();
        publication.setCampaign(campaign);
        publicationRepository.save(publication);
        OccurrenceSeriesDTO series = create("FREQ=WEEKLY", MONDAY, null);

        occurrenceService.cancelScheduledOfCampaign(campaign.getId());

        assertThat(series(series.id()).getStatus()).isEqualTo(OccurrenceSeriesStatus.STOPPED);
        assertThat(daysOf(series.id())).isEmpty();
    }

    @Test
    void deleting_a_publication_never_broadcast_takes_its_series_along() {
        OccurrenceSeriesDTO series = create("FREQ=WEEKLY", MONDAY, null);

        publicationService.delete(publicationId);

        assertThat(seriesRepository.findById(series.id())).isEmpty();
        assertThat(publicationRepository.findById(publicationId)).isEmpty();
    }

    @Test
    void a_series_waits_while_its_publication_is_back_to_draft() {
        OccurrenceSeriesDTO series = create("FREQ=DAILY", MONDAY, null);
        Publication publication = publicationRepository.findById(publicationId).orElseThrow();
        publication.setStatus(PublicationStatus.DRAFT);
        publicationRepository.save(publication);
        clock.set(MONDAY.plusDays(2).atTime(23, 0));

        seriesService.generateAll();

        assertThat(daysOf(series.id())).hasSize(5);
        assertThat(series(series.id()).getStatus()).isEqualTo(OccurrenceSeriesStatus.ACTIVE);
    }

    @Test
    void refuses_a_start_in_the_past_or_an_end_before_the_start() {
        assertThatThrownBy(() -> create("FREQ=WEEKLY", NOW.toLocalDate().minusDays(1), null))
                .isInstanceOf(InvalidScheduleException.class).hasMessageContaining("passé");
        assertThatThrownBy(() -> create("FREQ=WEEKLY;UNTIL=20260927", MONDAY, null))
                .isInstanceOf(InvalidScheduleException.class).hasMessageContaining("précède");
        assertThat(seriesRepository.count()).isZero();
    }
}
