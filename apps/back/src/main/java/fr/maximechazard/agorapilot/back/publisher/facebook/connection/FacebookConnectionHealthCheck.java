package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Contrôle quotidien du jeton connecté.
 * <p>
 * Le matin, avant la fenêtre de publication : un jeton invalidé dans la nuit
 * suspend le canal et s'affiche dans le bandeau des heures avant la première
 * diffusion, au lieu de se découvrir à son échec. Le résultat est gardé en base
 * ({@link FacebookConnection#getLastCheckResult()}), il survit à un redémarrage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FacebookConnectionHealthCheck {

    private final FacebookConnectionService connectionService;

    @Scheduled(cron = "${facebook.connection.check-cron:0 0 7 * * *}")
    public void run() {
        try {
            connectionService.check().ifPresent(connection ->
                    log.info("Jeton Facebook contrôlé : {}", connection.getLastCheckResult()));
        } catch (RuntimeException e) {
            log.error("Contrôle du jeton Facebook en échec", e);
        }
    }
}
