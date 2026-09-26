package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publisher.facebook.FacebookClient;
import fr.maximechazard.agorapilot.back.publisher.facebook.FacebookPostResponse;
import fr.maximechazard.agorapilot.back.publisher.facebook.FacebookTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La diffusion planifiée contre une vraie base, avec le vrai publisher Facebook —
 * seul l'appel HTTP à l'API Graph est bouchonné.
 * <p>
 * Ce que les tests unitaires ne peuvent pas voir : que la prise en charge est
 * réellement <em>validée</em> avant l'appel distant (lue depuis une autre
 * transaction), et que le chargement paresseux de la publication fonctionne
 * maintenant que la diffusion n'a plus de transaction englobante.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_scheduled_delivery;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        // L'ordonnanceur ne doit pas balayer en parallèle du test.
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "agorapilot.scheduling.max-lateness=PT1H",
        "spring.jpa.show-sql=false"
})
class ScheduledDeliveryPersistenceTest {

    @Autowired
    private PublicationDeliveryService publicationDeliveryService;
    @Autowired
    private PublicationRepository publicationRepository;
    @Autowired
    private PublicationOccurrenceRepository publicationOccurrenceRepository;
    @Autowired
    private PublicationDeliveryRepository publicationDeliveryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private FacebookClient facebookClient;
    @MockitoBean
    private FacebookTokenService facebookTokenService;

    @BeforeEach
    void setUp() {
        publicationDeliveryRepository.deleteAll();
        publicationOccurrenceRepository.deleteAll();
        publicationRepository.deleteAll();
        when(facebookTokenService.getCurrentPageToken()).thenReturn("page-token");
    }

    private Long occurrence(LocalDateTime scheduledAt, DeliveryStatus deliveryStatus) {
        Publication publication = publicationRepository.save(
                new Publication("Marché de Noël samedi", PublicationStatus.VERIFIED));

        PublicationOccurrence occurrence = new PublicationOccurrence();
        occurrence.setPublication(publication);
        occurrence.setScheduledAt(scheduledAt);
        PublicationDelivery delivery = new PublicationDelivery();
        delivery.setChannel(DeliveryChannel.FACEBOOK);
        delivery.setStatus(deliveryStatus);
        occurrence.addDelivery(delivery);

        return publicationOccurrenceRepository.save(occurrence).getId();
    }

    private PublicationDelivery onlyDelivery() {
        assertThat(publicationDeliveryRepository.findAll()).hasSize(1);
        return publicationDeliveryRepository.findAll().getFirst();
    }

    private PublicationOccurrenceStatus occurrenceStatus(Long occurrenceId) {
        return publicationOccurrenceRepository.findById(occurrenceId).orElseThrow().getStatus();
    }

    @Test
    void publishes_a_due_delivery_and_resolves_the_occurrence() {
        Long occurrenceId = occurrence(LocalDateTime.now().minusMinutes(2), DeliveryStatus.PENDING);
        when(facebookClient.publish(anyString(), any(), anyString())).thenReturn(new FacebookPostResponse("123_456"));

        publicationDeliveryService.publishScheduledOccurrence(occurrenceId);

        verify(facebookClient).publish(eq("Marché de Noël samedi"), any(), any());
        PublicationDelivery delivery = onlyDelivery();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PUBLISHED);
        assertThat(delivery.getExternalId()).isEqualTo("123_456");
        assertThat(occurrenceStatus(occurrenceId)).isEqualTo(PublicationOccurrenceStatus.PUBLISHED);
    }

    /**
     * Lue depuis une transaction indépendante au moment où Facebook est appelé,
     * la livraison doit déjà être IN_PROGRESS : c'est ce qu'un redémarrage
     * trouverait si le back tombait à cet instant.
     */
    @Test
    void the_claim_is_committed_when_facebook_is_called() {
        Long occurrenceId = occurrence(LocalDateTime.now().minusMinutes(2), DeliveryStatus.PENDING);
        AtomicReference<DeliveryStatus> committedDuringCall = new AtomicReference<>();
        TransactionTemplate independent = new TransactionTemplate(transactionManager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        when(facebookClient.publish(anyString(), any(), anyString())).thenAnswer(invocation -> {
            committedDuringCall.set(independent.execute(status -> onlyDelivery().getStatus()));
            return new FacebookPostResponse("123_456");
        });

        publicationDeliveryService.publishScheduledOccurrence(occurrenceId);

        assertThat(committedDuringCall.get()).isEqualTo(DeliveryStatus.IN_PROGRESS);
    }

    @Test
    void traces_a_facebook_refusal_as_failed() {
        Long occurrenceId = occurrence(LocalDateTime.now().minusMinutes(2), DeliveryStatus.PENDING);
        when(facebookClient.publish(anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException("Graph API unavailable"));

        publicationDeliveryService.publishScheduledOccurrence(occurrenceId);

        PublicationDelivery delivery = onlyDelivery();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getErrorMessage()).isEqualTo("Graph API unavailable");
        assertThat(occurrenceStatus(occurrenceId)).isEqualTo(PublicationOccurrenceStatus.FAILED);
    }

    /** Ce que trouverait le back au redémarrage après un arrêt pendant l'appel. */
    @Test
    void settles_an_interrupted_delivery_without_calling_facebook_again() {
        Long occurrenceId = occurrence(LocalDateTime.now().minusMinutes(2), DeliveryStatus.IN_PROGRESS);

        publicationDeliveryService.publishScheduledOccurrence(occurrenceId);

        verify(facebookClient, never()).publish(any(), any(), any());
        assertThat(onlyDelivery().getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(occurrenceStatus(occurrenceId)).isEqualTo(PublicationOccurrenceStatus.FAILED);
    }

    @Test
    void does_not_publish_past_the_maximum_lateness() {
        Long occurrenceId = occurrence(LocalDateTime.now().minusHours(3), DeliveryStatus.PENDING);

        publicationDeliveryService.publishScheduledOccurrence(occurrenceId);

        verify(facebookClient, never()).publish(any(), any(), any());
        assertThat(onlyDelivery().getErrorMessage()).contains("60 minutes late");
        assertThat(occurrenceStatus(occurrenceId)).isEqualTo(PublicationOccurrenceStatus.FAILED);
    }
}
