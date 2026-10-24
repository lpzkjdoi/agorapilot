package fr.maximechazard.agorapilot.back.account;

import java.util.Arrays;
import java.util.Optional;

/**
 * Droits d'un compte, notés {@code ressource:action} comme les scopes GitHub.
 * <p>
 * Aucun droit n'est implicite, pas même la lecture : un compte sans droit ne voit
 * rien. Le code ({@link #code()}) est l'autorité Spring Security exigée par la
 * chaîne de filtres, et ce que le front reçoit dans {@code GET /api/me}.
 */
public enum Permission {
    PUBLICATIONS_READ("publications:read"),
    PUBLICATIONS_WRITE("publications:write"),
    PUBLICATIONS_VERIFY("publications:verify"),
    PUBLICATIONS_DELETE("publications:delete"),
    PUBLICATIONS_PUBLISH("publications:publish"),
    OCCURRENCES_SCHEDULE("occurrences:schedule"),
    CAMPAIGNS_READ("campaigns:read"),
    CAMPAIGNS_WRITE("campaigns:write"),
    MEDIAS_READ("medias:read"),
    MEDIAS_WRITE("medias:write"),
    MEDIAS_DELETE("medias:delete"),
    CHANNELS_MANAGE("channels:manage"),
    USERS_MANAGE("users:manage");

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<Permission> fromCode(String code) {
        return Arrays.stream(values()).filter(permission -> permission.code.equals(code)).findFirst();
    }
}
