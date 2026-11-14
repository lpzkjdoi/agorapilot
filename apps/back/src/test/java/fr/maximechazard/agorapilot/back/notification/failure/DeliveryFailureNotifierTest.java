package fr.maximechazard.agorapilot.back.notification.failure;

import fr.maximechazard.agorapilot.back.notification.Notification;
import fr.maximechazard.agorapilot.back.notification.NotificationEntry;
import fr.maximechazard.agorapilot.back.notification.NotificationField;
import fr.maximechazard.agorapilot.back.notification.NotificationLinks;
import fr.maximechazard.agorapilot.back.notification.NotificationService;
import fr.maximechazard.agorapilot.back.notification.NotificationSeverity;
import fr.maximechazard.agorapilot.back.notification.NotificationType;
import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.events.OccurrenceFailedEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryFailureNotifierTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T13:00:12Z"), PARIS);

    private final NotificationService notificationService = mock(NotificationService.class);
    private final DeliveryFailureNotifier notifier = new DeliveryFailureNotifier(
            notificationService, new NotificationLinks("https://agorapilot.test/"), CLOCK);

    private static OccurrenceFailedEvent event(List<OccurrenceFailedEvent.FailedDelivery> failures) {
        return new OccurrenceFailedEvent(318L, 57L, "Collecte de jouets\nDéposez vos jouets en mairie",
                "Noël solidaire", LocalDateTime.of(2026, 11, 15, 14, 0), failures);
    }

    @Test
    void describes_the_failed_occurrence_and_its_reason() {
        Notification notification = notifier.toNotification(event(List.of(
                new OccurrenceFailedEvent.FailedDelivery(DeliveryChannel.FACEBOOK, "(#190) session has expired"))));

        assertThat(notification.type()).isEqualTo(NotificationType.DELIVERY_FAILED);
        assertThat(notification.severity()).isEqualTo(NotificationSeverity.ERROR);
        assertThat(notification.title()).isEqualTo("Échec de diffusion");
        assertThat(notification.reference()).isEqualTo("occurrence #318");
        assertThat(notification.link()).isEqualTo("https://agorapilot.test/calendrier");
        assertThat(notification.occurredAt()).isEqualTo(CLOCK.instant());
        // 14h à Paris, en heure d'hiver.
        assertThat(notification.entries()).containsExactly(new NotificationEntry(
                Instant.parse("2026-11-15T13:00:00Z"), "Collecte de jouets", "Facebook · Campagne Noël solidaire",
                "https://agorapilot.test/calendrier"));
        assertThat(notification.fields()).containsExactly(NotificationField.code("Motif", "(#190) session has expired"));
    }

    @Test
    void names_the_channel_of_each_reason_when_several_failed() {
        Notification notification = notifier.toNotification(new OccurrenceFailedEvent(1L, 2L, "Loto", null,
                LocalDateTime.of(2026, 11, 15, 14, 0), List.of(
                new OccurrenceFailedEvent.FailedDelivery(DeliveryChannel.FACEBOOK, "refusé"),
                new OccurrenceFailedEvent.FailedDelivery(DeliveryChannel.INTRAMUROS, "indisponible"))));

        assertThat(notification.entries().getFirst().detail()).isEqualTo("Facebook, Intramuros");
        assertThat(notification.fields()).extracting(NotificationField::name)
                .containsExactly("Motif · Facebook", "Motif · Intramuros");
    }

    @Test
    void sends_the_notification_through_the_service() {
        notifier.onOccurrenceFailed(event(List.of()));

        verify(notificationService).notify(any(Notification.class));
    }

    /** Une notification ratée ne doit rien casser chez l'émetteur de l'événement. */
    @Test
    void never_lets_a_notification_failure_escape() {
        when(notificationService.notify(any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> notifier.onOccurrenceFailed(event(List.of()))).doesNotThrowAnyException();
    }
}
