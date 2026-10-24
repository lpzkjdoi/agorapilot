package fr.maximechazard.agorapilot.back.dashboard;

import fr.maximechazard.agorapilot.back.campaign.Campaign;
import fr.maximechazard.agorapilot.back.campaign.CampaignRepository;
import fr.maximechazard.agorapilot.back.campaign.CampaignStatus;
import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.PublicationDelivery;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrenceStatus;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les compteurs et la liste du tableau de bord, contre une vraie base : ce qui
 * compte, et surtout ce qui ne compte pas.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_dashboard;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        // L'ordonnanceur ne doit pas servir les diffusions passées du jeu de test.
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "spring.jpa.show-sql=false"
})
class DashboardPersistenceTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    /** Un samedi en milieu de journée : la semaine court jusqu'au vendredi suivant inclus. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        }
    }

    @Autowired
    private DashboardService service;
    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private PublicationRepository publicationRepository;
    @Autowired
    private PublicationOccurrenceRepository publicationOccurrenceRepository;
    @Autowired
    private PublicationDeliveryRepository publicationDeliveryRepository;

    private Publication publication;

    @BeforeEach
    void setUp() {
        publicationDeliveryRepository.deleteAll();
        publicationOccurrenceRepository.deleteAll();
        publicationRepository.deleteAll();
        campaignRepository.deleteAll();
        publication = publicationRepository.save(new Publication("Marché de Noël samedi", PublicationStatus.VERIFIED));
    }

    // ------------------------------------------------------------ helpers

    private Campaign campaign(LocalDateTime start, LocalDateTime end, CampaignStatus status) {
        Campaign campaign = new Campaign("Fête du village", "Programme du week-end", start, end);
        campaign.setStatus(status);
        return campaignRepository.save(campaign);
    }

    private PublicationOccurrence occurrence(LocalDateTime scheduledAt, PublicationOccurrenceStatus status) {
        PublicationOccurrence occurrence = new PublicationOccurrence();
        occurrence.setPublication(publication);
        occurrence.setScheduledAt(scheduledAt);
        occurrence.setStatus(status);
        PublicationDelivery delivery = new PublicationDelivery();
        delivery.setChannel(DeliveryChannel.FACEBOOK);
        occurrence.addDelivery(delivery);
        return publicationOccurrenceRepository.save(occurrence);
    }

    // ------------------------------------------------------------ tests

    @Test
    void returns_zeros_and_no_upcoming_occurrence_on_an_empty_base() {
        publicationRepository.deleteAll();

        DashboardDTO summary = service.getSummary();

        assertThat(summary.activeCampaigns()).isZero();
        assertThat(summary.scheduledThisWeek()).isZero();
        assertThat(summary.draftPublications()).isZero();
        assertThat(summary.failedOccurrences()).isZero();
        assertThat(summary.upcomingOccurrences()).isEmpty();
    }

    /** Le statut stocké ne suit pas le calendrier : seules les dates font foi, sauf clôture ou annulation. */
    @Test
    void counts_campaigns_running_now_from_their_dates() {
        campaign(NOW.minusDays(3), NOW.plusDays(3), CampaignStatus.ACTIVE);
        campaign(NOW.minusDays(1), null, CampaignStatus.SCHEDULED);

        campaign(NOW.plusDays(1), null, CampaignStatus.SCHEDULED);
        campaign(NOW.minusDays(10), NOW.minusDays(1), CampaignStatus.ACTIVE);
        campaign(NOW.minusDays(3), NOW.plusDays(3), CampaignStatus.COMPLETED);
        campaign(NOW.minusDays(3), NOW.plusDays(3), CampaignStatus.CANCELED);
        campaign(null, null, CampaignStatus.DRAFT);
        Campaign archived = campaign(NOW.minusDays(3), null, CampaignStatus.ACTIVE);
        archived.setArchived(true);
        campaignRepository.save(archived);

        assertThat(service.getSummary().activeCampaigns()).isEqualTo(2);
    }

    @Test
    void counts_draft_publications_that_are_not_archived() {
        publicationRepository.save(new Publication("Brouillon 1", PublicationStatus.DRAFT));
        publicationRepository.save(new Publication("Brouillon 2", PublicationStatus.DRAFT));
        Publication archived = new Publication("Brouillon archivé", PublicationStatus.DRAFT);
        archived.setArchived(true);
        publicationRepository.save(archived);

        assertThat(service.getSummary().draftPublications()).isEqualTo(2);
    }

    /** Aujourd'hui dès minuit, et jusqu'au soir du septième jour : la période du calendrier de la semaine. */
    @Test
    void counts_scheduled_occurrences_of_the_seven_day_week() {
        occurrence(NOW.toLocalDate().atStartOfDay(), PublicationOccurrenceStatus.SCHEDULED);
        occurrence(NOW.plusDays(6).withHour(23).withMinute(59), PublicationOccurrenceStatus.SCHEDULED);

        occurrence(NOW.toLocalDate().plusDays(7).atStartOfDay(), PublicationOccurrenceStatus.SCHEDULED);
        occurrence(NOW.minusDays(1), PublicationOccurrenceStatus.SCHEDULED);
        occurrence(NOW.plusDays(1), PublicationOccurrenceStatus.PUBLISHED);
        occurrence(NOW.plusDays(1), PublicationOccurrenceStatus.FAILED);

        assertThat(service.getSummary().scheduledThisWeek()).isEqualTo(2);
    }

    @Test
    void counts_every_failed_occurrence_whatever_its_date() {
        occurrence(NOW.minusMonths(2), PublicationOccurrenceStatus.FAILED);
        occurrence(NOW.minusHours(1), PublicationOccurrenceStatus.FAILED);
        occurrence(NOW.minusHours(1), PublicationOccurrenceStatus.PUBLISHED);

        assertThat(service.getSummary().failedOccurrences()).isEqualTo(2);
    }

    @Test
    void lists_the_next_five_scheduled_occurrences_from_now_closest_first() {
        PublicationOccurrence third = occurrence(NOW.plusDays(3), PublicationOccurrenceStatus.SCHEDULED);
        PublicationOccurrence first = occurrence(NOW.plusMinutes(30), PublicationOccurrenceStatus.SCHEDULED);
        PublicationOccurrence second = occurrence(NOW.plusDays(1), PublicationOccurrenceStatus.SCHEDULED);
        PublicationOccurrence fourth = occurrence(NOW.plusDays(20), PublicationOccurrenceStatus.SCHEDULED);
        PublicationOccurrence fifth = occurrence(NOW.plusDays(40), PublicationOccurrenceStatus.SCHEDULED);
        occurrence(NOW.plusDays(50), PublicationOccurrenceStatus.SCHEDULED);

        // Heure passée mais encore en file (canal suspendu) : plus « à venir ».
        occurrence(NOW.minusMinutes(10), PublicationOccurrenceStatus.SCHEDULED);
        occurrence(NOW.plusHours(1), PublicationOccurrenceStatus.FAILED);

        assertThat(service.getSummary().upcomingOccurrences())
                .extracting(PublicationOccurrenceDTO::id)
                .containsExactly(first.getId(), second.getId(), third.getId(), fourth.getId(), fifth.getId());
    }

    @Test
    void embeds_the_publication_and_deliveries_of_upcoming_occurrences() {
        occurrence(NOW.plusDays(1), PublicationOccurrenceStatus.SCHEDULED);

        PublicationOccurrenceDTO upcoming = service.getSummary().upcomingOccurrences().getFirst();

        assertThat(upcoming.publication().content()).isEqualTo("Marché de Noël samedi");
        assertThat(upcoming.deliveries()).extracting("channel").containsExactly(DeliveryChannel.FACEBOOK);
    }
}
