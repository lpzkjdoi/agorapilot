package fr.maximechazard.agorapilot.back.notification.webhook;

import fr.maximechazard.agorapilot.back.notification.Notification;
import fr.maximechazard.agorapilot.back.notification.NotificationChannelType;
import fr.maximechazard.agorapilot.back.notification.NotificationEntry;
import fr.maximechazard.agorapilot.back.notification.NotificationField;
import fr.maximechazard.agorapilot.back.notification.NotificationProperties;
import fr.maximechazard.agorapilot.back.notification.NotificationRestClient;
import fr.maximechazard.agorapilot.back.notification.NotificationSeverity;
import fr.maximechazard.agorapilot.back.notification.NotificationTarget;
import fr.maximechazard.agorapilot.back.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static fr.maximechazard.agorapilot.back.notification.NotificationFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WebhookNotificationSenderTest {

    private static final NotificationTarget TARGET = new NotificationTarget(
            "n8n", NotificationChannelType.WEBHOOK, "https://n8n.test/hook", Set.of(NotificationType.DAILY_SUMMARY));

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final WebhookNotificationSender sender = new WebhookNotificationSender(
            new NotificationRestClient(builder.build()), new NotificationProperties(true, "preprod", List.of()));

    @Test
    void posts_the_raw_notification_as_json() {
        server.expect(once(), requestTo("https://n8n.test/hook"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().json("""
                      {
                        "type": "DAILY_SUMMARY",
                        "severity": "INFO",
                        "environment": "preprod",
                        "title": "Programme du dimanche 15 novembre",
                        "summary": "1 diffusion prévue aujourd'hui.",
                        "entries": [{ "at": "2026-11-15T09:30:00Z", "label": "Marché de Noël", "detail": "Facebook" }],
                        "fields": [{ "name": "Prévues", "value": "1", "inline": true, "code": false }],
                        "occurredAt": "2026-11-15T06:00:00Z"
                      }"""))
              .andRespond(withSuccess());

        sender.send(TARGET, Notification
                .builder(NotificationType.DAILY_SUMMARY, NotificationSeverity.INFO, "Programme du dimanche 15 novembre")
                .summary("1 diffusion prévue aujourd'hui.")
                .entry(new NotificationEntry(Instant.parse("2026-11-15T09:30:00Z"), "Marché de Noël", "Facebook", null))
                .field(NotificationField.inline("Prévues", "1"))
                .occurredAt(NOW)
                .build());

        server.verify();
    }

    @Test
    void fails_on_a_non_2xx_answer() {
        server.expect(once(), requestTo("https://n8n.test/hook")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> sender.send(TARGET, Notification
                .builder(NotificationType.TEST, NotificationSeverity.SUCCESS, "Test").occurredAt(NOW).build()))
                .isInstanceOf(HttpServerErrorException.class);
    }
}
