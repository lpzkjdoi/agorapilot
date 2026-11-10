package fr.maximechazard.agorapilot.back.publisher.facebook.connection.dtos;

import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnection;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * État du canal Facebook pour la page « Canal Facebook ». Jamais le jeton
 * lui-même.
 *
 * @param connection {@code null} tant qu'aucune Page n'a été connectée : rien ne peut alors être publié
 */
public record FacebookConnectionStatusDTO(Connection connection) {

    public static FacebookConnectionStatusDTO of(FacebookConnection connection) {
        return new FacebookConnectionStatusDTO(connection == null ? null : Connection.of(connection));
    }

    public record Connection(String pageId, String pageName, List<String> scopes, String connectedBy,
                             LocalDateTime connectedAt, LocalDateTime expiresAt, LocalDateTime dataAccessExpiresAt,
                             LocalDateTime lastCheckedAt, FacebookConnection.CheckResult lastCheckResult,
                             String lastCheckMessage) {

        static Connection of(FacebookConnection connection) {
            List<String> scopes = connection.getScopes().isBlank()
                    ? List.of()
                    : Arrays.asList(connection.getScopes().split(","));
            return new Connection(connection.getPageId(), connection.getPageName(), scopes,
                    connection.getConnectedBy(), connection.getConnectedAt(), connection.getExpiresAt(),
                    connection.getDataAccessExpiresAt(), connection.getLastCheckedAt(),
                    connection.getLastCheckResult(), connection.getLastCheckMessage());
        }
    }
}
