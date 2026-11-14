package fr.maximechazard.agorapilot.back.notification.discord;

import fr.maximechazard.agorapilot.back.notification.NotificationProperties;
import fr.maximechazard.agorapilot.back.notification.NotificationRestClient;
import fr.maximechazard.agorapilot.back.notification.NotificationTarget;
import fr.maximechazard.agorapilot.back.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static fr.maximechazard.agorapilot.back.notification.NotificationFixtures.discord;
import static fr.maximechazard.agorapilot.back.notification.NotificationFixtures.notification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DiscordNotificationSenderTest {

    private static final String WEBHOOK = "https://discord.test/api/webhooks/1571/token-abc";
    private static final NotificationTarget TARGET = discord("alertes", WEBHOOK, NotificationType.TEST);

    private MockRestServiceServer server;
    private DiscordNotificationSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new DiscordNotificationSender(new NotificationRestClient(builder.build()),
                new DiscordEmbedRenderer(new NotificationProperties(true, "preprod", List.of())));
    }

    /** Le corps exact posté à Discord, sans champ {@code null} qu'il pourrait refuser. */
    @Test
    void posts_the_embed_to_the_webhook_and_waits_for_the_created_message() {
        server.expect(once(), requestTo(WEBHOOK + "?wait=true"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
              .andExpect(content().json("""
                      {
                        "username": "AgoraPilot · preprod",
                        "embeds": [{
                          "title": "🧪 Titre",
                          "color": 3900150,
                          "footer": { "text": "AgoraPilot · preprod · test" },
                          "timestamp": "2026-11-15T06:00:00Z"
                        }],
                        "allowed_mentions": { "parse": [] }
                      }""", org.springframework.test.json.JsonCompareMode.STRICT))
              .andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));

        sender.send(TARGET, notification(NotificationType.TEST));

        server.verify();
    }

    @Test
    void retries_once_after_a_rate_limit() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "0");
        server.expect(once(), requestTo(WEBHOOK + "?wait=true"))
              .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));
        server.expect(once(), requestTo(WEBHOOK + "?wait=true"))
              .andRespond(withSuccess());

        sender.send(TARGET, notification(NotificationType.TEST));

        server.verify();
    }

    /** Webhook supprimé, payload refusé : l'échec remonte au service, qui le trace. */
    @Test
    void fails_when_discord_refuses_the_message() {
        server.expect(once(), requestTo(WEBHOOK + "?wait=true"))
              .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> sender.send(TARGET, notification(NotificationType.TEST)))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void reads_and_caps_the_retry_delay() {
        assertThat(DiscordNotificationSender.retryDelay(rateLimited("0.25"))).isEqualTo(Duration.ofMillis(250));
        assertThat(DiscordNotificationSender.retryDelay(rateLimited("60"))).isEqualTo(DiscordNotificationSender.MAX_RETRY_DELAY);
        assertThat(DiscordNotificationSender.retryDelay(rateLimited("bientôt"))).isEqualTo(Duration.ofSeconds(1));
        assertThat(DiscordNotificationSender.retryDelay(rateLimited(null))).isEqualTo(Duration.ofSeconds(1));
    }

    private static HttpClientErrorException rateLimited(String retryAfter) {
        HttpHeaders headers = new HttpHeaders();
        if (retryAfter != null) {
            headers.add("Retry-After", retryAfter);
        }
        return HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, new byte[0], null);
    }
}
