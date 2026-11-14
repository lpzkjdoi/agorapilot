package fr.maximechazard.agorapilot.back.notification.failure;

import fr.maximechazard.agorapilot.back.notification.Notification;
import fr.maximechazard.agorapilot.back.notification.NotificationEntry;
import fr.maximechazard.agorapilot.back.notification.NotificationField;
import fr.maximechazard.agorapilot.back.notification.NotificationLinks;
import fr.maximechazard.agorapilot.back.notification.NotificationService;
import fr.maximechazard.agorapilot.back.notification.NotificationSeverity;
import fr.maximechazard.agorapilot.back.notification.NotificationTexts;
import fr.maximechazard.agorapilot.back.notification.NotificationType;
import fr.maximechazard.agorapilot.back.publication.events.OccurrenceFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Prévient quand une diffusion échoue, qu'elle soit planifiée ou lancée à la
 * main (« publier maintenant »).
 * <p>
 * Après validation seulement, pour ne notifier que ce que la base a retenu ; et
 * sur un autre fil, pour qu'un Discord lent ne retienne ni l'ordonnanceur ni la
 * réponse HTTP de « publier maintenant ».
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryFailureNotifier {

    private final NotificationService notificationService;
    private final NotificationLinks links;
    private final Clock clock;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onOccurrenceFailed(OccurrenceFailedEvent event) {
        try {
            notificationService.notify(toNotification(event));
        } catch (RuntimeException e) {
            log.warn("Could not notify the failure of occurrence {}: {}", event.occurrenceId(), e.getMessage());
        }
    }

    Notification toNotification(OccurrenceFailedEvent event) {
        String channels = event.failures().stream()
                .map(failure -> NotificationTexts.channelLabel(failure.channel()))
                .collect(Collectors.joining(", "));

        String detail = event.campaignName() == null
                ? channels
                : channels + " · Campagne " + event.campaignName();

        Instant scheduledAt = event.scheduledAt() == null
                ? null
                : event.scheduledAt().atZone(clock.getZone()).toInstant();

        Notification.Builder notification = Notification
                .builder(NotificationType.DELIVERY_FAILED, NotificationSeverity.ERROR, "Échec de diffusion")
                .summary("Cette publication n'a pas pu être diffusée.")
                .link(links.calendar())
                .reference("occurrence #" + event.occurrenceId())
                .entry(new NotificationEntry(scheduledAt, NotificationTexts.excerpt(event.publicationContent()),
                        detail, links.calendar()))
                .occurredAt(clock.instant());

        for (OccurrenceFailedEvent.FailedDelivery failure : event.failures()) {
            String name = event.failures().size() > 1
                    ? "Motif · " + NotificationTexts.channelLabel(failure.channel())
                    : "Motif";
            notification.field(NotificationField.code(name, failure.errorMessage()));
        }

        return notification.build();
    }
}
