package fr.maximechazard.agorapilot.back.notification;

import java.util.Set;

/**
 * Une destination de notifications : un salon Discord, un webhook…
 *
 * @param name   nom lisible, repris dans les logs et l'API d'administration —
 *               jamais l'URL, qui porte le jeton du webhook
 * @param type   technologie d'envoi
 * @param url    adresse du webhook ; vide, la cible est ignorée (développement,
 *               tests, environnement sans salon)
 * @param events types de notification reçus
 */
public record NotificationTarget(String name, NotificationChannelType type, String url, Set<NotificationType> events) {

    public NotificationTarget {
        events = events == null ? Set.of() : Set.copyOf(events);
    }

    public boolean isConfigured() {
        return type != null && url != null && !url.isBlank();
    }

    public boolean receives(NotificationType notificationType) {
        return events.contains(notificationType);
    }
}
