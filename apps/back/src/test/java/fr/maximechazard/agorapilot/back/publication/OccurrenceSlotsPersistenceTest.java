package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import fr.maximechazard.agorapilot.back.publication.exceptions.InvalidScheduleException;
import fr.maximechazard.agorapilot.back.publication.exceptions.OccurrenceNotModifiableException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationOccurrenceRequest;
import fr.maximechazard.agorapilot.back.publication.requests.RescheduleOccurrenceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Répartition des diffusions dans la fenêtre de publication, contre une vraie
 * base : ajout, annulation, déplacement et épinglage re-répartissent les
 * horaires d'un jour, et ce qui doit rester fixe le reste.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_occurrence_slots;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        // L'ordonnanceur ne doit pas balayer en parallèle du test.
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "spring.jpa.show-sql=false"
})
class OccurrenceSlotsPersistenceTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 3);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    /** Horloge figée qu'un test peut avancer. */
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
    private PublicationOccurrenceService service;
    @Autowired
    private SchedulingProperties schedulingProperties;
    @Autowired
    private PublicationRepository publicationRepository;
    @Autowired
    private PublicationOccurrenceRepository publicationOccurrenceRepository;
    @Autowired
    private PublicationDeliveryRepository publicationDeliveryRepository;
    @Autowired
    private MutableClock clock;

    private Long publicationId;

    @BeforeEach
    void setUp() {
        publicationDeliveryRepository.deleteAll();
        publicationOccurrenceRepository.deleteAll();
        publicationRepository.deleteAll();
        publicationId = publicationRepository.save(new Publication("Marché de Noël samedi", PublicationStatus.VERIFIED)).getId();
        clock.set(LocalDateTime.of(2026, 9, 25, 10, 0));
    }

    // ------------------------------------------------------------ helpers

    private Long scheduleOn(LocalDate day) {
        return service.create(request("date", day)).id();
    }

    private Long scheduleAt(LocalDateTime scheduledAt) {
        return service.create(request("scheduledAt", scheduledAt)).id();
    }

    private CreatePublicationOccurrenceRequest request(String field, Object value) {
        CreatePublicationOccurrenceRequest request = new CreatePublicationOccurrenceRequest();
        set(request, "publicationId", publicationId);
        set(request, field, value);
        set(request, "channels", Set.of(DeliveryChannel.FACEBOOK));
        return request;
    }

    private static RescheduleOccurrenceRequest reschedule(LocalDate date, LocalTime time) {
        RescheduleOccurrenceRequest request = new RescheduleOccurrenceRequest();
        set(request, "date", date);
        set(request, "time", time);
        return request;
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

    /** Ce que laisse l'ordonnanceur après un refus de Facebook. */
    private void markFailed(Long occurrenceId, String reason) {
        PublicationOccurrence occurrence = publicationOccurrenceRepository.findById(occurrenceId).orElseThrow();
        for (PublicationDelivery delivery : publicationDeliveryRepository.findAllByOccurrence(occurrence)) {
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setErrorMessage(reason);
            publicationDeliveryRepository.save(delivery);
        }
        occurrence.setStatus(PublicationOccurrenceStatus.FAILED);
        publicationOccurrenceRepository.save(occurrence);
    }

    private PublicationDelivery deliveryOf(Long occurrenceId) {
        PublicationOccurrence occurrence = publicationOccurrenceRepository.findById(occurrenceId).orElseThrow();
        return publicationDeliveryRepository.findAllByOccurrence(occurrence).getFirst();
    }

    private LocalTime timeOf(Long occurrenceId) {
        return publicationOccurrenceRepository.findById(occurrenceId).orElseThrow().getScheduledAt().toLocalTime();
    }

    private LocalDateTime at(Long occurrenceId) {
        return publicationOccurrenceRepository.findById(occurrenceId).orElseThrow().getScheduledAt();
    }

    // ------------------------------------------------------------ tests

    @Test
    void binds_the_default_window_from_the_configuration() {
        assertThat(schedulingProperties.windowStart()).isEqualTo(LocalTime.of(15, 0));
        assertThat(schedulingProperties.windowEnd()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    void places_a_lone_publication_in_the_middle_of_the_window() {
        Long id = scheduleOn(DAY);

        assertThat(at(id)).isEqualTo(DAY.atTime(18, 30));
        assertThat(publicationOccurrenceRepository.findById(id).orElseThrow().isPinned()).isFalse();
    }

    /** L'ordre de programmation est conservé : la nouvelle venue prend le dernier créneau. */
    @Test
    void spreads_the_day_again_on_each_addition() {
        Long first = scheduleOn(DAY);
        Long second = scheduleOn(DAY);

        assertThat(timeOf(first)).isEqualTo(LocalTime.of(16, 45));
        assertThat(timeOf(second)).isEqualTo(LocalTime.of(20, 15));

        Long third = scheduleOn(DAY);

        assertThat(List.of(timeOf(first), timeOf(second), timeOf(third)))
                .containsExactly(LocalTime.of(16, 10), LocalTime.of(18, 30), LocalTime.of(20, 50));
    }

    @Test
    void leaves_other_days_alone() {
        Long otherDay = scheduleOn(DAY.plusDays(1));
        scheduleOn(DAY);
        scheduleOn(DAY);

        assertThat(at(otherDay)).isEqualTo(DAY.plusDays(1).atTime(18, 30));
    }

    @Test
    void keeps_a_pinned_time_and_spreads_the_others_without_it() {
        Long pinned = scheduleAt(DAY.atTime(17, 0));
        Long automatic = scheduleOn(DAY);

        assertThat(timeOf(pinned)).isEqualTo(LocalTime.of(17, 0));
        assertThat(timeOf(automatic)).isEqualTo(LocalTime.of(18, 30));
    }

    @Test
    void ignores_what_is_already_published_that_day() {
        Long published = scheduleOn(DAY);
        PublicationOccurrence occurrence = publicationOccurrenceRepository.findById(published).orElseThrow();
        occurrence.setStatus(PublicationOccurrenceStatus.PUBLISHED);
        publicationOccurrenceRepository.save(occurrence);

        Long next = scheduleOn(DAY);

        assertThat(timeOf(published)).isEqualTo(LocalTime.of(18, 30));
        assertThat(timeOf(next)).isEqualTo(LocalTime.of(18, 30));
    }

    @Test
    void closes_the_gap_left_by_a_cancellation() {
        Long kept = scheduleOn(DAY);
        Long cancelled = scheduleOn(DAY);

        service.cancel(cancelled);

        assertThat(publicationOccurrenceRepository.findById(cancelled)).isEmpty();
        assertThat(timeOf(kept)).isEqualTo(LocalTime.of(18, 30));
    }

    /** Le glisser-déposer du calendrier : les deux jours sont répartis à nouveau. */
    @Test
    void moving_to_another_day_spreads_both_days() {
        Long stays = scheduleOn(DAY);
        Long moves = scheduleOn(DAY);
        Long alreadyThere = scheduleOn(DAY.plusDays(2));

        PublicationOccurrenceDTO moved = service.reschedule(moves, reschedule(DAY.plusDays(2), null));

        assertThat(moved.pinned()).isFalse();
        assertThat(at(stays)).isEqualTo(DAY.atTime(18, 30));
        assertThat(at(alreadyThere)).isEqualTo(DAY.plusDays(2).atTime(16, 45));
        assertThat(at(moves)).isEqualTo(DAY.plusDays(2).atTime(20, 15));
    }

    @Test
    void setting_a_time_pins_it_and_frees_its_slot() {
        Long pinnedLater = scheduleOn(DAY);
        Long other = scheduleOn(DAY);

        PublicationOccurrenceDTO dto = service.reschedule(pinnedLater, reschedule(DAY, LocalTime.of(21, 45)));

        assertThat(dto.pinned()).isTrue();
        assertThat(at(pinnedLater)).isEqualTo(DAY.atTime(21, 45));
        assertThat(timeOf(other)).isEqualTo(LocalTime.of(18, 30));
    }

    @Test
    void a_pinned_publication_can_return_to_automatic() {
        Long pinned = scheduleAt(DAY.atTime(9, 0));

        PublicationOccurrenceDTO dto = service.reschedule(pinned, reschedule(DAY, null));

        assertThat(dto.pinned()).isFalse();
        assertThat(timeOf(pinned)).isEqualTo(LocalTime.of(18, 30));
    }

    /** Programmé le jour même à 18:32 : il ne reste que 18:40–22:00. */
    @Test
    void uses_only_what_remains_of_today_s_window() {
        clock.set(DAY.atTime(18, 32));

        Long id = scheduleOn(DAY);

        // Centre de 18:40–22:00.
        assertThat(at(id)).isEqualTo(DAY.atTime(20, 20));
    }

    @Test
    void refuses_today_once_the_window_is_over() {
        clock.set(DAY.atTime(21, 58));

        assertThatThrownBy(() -> scheduleOn(DAY)).isInstanceOf(InvalidScheduleException.class);
        assertThat(publicationOccurrenceRepository.findAll()).isEmpty();
    }

    /**
     * Une diffusion imminente peut être prise en charge par l'ordonnanceur à
     * tout instant : elle n'est ni déplacée par la répartition, ni modifiable.
     */
    @Test
    void never_touches_an_imminent_publication() {
        Long imminent = scheduleOn(DAY);
        clock.set(DAY.atTime(18, 27));

        Long late = scheduleOn(DAY);

        assertThat(at(imminent)).isEqualTo(DAY.atTime(18, 30));
        assertThat(at(late)).isAfter(DAY.atTime(18, 35));
        assertThatThrownBy(() -> service.cancel(imminent)).isInstanceOf(OccurrenceNotModifiableException.class);
        assertThatThrownBy(() -> service.reschedule(imminent, reschedule(DAY.plusDays(1), null)))
                .isInstanceOf(OccurrenceNotModifiableException.class);
    }

    @Test
    void refuses_to_modify_a_publication_already_served() {
        Long served = scheduleOn(DAY);
        PublicationOccurrence occurrence = publicationOccurrenceRepository.findById(served).orElseThrow();
        occurrence.setStatus(PublicationOccurrenceStatus.PUBLISHED);
        publicationOccurrenceRepository.save(occurrence);

        assertThatThrownBy(() -> service.cancel(served)).isInstanceOf(OccurrenceNotModifiableException.class);
    }

    @Test
    void refuses_a_pinned_time_in_the_past() {
        Long id = scheduleOn(DAY);

        assertThatThrownBy(() -> service.reschedule(id, reschedule(LocalDate.of(2026, 9, 25), LocalTime.of(9, 0))))
                .isInstanceOf(InvalidScheduleException.class);
    }

    // ------------------------------------------------------------ reprise

    private static final String REFUSAL = "Confirmez votre identité (Facebook, code 368, sous-code 4854002)";

    /** Reprise vers un jour qui a déjà une diffusion : elle prend le dernier créneau. */
    @Test
    void retrying_puts_a_failed_publication_back_in_the_queue() {
        Long failed = scheduleOn(DAY);
        Long alreadyThere = scheduleOn(DAY.plusDays(1));
        markFailed(failed, REFUSAL);

        PublicationOccurrenceDTO retried = service.retry(failed, reschedule(DAY.plusDays(1), null));

        assertThat(retried.status()).isEqualTo(PublicationOccurrenceStatus.SCHEDULED);
        assertThat(retried.pinned()).isFalse();
        assertThat(deliveryOf(failed).getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(deliveryOf(failed).getErrorMessage()).isNull();
        assertThat(at(alreadyThere)).isEqualTo(DAY.plusDays(1).atTime(16, 45));
        assertThat(at(failed)).isEqualTo(DAY.plusDays(1).atTime(20, 15));
    }

    @Test
    void retrying_at_a_given_time_pins_it() {
        Long failed = scheduleOn(DAY);
        markFailed(failed, REFUSAL);

        PublicationOccurrenceDTO retried = service.retry(failed, reschedule(DAY.plusDays(1), LocalTime.of(19, 5)));

        assertThat(retried.pinned()).isTrue();
        assertThat(at(failed)).isEqualTo(DAY.plusDays(1).atTime(19, 5));
    }

    /**
     * Le jour de l'échec n'est pas réparti à nouveau : l'autre diffusion du jour
     * garde l'horaire annoncé au lieu de glisser dans le reste de la fenêtre.
     */
    @Test
    void retrying_leaves_the_day_of_the_failure_alone() {
        Long failed = scheduleOn(DAY);
        Long next = scheduleOn(DAY);
        markFailed(failed, REFUSAL);
        clock.set(DAY.atTime(17, 0));

        service.retry(failed, reschedule(DAY.plusDays(1), null));

        assertThat(at(next)).isEqualTo(DAY.atTime(20, 15));
    }

    @Test
    void refuses_to_retry_a_publication_that_did_not_fail() {
        Long scheduled = scheduleOn(DAY);

        assertThatThrownBy(() -> service.retry(scheduled, reschedule(DAY.plusDays(1), null)))
                .isInstanceOf(OccurrenceNotModifiableException.class);
    }

    /** Une reprise refusée ne doit pas laisser une diffusion à moitié remise en file. */
    @Test
    void a_refused_retry_leaves_the_failure_untouched() {
        Long failed = scheduleOn(DAY);
        markFailed(failed, REFUSAL);
        clock.set(DAY.atTime(21, 58));

        assertThatThrownBy(() -> service.retry(failed, reschedule(DAY, null)))
                .isInstanceOf(InvalidScheduleException.class);

        assertThat(publicationOccurrenceRepository.findById(failed).orElseThrow().getStatus())
                .isEqualTo(PublicationOccurrenceStatus.FAILED);
        assertThat(deliveryOf(failed).getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(deliveryOf(failed).getErrorMessage()).isEqualTo(REFUSAL);
        assertThat(at(failed)).isEqualTo(DAY.atTime(18, 30));
    }

    @Test
    void lists_a_range_in_chronological_order() {
        Long later = scheduleOn(DAY.plusDays(1));
        Long first = scheduleOn(DAY);
        scheduleOn(DAY.plusDays(40));

        assertThat(service.findBetween(DAY, DAY.plusDays(2)))
                .extracting(PublicationOccurrenceDTO::id)
                .containsExactly(first, later);
    }
}
