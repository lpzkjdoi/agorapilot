package fr.maximechazard.agorapilot.back.account;

import org.springframework.security.core.Authentication;

/** Lecture des droits d'une requête, quand une règle métier en dépend. */
public final class Permissions {

    private Permissions() {
    }

    public static boolean held(Authentication authentication, Permission permission) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.code().equals(authority.getAuthority()));
    }
}
