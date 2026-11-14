package fr.maximechazard.agorapilot.back.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Point d'entrée unique pour notifier : route une notification vers les cibles
 * qui en suivent le type, et confie chaque envoi au sender de sa technologie.
 * <p>
 * Une notification n'est jamais critique : l'échec d'une cible est journalisé
 * et n'empêche ni l'envoi aux autres, ni le traitement métier qui notifiait.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final String MASKED_URL = "[webhook]";

    private final NotificationProperties properties;
    private final NotificationSenderFactory senderFactory;

    /** Envoie la notification à toutes les cibles configurées qui en suivent le type. */
    public List<NotificationDispatchResult> notify(Notification notification) {
        if (!properties.enabled()) {
            return List.of();
        }

        List<NotificationTarget> recipients = properties.targets().stream()
                .filter(NotificationTarget::isConfigured)
                .filter(target -> target.receives(notification.type()))
                .toList();

        if (recipients.isEmpty()) {
            log.debug("No notification target for {}.", notification.type());
        }

        return dispatch(notification, recipients);
    }

    /**
     * Envoie la notification à une cible désignée, quels que soient les types
     * qu'elle suit — pour vérifier qu'un salon est bien relié.
     *
     * @throws NotificationTargetNotFoundException aucune cible configurée sous ce nom
     */
    public List<NotificationDispatchResult> notifyTarget(String targetName, Notification notification) {
        NotificationTarget target = properties.targets().stream()
                .filter(NotificationTarget::isConfigured)
                .filter(candidate -> candidate.name().equals(targetName))
                .findFirst()
                .orElseThrow(() -> new NotificationTargetNotFoundException(targetName));

        return dispatch(notification, List.of(target));
    }

    /** Les cibles déclarées, configurées ou non. */
    public List<NotificationTarget> targets() {
        return properties.targets();
    }

    private List<NotificationDispatchResult> dispatch(Notification notification, List<NotificationTarget> recipients) {
        List<NotificationDispatchResult> results = new ArrayList<>();

        for (NotificationTarget target : recipients) {
            try {
                senderFactory.get(target.type()).send(target, notification);
                results.add(NotificationDispatchResult.delivered(target.name()));
            } catch (RuntimeException e) {
                String reason = mask(e.getMessage(), target);
                log.warn("Notification {} to target '{}' failed: {}", notification.type(), target.name(), reason);
                results.add(NotificationDispatchResult.failed(target.name(), reason));
            }
        }

        return results;
    }

    /**
     * Les exceptions HTTP citent l'URL appelée, or celle d'un webhook vaut mot de
     * passe : elle ne doit finir ni dans les logs ni dans une réponse d'API.
     */
    private static String mask(String message, NotificationTarget target) {
        if (message == null) {
            return "Unknown error";
        }

        return message.replace(target.url(), MASKED_URL);
    }
}
