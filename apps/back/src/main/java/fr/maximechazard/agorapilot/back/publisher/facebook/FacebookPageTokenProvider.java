package fr.maximechazard.agorapilot.back.publisher.facebook;

import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnection;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnectionProperties;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnectionRepository;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookTokenSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Le jeton de Page qui publie, selon {@link FacebookTokenSource}.
 * <p>
 * Seul point où les deux mécanismes se rejoignent : la connexion depuis
 * l'interface ({@code facebook_connections}) et le bootstrap historique
 * ({@code facebook_tokens}, via {@link FacebookTokenService}, inchangé).
 */
@Component
@RequiredArgsConstructor
public class FacebookPageTokenProvider {

    private final FacebookConnectionProperties props;
    private final FacebookConnectionRepository connections;
    private final FacebookTokenService legacyTokens;

    /** Le mécanisme dont vient le jeton qui publie en ce moment. */
    public enum Origin {
        CONNECTION,
        LEGACY,
        /** Aucun jeton : ni connexion, ni bootstrap. */
        NONE
    }

    /**
     * @throws IllegalStateException aucun jeton, ni connecté ni historique
     */
    @Transactional(readOnly = true)
    public String currentPageToken() {
        return connected()
                .map(FacebookConnection::getAccessToken)
                .orElseGet(legacyTokens::getCurrentPageToken);
    }

    @Transactional(readOnly = true)
    public Origin origin() {
        if (connected().isPresent()) {
            return Origin.CONNECTION;
        }
        return legacyTokens.getTokenStatus(FacebookTokenService.PAGE_TOKEN_TYPE).isPresent()
                ? Origin.LEGACY
                : Origin.NONE;
    }

    /** Vide en mode historique : la connexion, même faite, est alors ignorée. */
    private Optional<FacebookConnection> connected() {
        return props.enabled() ? connections.findTopByOrderByConnectedAtDesc() : Optional.empty();
    }
}
