package fr.maximechazard.agorapilot.back.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Client HTTP propre aux notifications, distinct de celui de Facebook : délais
 * courts, puisqu'un salon Discord lent ne doit retenir personne.
 * <p>
 * {@code @EnableAsync} permet aux écouteurs d'événements métier de notifier
 * hors du fil de l'ordonnanceur.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    @Bean
    public NotificationRestClient notificationRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        return new NotificationRestClient(RestClient.builder()
                .requestFactory(factory)
                .build());
    }
}
