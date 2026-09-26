package fr.maximechazard.agorapilot.back.publisher.facebook;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de l'intégration Facebook.
 * <p>
 * Volontairement sans token de page : celui-ci n'est pas une donnée de
 * configuration mais un état, obtenu via {@code POST /admin/facebook/bootstrap}
 * puis conservé en base et renouvelé par {@link FacebookTokenScheduler}.
 */
@ConfigurationProperties(prefix = "facebook")
public record FacebookProperties(
        String clientId,
        String clientSecret,
        String pageId,
        String apiBaseUrl,
        String apiVersion
) {
}