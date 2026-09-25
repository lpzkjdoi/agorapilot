package fr.maximechazard.agorapilot.back.publication;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Réglages de la diffusion planifiée.
 *
 * @param occurrencesDelay période de balayage des occurrences dues
 * @param maxLateness      retard au-delà duquel une livraison n'est plus
 *                         diffusée automatiquement (reprise après une panne :
 *                         une annonce d'événement déjà passé ne doit pas partir)
 */
@ConfigurationProperties(prefix = "agorapilot.scheduling")
public record SchedulingProperties(
        Duration occurrencesDelay,
        Duration maxLateness
) {
}
