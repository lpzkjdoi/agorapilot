package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.campaign.Campaign;
import fr.maximechazard.agorapilot.back.campaign.CampaignRepository;
import fr.maximechazard.agorapilot.back.campaign.CampaignService;
import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.exceptions.OccurrenceNotModifiableException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationOccurrenceRequest;
import fr.maximechazard.agorapilot.back.publication.requests.RescheduleOccurrenceRequest;
import fr.maximechazard.agorapilot.back.publication.requests.UpdatePublicationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Modification et suppression d'une publication contre une vraie base : ce que
 * la suppression annule, ce qu'elle garde comme historique, et ce qui la bloque.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_publication_crud;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        // L'ordonnanceur ne doit pas servir les diffusions du jeu de test.
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "spring.jpa.show-sql=false"
})
class PublicationCrudPersistenceTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 10, 0);
    private static final LocalDate TOMORROW = NOW.toLocalDate().plusDays(1);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        }
    }

    @Autowired
    private PublicationService service;
    @Autowired
    private PublicationOccurrenceService occurrenceService;
    @Autowired
    private CampaignService campaignService;
    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private PublicationRepository publicationRepository;
    @Autowired
    private PublicationOccurrenceRepository publicationOccurrenceRepository;
    @Autowired
    private PublicationDeliveryRepository publicationDeliveryRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long publicationId;

    @BeforeEach
    void setUp() {
        publicationDeliveryRepository.deleteAll();
        publicationOccurrenceRepository.deleteAll();
        publicationRepository.deleteAll();
        campaignRepository.deleteAll();
        publicationId = publicationRepository.save(new Publication("Marché de Noël samedi", PublicationStatus.DRAFT)).getId();
    }

    // ------------------------------------------------------------ helpers

    private Long scheduleOn(Long id, LocalDate day) {
        CreatePublicationOccurrenceRequest request = new CreatePublicationOccurrenceRequest();
        set(request, "publicationId", id);
        set(request, "date", day);
        set(request, "channels", Set.of(DeliveryChannel.FACEBOOK));
        return occurrenceService.create(request).id();
    }

    /** Une occurrence posée directement en base, avec le statut voulu pour son unique livraison Facebook. */
    private Long occurrenceAt(LocalDateTime scheduledAt, PublicationOccurrenceStatus status, DeliveryStatus deliveryStatus) {
        PublicationOccurrence occurrence = new PublicationOccurrence();
        occurrence.setPublication(publicationRepository.findById(publicationId).orElseThrow());
        occurrence.setScheduledAt(scheduledAt);
        occurrence.setStatus(status);
        PublicationDelivery delivery = new PublicationDelivery();
        delivery.setChannel(DeliveryChannel.FACEBOOK);
        delivery.setStatus(deliveryStatus);
        occurrence.addDelivery(delivery);
        return publicationOccurrenceRepository.save(occurrence).getId();
    }

    private static UpdatePublicationRequest update(String content, PublicationStatus status) {
        UpdatePublicationRequest request = new UpdatePublicationRequest();
        set(request, "content", content);
        set(request, "status", status);
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

    // ------------------------------------------------------------ modification

    @Test
    void updates_the_content_and_the_status() {
        PublicationDTO updated = service.update(publicationId, update("Marché de Noël dimanche", PublicationStatus.VERIFIED));

        assertThat(updated.content()).isEqualTo("Marché de Noël dimanche");
        assertThat(updated.status()).isEqualTo(PublicationStatus.VERIFIED);
        Publication stored = publicationRepository.findById(publicationId).orElseThrow();
        assertThat(stored.getContent()).isEqualTo("Marché de Noël dimanche");
        assertThat(stored.getStatus()).isEqualTo(PublicationStatus.VERIFIED);
    }

    /** Les diffusions à venir lisent la publication au moment de partir : elles suivent la modification. */
    @Test
    void keeps_the_scheduled_occurrences_of_an_updated_publication() {
        Long occurrenceId = scheduleOn(publicationId, TOMORROW);

        service.update(publicationId, update("Marché de Noël dimanche", PublicationStatus.VERIFIED));

        PublicationOccurrence occurrence = publicationOccurrenceRepository.findById(occurrenceId).orElseThrow();
        assertThat(occurrence.getStatus()).isEqualTo(PublicationOccurrenceStatus.SCHEDULED);
    }

    @Test
    void refuses_to_update_an_unknown_publication() {
        assertThatThrownBy(() -> service.update(404L, update("Texte", PublicationStatus.DRAFT)))
                .isInstanceOf(PublicationNotFoundException.class);
    }

    // ------------------------------------------------------------ suppression

    @Test
    void erases_a_publication_that_was_never_scheduled() {
        service.delete(publicationId);

        assertThat(publicationRepository.findById(publicationId)).isEmpty();
    }

    /** Les autres diffusions du jour reprennent la place libérée. */
    @Test
    void cancels_the_scheduled_occurrences_and_spreads_their_day_again() {
        Long other = publicationRepository.save(new Publication("Conseil municipal", PublicationStatus.VERIFIED)).getId();
        Long otherOccurrence = scheduleOn(other, TOMORROW);
        scheduleOn(publicationId, TOMORROW);
        assertThat(publicationOccurrenceRepository.findById(otherOccurrence).orElseThrow().getScheduledAt().toLocalTime())
                .isEqualTo(LocalTime.of(16, 45));

        service.delete(publicationId);

        assertThat(publicationRepository.findById(publicationId)).isEmpty();
        assertThat(publicationOccurrenceRepository.findAll()).extracting(PublicationOccurrence::getId).containsExactly(otherOccurrence);
        assertThat(publicationOccurrenceRepository.findById(otherOccurrence).orElseThrow().getScheduledAt())
                .isEqualTo(TOMORROW.atTime(18, 30));
    }

    @Test
    void erases_failures_that_published_nothing() {
        occurrenceAt(NOW.minusDays(1), PublicationOccurrenceStatus.FAILED, DeliveryStatus.FAILED);

        service.delete(publicationId);

        assertThat(publicationRepository.findById(publicationId)).isEmpty();
        assertThat(publicationOccurrenceRepository.findAll()).isEmpty();
    }

    /** Ce qui est parti reste dans le calendrier : la publication est archivée, pas effacée. */
    @Test
    void archives_a_published_publication_and_keeps_only_its_history() {
        Long published = occurrenceAt(NOW.minusDays(2), PublicationOccurrenceStatus.PUBLISHED, DeliveryStatus.PUBLISHED);
        occurrenceAt(NOW.minusDays(1), PublicationOccurrenceStatus.FAILED, DeliveryStatus.FAILED);
        scheduleOn(publicationId, TOMORROW);

        service.delete(publicationId);

        Publication archived = publicationRepository.findById(publicationId).orElseThrow();
        assertThat(archived.getArchived()).isTrue();
        assertThat(publicationOccurrenceRepository.findAll()).extracting(PublicationOccurrence::getId).containsExactly(published);
    }

    @Test
    void hides_an_archived_publication_everywhere() {
        Campaign campaign = campaignRepository.save(new Campaign("Fêtes de fin d'année", "Annonces", null, null));
        service.setCampaign(publicationId, campaign.getId());
        occurrenceAt(NOW.minusDays(2), PublicationOccurrenceStatus.PUBLISHED, DeliveryStatus.PUBLISHED);

        service.delete(publicationId);

        assertThat(service.getAll()).isEmpty();
        // En production, la session reste ouverte pendant la requête HTTP (open-in-view).
        List<CampaignDTO> campaigns = transactionTemplate.execute(status -> campaignService.getAll());
        assertThat(campaigns.getFirst().publications()).isEmpty();
        assertThatThrownBy(() -> service.update(publicationId, update("Texte", PublicationStatus.DRAFT)))
                .isInstanceOf(PublicationNotFoundException.class);
        assertThatThrownBy(() -> scheduleOn(publicationId, TOMORROW))
                .isInstanceOf(PublicationNotFoundException.class);
        assertThatThrownBy(() -> service.delete(publicationId))
                .isInstanceOf(PublicationNotFoundException.class);
    }

    @Test
    void refuses_to_retry_a_failure_kept_with_an_archived_publication() {
        // Échec partiel : un canal est parti, l'occurrence est gardée comme historique.
        Long failed = occurrenceAt(NOW.minusDays(1), PublicationOccurrenceStatus.FAILED, DeliveryStatus.PUBLISHED);
        service.delete(publicationId);

        RescheduleOccurrenceRequest request = new RescheduleOccurrenceRequest();
        set(request, "date", TOMORROW);

        assertThatThrownBy(() -> occurrenceService.retry(failed, request))
                .isInstanceOf(OccurrenceNotModifiableException.class);
    }

    /** Tout ou rien : une diffusion imminente bloque la suppression, et rien n'est touché. */
    @Test
    void refuses_to_delete_while_a_delivery_is_imminent() {
        Long imminent = occurrenceAt(NOW.plusMinutes(3), PublicationOccurrenceStatus.SCHEDULED, DeliveryStatus.PENDING);
        Long later = scheduleOn(publicationId, TOMORROW);

        assertThatThrownBy(() -> service.delete(publicationId))
                .isInstanceOf(OccurrenceNotModifiableException.class);

        assertThat(publicationRepository.findById(publicationId)).isPresent();
        assertThat(publicationOccurrenceRepository.findAll()).extracting(PublicationOccurrence::getId)
                .containsExactlyInAnyOrder(imminent, later);
    }

    @Test
    void refuses_to_delete_while_a_delivery_is_in_progress() {
        occurrenceAt(NOW.minusMinutes(1), PublicationOccurrenceStatus.SCHEDULED, DeliveryStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.delete(publicationId))
                .isInstanceOf(OccurrenceNotModifiableException.class);
        assertThat(publicationRepository.findById(publicationId)).isPresent();
    }
}
