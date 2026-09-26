package fr.maximechazard.agorapilot.back.publication;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Réglages de la diffusion planifiée.
 *
 * @param occurrencesDelay période de balayage des occurrences dues
 * @param maxLateness      retard au-delà duquel une livraison n'est plus
 *                         diffusée automatiquement (reprise après une panne :
 *                         une annonce d'événement déjà passé ne doit pas partir)
 * @param windowStart      début de la fenêtre de publication Facebook : les
 *                         diffusions programmées « pour un jour » y sont réparties
 * @param windowEnd        fin de cette fenêtre, le même jour
 */
@ConfigurationProperties(prefix = "agorapilot.scheduling")
public record SchedulingProperties(
        Duration occurrencesDelay,
        Duration maxLateness,
        LocalTime windowStart,
        LocalTime windowEnd
) {
    public SchedulingProperties {
        // Une fenêtre à cheval sur minuit n'est pas prise en charge : mieux vaut
        // un démarrage en échec qu'une répartition silencieusement absurde.
        if (windowStart != null && windowEnd != null && !windowStart.isBefore(windowEnd)) {
            throw new IllegalArgumentException(
                    "agorapilot.scheduling.window-start (" + windowStart
                            + ") must be before window-end (" + windowEnd + ").");
        }
    }
}
