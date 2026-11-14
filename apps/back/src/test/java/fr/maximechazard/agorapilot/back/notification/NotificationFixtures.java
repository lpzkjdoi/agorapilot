package fr.maximechazard.agorapilot.back.notification;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Notifications et cibles prêtes à l'emploi pour les tests du module. */
public final class NotificationFixtures {

    public static final Instant NOW = Instant.parse("2026-11-15T06:00:00Z");

    private NotificationFixtures() {
    }

    public static Notification notification(NotificationType type) {
        return Notification.builder(type, NotificationSeverity.INFO, "Titre").occurredAt(NOW).build();
    }

    public static NotificationTarget discord(String name, String url, NotificationType... events) {
        return new NotificationTarget(name, NotificationChannelType.DISCORD, url, Set.of(events));
    }

    public static NotificationProperties properties(NotificationTarget... targets) {
        return new NotificationProperties(true, "preprod", List.of(targets));
    }
}
