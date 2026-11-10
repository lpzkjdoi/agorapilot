package fr.maximechazard.agorapilot.back.publisher.facebook;

import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnection;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le jeton de Page qui publie : celui de la dernière connexion faite depuis la
 * page « Canal Facebook » ({@code facebook_connections}).
 */
@Component
@RequiredArgsConstructor
public class FacebookPageTokenProvider {

    private final FacebookConnectionRepository connections;

    /**
     * @throws IllegalStateException aucune Page connectée : rien ne peut être publié
     */
    @Transactional(readOnly = true)
    public String currentPageToken() {
        return connections.findTopByOrderByConnectedAtDesc()
                .map(FacebookConnection::getAccessToken)
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune Page Facebook connectée : la connecter depuis la page « Canal Facebook »."));
    }
}
