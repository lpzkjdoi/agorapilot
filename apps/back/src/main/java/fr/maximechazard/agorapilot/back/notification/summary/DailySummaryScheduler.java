package fr.maximechazard.agorapilot.back.notification.summary;

import fr.maximechazard.agorapilot.back.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Envoie chaque matin le programme du jour, à l'heure du fuseau du conteneur
 * ({@code TZ}), comme le contrôle quotidien du jeton Facebook.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailySummaryScheduler {

    private final DailySummaryService dailySummaryService;
    private final NotificationService notificationService;

    @Scheduled(cron = "${agorapilot.notifications.daily-summary-cron:0 0 7 * * *}")
    public void sendDailySummary() {
        try {
            notificationService.notify(dailySummaryService.today());
        } catch (RuntimeException e) {
            log.error("Daily summary could not be sent:", e);
        }
    }
}
