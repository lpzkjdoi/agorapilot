package fr.maximechazard.agorapilot.back.notification.summary;

import fr.maximechazard.agorapilot.back.notification.Notification;
import fr.maximechazard.agorapilot.back.notification.NotificationService;
import fr.maximechazard.agorapilot.back.notification.NotificationType;
import org.junit.jupiter.api.Test;

import static fr.maximechazard.agorapilot.back.notification.NotificationFixtures.notification;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailySummarySchedulerTest {

    private final DailySummaryService summaryService = mock(DailySummaryService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final DailySummaryScheduler scheduler = new DailySummaryScheduler(summaryService, notificationService);

    @Test
    void sends_the_summary_of_the_day() {
        Notification summary = notification(NotificationType.DAILY_SUMMARY);
        when(summaryService.today()).thenReturn(summary);

        scheduler.sendDailySummary();

        verify(notificationService).notify(summary);
    }

    /** Une base injoignable ne doit pas faire planter la tâche planifiée. */
    @Test
    void survives_a_failure_while_building_the_summary() {
        when(summaryService.today()).thenThrow(new IllegalStateException("base injoignable"));

        assertThatCode(scheduler::sendDailySummary).doesNotThrowAnyException();
    }
}
