package fr.maximechazard.agorapilot.back.account;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Le pseudo du compte à l'origine de la requête en cours. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * Vide hors requête authentifiée : l'ordonnanceur, un démarrage, une requête
     * anonyme. Ce qu'ils écrivent n'a pas d'auteur.
     */
    public static Optional<String> username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user.username());
        }

        return Optional.ofNullable(authentication.getName());
    }
}
