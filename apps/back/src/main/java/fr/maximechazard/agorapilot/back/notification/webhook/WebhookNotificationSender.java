package fr.maximechazard.agorapilot.back.notification.webhook;

import fr.maximechazard.agorapilot.back.notification.Notification;
import fr.maximechazard.agorapilot.back.notification.NotificationChannelType;
import fr.maximechazard.agorapilot.back.notification.NotificationProperties;
import fr.maximechazard.agorapilot.back.notification.NotificationRestClient;
import fr.maximechazard.agorapilot.back.notification.NotificationSender;
import fr.maximechazard.agorapilot.back.notification.NotificationTarget;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

/**
 * Poste la notification en JSON sur une URL quelconque (n8n, Home Assistant,
 * script maison…). Toute réponse hors 2xx est un échec.
 */
@Component
public class WebhookNotificationSender implements NotificationSender {

    private final RestClient restClient;
    private final String environment;

    public WebhookNotificationSender(NotificationRestClient restClient,
                                     NotificationProperties properties) {
        this.restClient = restClient.client();
        this.environment = properties.environment();
    }

    @Override
    public NotificationChannelType supports() {
        return NotificationChannelType.WEBHOOK;
    }

    @Override
    public void send(NotificationTarget target, Notification notification) {
        restClient.post()
                .uri(URI.create(target.url()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(WebhookPayload.of(notification, environment))
                .retrieve()
                .toBodilessEntity();
    }
}
