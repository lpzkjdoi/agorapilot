package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Connexion de la Page Facebook depuis l'interface.
 * <p>
 * À part de {@code FacebookProperties} pour laisser intact le mécanisme
 * historique, que {@link FacebookTokenSource#LEGACY} doit pouvoir rétablir tel
 * quel.
 *
 * @param tokenSource   d'où vient le jeton qui publie
 * @param dialogBaseUrl hôte de la boîte de dialogue OAuth, distinct de l'API Graph
 * @param stateTtl      durée de validité d'une demande de connexion
 */
@ConfigurationProperties(prefix = "facebook.connection")
public record FacebookConnectionProperties(
        @DefaultValue("CONNECTION") FacebookTokenSource tokenSource,
        @DefaultValue("https://www.facebook.com") String dialogBaseUrl,
        @DefaultValue("PT10M") Duration stateTtl
) {

    public boolean enabled() {
        return tokenSource == FacebookTokenSource.CONNECTION;
    }
}
