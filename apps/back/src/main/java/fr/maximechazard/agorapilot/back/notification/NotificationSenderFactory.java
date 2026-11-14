package fr.maximechazard.agorapilot.back.notification;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fournit le {@link NotificationSender} d'une technologie d'envoi.
 * <p>
 * Les senders sont découverts par injection, comme les publishers de
 * {@code PublisherRegistry} : un nouveau canal s'ajoute sans toucher ni à cette
 * classe ni au code qui notifie. Deux senders pour la même technologie font
 * échouer le démarrage plutôt que d'en choisir un au hasard.
 */
@Component
public class NotificationSenderFactory {

    private final Map<NotificationChannelType, NotificationSender> sendersByType;

    public NotificationSenderFactory(List<NotificationSender> senders) {
        this.sendersByType = senders.stream()
                .collect(Collectors.toUnmodifiableMap(NotificationSender::supports, Function.identity()));
    }

    public Optional<NotificationSender> forType(NotificationChannelType type) {
        return Optional.ofNullable(sendersByType.get(type));
    }

    public NotificationSender get(NotificationChannelType type) {
        return forType(type).orElseThrow(() -> new IllegalStateException(
                "No notification sender available for channel type " + type + "."));
    }
}
