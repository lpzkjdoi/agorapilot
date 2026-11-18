package fr.maximechazard.agorapilot.back.account;

import fr.maximechazard.agorapilot.back.account.exceptions.TooManyLoginAttemptsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite les échecs de connexion par adresse IP.
 * <p>
 * Complète le verrouillage par compte ({@link AuthService}) : celui-ci protège un
 * compte qu'on vise, mais pas l'essai d'un même mot de passe sur beaucoup de
 * pseudos. Au-delà de {@code maxFailures} échecs dans une fenêtre de
 * {@code window}, l'IP est refusée (429) jusqu'à la fin de la fenêtre, sans même
 * que le mot de passe soit vérifié. Seuls les échecs comptent : une équipe
 * derrière une même IP peut se connecter autant qu'elle veut.
 * <p>
 * En mémoire : un redémarrage remet les compteurs à zéro, ce qui est acceptable
 * pour un seul back.
 */
@Component
@Slf4j
public class LoginRateLimiter {
    /** Au-delà, les fenêtres échues sont purgées pour borner la mémoire. */
    private static final int PURGE_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final SecurityProperties.LoginRateLimit limit;
    private final Clock clock;

    public LoginRateLimiter(SecurityProperties properties, Clock clock) {
        this.limit = properties.loginRateLimit();
        this.clock = clock;
    }

    /** @throws TooManyLoginAttemptsException si l'IP a épuisé ses échecs dans la fenêtre courante */
    public void check(String ip) {
        Window window = windows.get(ip);
        Instant now = clock.instant();

        if (window != null && !window.expired(now, limit.window()) && window.failures() >= limit.maxFailures()) {
            log.warn("Connexion refusée pour l'IP {} : {} échecs depuis {}", ip, window.failures(), window.start());
            throw new TooManyLoginAttemptsException();
        }
    }

    public void recordFailure(String ip) {
        Instant now = clock.instant();

        if (windows.size() > PURGE_THRESHOLD) {
            windows.values().removeIf(w -> w.expired(now, limit.window()));
        }

        windows.compute(ip, (key, window) -> window == null || window.expired(now, limit.window())
                ? new Window(now, 1)
                : new Window(window.start(), window.failures() + 1));
    }

    /** Pour les tests, dont le contexte Spring est partagé d'un test à l'autre. */
    void clear() {
        windows.clear();
    }

    private record Window(Instant start, int failures) {
        boolean expired(Instant now, Duration length) {
            return !now.isBefore(start.plus(length));
        }
    }
}
