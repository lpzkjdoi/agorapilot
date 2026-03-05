package fr.maximechazard.agorapilot.back.publishing.channel.facebook;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "facebook")
public record FacebookProperties(
        String clientId,
        String clientSecret,
        String pageId,
        String accessToken,
        String apiBaseUrl,
        String apiVersion
) {
}