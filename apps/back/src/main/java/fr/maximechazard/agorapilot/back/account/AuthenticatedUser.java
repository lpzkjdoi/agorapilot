package fr.maximechazard.agorapilot.back.account;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Ce que la session retient d'une connexion : qui, et depuis quand. Rien sur les
 * droits ni le statut, relus en base à chaque requête (cf. {@link AccountRefreshFilter}).
 *
 * @param authenticatedAt heure de la connexion, comparée à la date de dernier
 *                        changement du mot de passe et à la durée maximale de session
 */
public record AuthenticatedUser(Long id, String username, LocalDateTime authenticatedAt) implements Serializable {
}
