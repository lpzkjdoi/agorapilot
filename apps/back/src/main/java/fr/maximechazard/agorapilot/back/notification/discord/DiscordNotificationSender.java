package fr.maximechazard.agorapilot.back.notification.discord;

import fr.maximechazard.agorapilot.back.notification.Notification;
import fr.maximechazard.agorapilot.back.notification.NotificationChannelType;
import fr.maximechazard.agorapilot.back.notification.NotificationRestClient;
import fr.maximechazard.agorapilot.back.notification.NotificationSender;
import fr.maximechazard.agorapilot.back.notification.NotificationTarget;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

/**
 * Poste une notification dans un salon Discord, via son webhook.
 * <p>
 * {@code ?wait=true} fait répondre Discord avec le message créé plutôt qu'un 204
 * vide : un refus (payload invalide, webhook supprimé) remonte alors en erreur.
 * <p>
 * Sur un 429, une seule nouvelle tentative après le délai indiqué par Discord,
 * plafonné : au-delà, la notification est perdue et journalisée, ce qui vaut
 * mieux que de bloquer le fil qui notifie.
 */
@Component
public class DiscordNotificationSender implements NotificationSender {

    static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final DiscordEmbedRenderer renderer;

    public DiscordNotificationSender(NotificationRestClient restClient,
                                     DiscordEmbedRenderer renderer) {
        this.restClient = restClient.client();
        this.renderer = renderer;
    }

    @Override
    public NotificationChannelType supports() {
        return NotificationChannelType.DISCORD;
    }

    @Override
    public void send(NotificationTarget target, Notification notification) {
        DiscordWebhookPayload payload = renderer.render(notification);
        URI uri = UriComponentsBuilder.fromUriString(target.url())
                .queryParam("wait", "true")
                .build(true)
                .toUri();

        try {
            post(uri, payload);
        } catch (HttpClientErrorException.TooManyRequests e) {
            pause(retryDelay(e));
            post(uri, payload);
        }
    }

    private void post(URI uri, DiscordWebhookPayload payload) {
        restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    /** {@code Retry-After} est en secondes, éventuellement décimales. */
    static Duration retryDelay(HttpClientErrorException e) {
        String header = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");

        if (header == null) {
            return Duration.ofSeconds(1);
        }

        try {
            Duration delay = Duration.ofMillis(Math.round(Double.parseDouble(header) * 1000));
            return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
        } catch (NumberFormatException ignored) {
            return Duration.ofSeconds(1);
        }
    }

    private static void pause(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Discord rate limit.", e);
        }
    }
}
