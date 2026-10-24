package fr.maximechazard.agorapilot.back.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param initialAdmin     premier compte, créé au démarrage si aucun n'existe
 * @param maxFailedAttempts échecs de connexion consécutifs avant verrouillage
 * @param lockDuration     durée du verrouillage
 * @param sessionMaxAge    durée de vie maximale d'une session, activité ou non
 */
@ConfigurationProperties(prefix = "agorapilot.security")
public record SecurityProperties(
        InitialAdmin initialAdmin,
        int maxFailedAttempts,
        Duration lockDuration,
        Duration sessionMaxAge
) {
    /** Sans pseudo ni mot de passe, aucun compte n'est créé. */
    public record InitialAdmin(String username, String password) {
        public boolean isConfigured() {
            return username != null && !username.isBlank() && password != null && !password.isBlank();
        }
    }
}
