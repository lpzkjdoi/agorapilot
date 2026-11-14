package fr.maximechazard.agorapilot.back.notification;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.DeliveryStatus;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.PublicationDelivery;
import fr.maximechazard.agorapilot.back.publication.PublicationDeliveryService;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * De l'échec d'une diffusion planifiée jusqu'au service de notification, avec la
 * vraie base, le vrai ordonnancement des transactions et le vrai fil asynchrone :
 * ce que les tests unitaires de chaque pièce ne prouvent pas.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_notification_flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "agorapilot.scheduling.max-lateness=PT1H",
        "spring.jpa.show-sql=false"
})
class NotificationFlowTest {

    @Autowired
    private PublicationDeliveryService publicationDeliveryService;
    @Autowired
    private PublicationRepository publicationRepository;
    @Autowired
    private PublicationOccurrenceRepository publicationOccurrenceRepository;
    @Autowired
    private PublicationDeliveryRepository publicationDeliveryRepository;
    @Autowired
    private NotificationProperties notificationProperties;

    @MockitoBean
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        publicationDeliveryRepository.deleteAll();
        publicationOccurrenceRepository.deleteAll();
        publicationRepository.deleteAll();
    }

    /** Intramuros n'a pas encore de publisher : la livraison échoue sans appel distant. */
    @Test
    void a_failed_scheduled_delivery_is_notified_after_commit() {
        Publication publication = publicationRepository.save(
                new Publication("Marché de Noël samedi", PublicationStatus.VERIFIED));
        PublicationOccurrence occurrence = new PublicationOccurrence();
        occurrence.setPublication(publication);
        occurrence.setScheduledAt(LocalDateTime.now().minusMinutes(2));
        PublicationDelivery delivery = new PublicationDelivery();
        delivery.setChannel(DeliveryChannel.INTRAMUROS);
        delivery.setStatus(DeliveryStatus.PENDING);
        occurrence.addDelivery(delivery);
        Long occurrenceId = publicationOccurrenceRepository.save(occurrence).getId();

        publicationDeliveryService.publishScheduledOccurrence(occurrenceId);

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, timeout(5_000)).notify(sent.capture());
        Notification notification = sent.getValue();
        assertThat(notification.type()).isEqualTo(NotificationType.DELIVERY_FAILED);
        assertThat(notification.reference()).isEqualTo("occurrence #" + occurrenceId);
        assertThat(notification.entries().getFirst().label()).isEqualTo("Marché de Noël samedi");
        assertThat(notification.entries().getFirst().detail()).isEqualTo("Intramuros");
        assertThat(notification.fields().getFirst().value()).contains("No publisher available");
    }

    /** Les trois salons sont déclarés par application.yaml, leurs URL venant de l'environnement. */
    @Test
    void binds_the_declared_targets() {
        assertThat(notificationProperties.targets())
                .extracting(NotificationTarget::name)
                .containsExactly("programme-du-jour", "alertes", "essais-notifications");
        assertThat(notificationProperties.targets())
                .allSatisfy(target -> assertThat(target.type()).isEqualTo(NotificationChannelType.DISCORD));
        assertThat(notificationProperties.targets().get(1).events()).containsExactly(NotificationType.DELIVERY_FAILED);
        assertThat(notificationProperties.environment()).isNotBlank();
    }
}
