package fr.maximechazard.agorapilot.back.notification.webhook;

import fr.maximechazard.agorapilot.back.notification.Notification;
import fr.maximechazard.agorapilot.back.notification.NotificationEntry;
import fr.maximechazard.agorapilot.back.notification.NotificationField;

import java.util.List;

/**
 * Corps posté sur un webhook générique : la notification telle quelle, texte
 * brut, au destinataire d'en faire la mise en forme.
 * <p>
 * Les instants sont émis en ISO-8601 (UTC) sous forme de texte, pour ne pas
 * dépendre du réglage des dates du sérialiseur.
 */
public record WebhookPayload(
        String type,
        String severity,
        String environment,
        String title,
        String summary,
        String link,
        String reference,
        List<Entry> entries,
        List<NotificationField> fields,
        String occurredAt
) {

    public record Entry(String at, String label, String detail, String link) {
        static Entry of(NotificationEntry entry) {
            return new Entry(entry.at() == null ? null : entry.at().toString(),
                    entry.label(), entry.detail(), entry.link());
        }
    }

    public static WebhookPayload of(Notification notification, String environment) {
        return new WebhookPayload(
                notification.type().name(),
                notification.severity().name(),
                environment,
                notification.title(),
                notification.summary(),
                notification.link(),
                notification.reference(),
                notification.entries().stream().map(Entry::of).toList(),
                notification.fields(),
                notification.occurredAt().toString()
        );
    }
}
