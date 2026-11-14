package fr.maximechazard.agorapilot.back.notification.summary;

import fr.maximechazard.agorapilot.back.campaign.Campaign;
import fr.maximechazard.agorapilot.back.notification.Notification;
import fr.maximechazard.agorapilot.back.notification.NotificationEntry;
import fr.maximechazard.agorapilot.back.notification.NotificationField;
import fr.maximechazard.agorapilot.back.notification.NotificationLinks;
import fr.maximechazard.agorapilot.back.notification.NotificationSeverity;
import fr.maximechazard.agorapilot.back.notification.NotificationType;
import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.PublicationDelivery;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrenceStatus;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailySummaryServiceTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    /** Dimanche 15 novembre 2026, 7h à Paris. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T06:00:00Z"), PARIS);
    private static final LocalDate DAY = LocalDate.of(2026, 11, 15);
    private static final String CALENDAR = "https://agorapilot.test/calendrier";

    private final PublicationOccurrenceRepository repository = mock(PublicationOccurrenceRepository.class);
    private final DailySummaryService service =
            new DailySummaryService(repository, new NotificationLinks("https://agorapilot.test"), CLOCK);

    private static PublicationOccurrence occurrence(String content, int hour, int minute, PublicationOccurrenceStatus status) {
        Publication publication = new Publication(content, PublicationStatus.VERIFIED);
        PublicationOccurrence occurrence = new PublicationOccurrence();
        occurrence.setPublication(publication);
        occurrence.setScheduledAt(DAY.atTime(hour, minute));
        occurrence.setStatus(status);
        PublicationDelivery delivery = new PublicationDelivery();
        delivery.setChannel(DeliveryChannel.FACEBOOK);
        occurrence.addDelivery(delivery);
        return occurrence;
    }

    private void given(List<PublicationOccurrence> occurrences, long failedYesterday) {
        when(repository.findAllByScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay())).thenReturn(occurrences);
        when(repository.countByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                eq(PublicationOccurrenceStatus.FAILED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(failedYesterday);
    }

    @Test
    void lists_the_day_in_chronological_order() {
        PublicationOccurrence concert = occurrence("Concert de l'harmonie", 18, 15, PublicationOccurrenceStatus.SCHEDULED);
        PublicationOccurrence market = occurrence("Marché de Noël – ouverture", 10, 30, PublicationOccurrenceStatus.SCHEDULED);
        Campaign campaign = new Campaign();
        campaign.setName("Noël solidaire");
        PublicationOccurrence toys = occurrence("Collecte de jouets", 14, 0, PublicationOccurrenceStatus.SCHEDULED);
        toys.getPublication().setCampaign(campaign);
        given(List.of(concert, market, toys), 0);

        Notification summary = service.today();

        assertThat(summary.type()).isEqualTo(NotificationType.DAILY_SUMMARY);
        assertThat(summary.severity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(summary.title()).isEqualTo("Programme du dimanche 15 novembre");
        assertThat(summary.summary()).isEqualTo("3 diffusions prévues aujourd'hui.");
        assertThat(summary.link()).isEqualTo(CALENDAR);
        assertThat(summary.entries()).containsExactly(
                new NotificationEntry(Instant.parse("2026-11-15T09:30:00Z"), "Marché de Noël – ouverture", "Facebook", CALENDAR),
                new NotificationEntry(Instant.parse("2026-11-15T13:00:00Z"), "Collecte de jouets",
                        "Facebook · Campagne Noël solidaire", CALENDAR),
                new NotificationEntry(Instant.parse("2026-11-15T17:15:00Z"), "Concert de l'harmonie", "Facebook", CALENDAR));
        assertThat(summary.fields()).containsExactly(
                NotificationField.inline("Prévues", "3"),
                NotificationField.inline("Canaux", "Facebook"),
                NotificationField.inline("Échecs hier", "0"));
    }

    /** Envoyé même vide : son absence voudrait dire que le service ne tourne plus. */
    @Test
    void says_so_when_nothing_is_planned() {
        given(List.of(), 0);

        Notification summary = service.today();

        assertThat(summary.summary()).isEqualTo("Aucune diffusion prévue aujourd'hui.");
        assertThat(summary.entries()).isEmpty();
        assertThat(summary.fields()).contains(NotificationField.inline("Canaux", "—"));
    }

    @Test
    void uses_the_singular_for_a_single_diffusion() {
        given(List.of(occurrence("Loto", 15, 0, PublicationOccurrenceStatus.SCHEDULED)), 0);

        assertThat(service.today().summary()).isEqualTo("1 diffusion prévue aujourd'hui.");
    }

    @Test
    void ignores_archived_publications() {
        PublicationOccurrence archived = occurrence("Ancienne annonce", 9, 0, PublicationOccurrenceStatus.SCHEDULED);
        archived.getPublication().setArchived(true);
        given(List.of(archived), 0);

        assertThat(service.today().entries()).isEmpty();
    }

    @Test
    void flags_yesterday_failures_and_occurrences_already_handled() {
        given(List.of(
                occurrence("Tôt le matin", 6, 0, PublicationOccurrenceStatus.PUBLISHED),
                occurrence("Raté", 6, 30, PublicationOccurrenceStatus.FAILED)), 2);

        Notification summary = service.today();

        assertThat(summary.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(summary.fields()).contains(NotificationField.inline("Échecs hier", "2"));
        assertThat(summary.entries()).extracting(NotificationEntry::detail)
                .containsExactly("Facebook · déjà diffusée", "Facebook · en échec");
    }
}
