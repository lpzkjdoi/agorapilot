package fr.maximechazard.agorapilot.back.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Réglages des notifications ({@code agorapilot.notifications}).
 *
 * @param enabled     interrupteur général
 * @param environment nom de l'environnement, affiché sur chaque notification
 *                    pour distinguer la préproduction d'une future production
 * @param targets     destinations ; les URL viennent de l'environnement, le dépôt
 *                    étant public
 */
@ConfigurationProperties(prefix = "agorapilot.notifications")
public record NotificationProperties(boolean enabled, String environment, List<NotificationTarget> targets) {

    public NotificationProperties {
        targets = targets == null ? List.of() : List.copyOf(targets);
    }
}
