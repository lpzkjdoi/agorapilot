package fr.maximechazard.agorapilot.back.notification;

import fr.maximechazard.agorapilot.back.notification.summary.DailySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Set;

/**
 * Vérification des notifications sans attendre 7 h ni un échec réel.
 * <p>
 * Réservé au droit {@code channels:manage}, comme tout {@code /admin/**}, et non
 * proxifié par le nginx du front : joignable uniquement sur la boucle locale du
 * VPS. Les URL de webhook n'y apparaissent jamais.
 */
@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class NotificationAdminController {

    private final NotificationService notificationService;
    private final DailySummaryService dailySummaryService;
    private final NotificationProperties properties;
    private final Clock clock;

    public record TargetDTO(String name, NotificationChannelType type, Set<NotificationType> events, boolean configured) {
    }

    @GetMapping("/targets")
    public List<TargetDTO> targets() {
        return notificationService.targets().stream()
                .map(target -> new TargetDTO(target.name(), target.type(), target.events(), target.isConfigured()))
                .toList();
    }

    /**
     * Sans {@code target} : aux cibles qui suivent les notifications de test.
     * Avec : à cette cible seule, quels que soient les types qu'elle suit.
     */
    @PostMapping("/test")
    public List<NotificationDispatchResult> test(@RequestParam(required = false) String target) {
        Notification notification = testNotification(target);
        return target == null
                ? notificationService.notify(notification)
                : notificationService.notifyTarget(target, notification);
    }

    /** Envoie dès maintenant le programme du jour à ses destinataires habituels. */
    @PostMapping("/daily-summary")
    public List<NotificationDispatchResult> dailySummary() {
        return notificationService.notify(dailySummaryService.today());
    }

    private Notification testNotification(String target) {
        return Notification.builder(NotificationType.TEST, NotificationSeverity.SUCCESS, "Notification de test")
                .summary("Ce salon est bien relié à AgoraPilot.")
                .field(NotificationField.inline("Environnement", properties.environment()))
                .field(NotificationField.inline("Cible", target == null ? "abonnés aux tests" : target))
                .occurredAt(clock.instant())
                .build();
    }
}
