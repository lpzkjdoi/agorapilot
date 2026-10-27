package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import fr.maximechazard.agorapilot.back.publisher.facebook.FacebookProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Les appels à l'API Graph du flux de connexion et du contrôle des jetons.
 * <p>
 * Le secret de l'app ne quitte jamais le back : l'échange du code et
 * {@code debug_token} se font ici, de serveur à serveur.
 */
@Component
public class FacebookOAuthClient {

    private final RestClient restClient;
    private final FacebookProperties props;
    private final ObjectMapper mapper;

    public FacebookOAuthClient(RestClient facebookRestClient, FacebookProperties props, ObjectMapper mapper) {
        this.restClient = facebookRestClient;
        this.props = props;
        this.mapper = mapper;
    }

    /** Le code renvoyé par la boîte de dialogue contre un jeton utilisateur de courte durée. */
    public String exchangeCode(String code, String redirectUri) {
        JsonNode response = get(restClient.get().uri(uri -> uri
                .path("/oauth/access_token")
                .queryParam("client_id", props.clientId())
                .queryParam("client_secret", props.clientSecret())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code", code)
                .build()));
        return response.path("access_token").asString();
    }

    /** Jeton utilisateur de courte durée contre un jeton longue durée (environ 60 jours). */
    public String exchangeForLongLived(String userToken) {
        JsonNode response = get(restClient.get().uri(uri -> uri
                .path("/oauth/access_token")
                .queryParam("grant_type", "fb_exchange_token")
                .queryParam("client_id", props.clientId())
                .queryParam("client_secret", props.clientSecret())
                .queryParam("fb_exchange_token", userToken)
                .build()));
        return response.path("access_token").asString();
    }

    /**
     * La Page demandée parmi celles que le compte administre. Obtenu à partir
     * d'un jeton utilisateur longue durée, son jeton n'expire pas.
     */
    public Optional<ManagedPage> findManagedPage(String longLivedUserToken, String pageId) {
        JsonNode response = get(restClient.get().uri(uri -> uri
                .path("/me/accounts")
                .queryParam("fields", "id,name,access_token")
                .queryParam("limit", 100)
                .queryParam("access_token", longLivedUserToken)
                .build()));

        for (JsonNode page : response.path("data")) {
            if (pageId.equals(page.path("id").asString())) {
                return Optional.of(new ManagedPage(
                        page.path("id").asString(),
                        page.path("name").asString(),
                        page.path("access_token").asString()));
            }
        }
        return Optional.empty();
    }

    /** Ce que Facebook dit d'un jeton, avec le jeton d'app comme autorité. */
    public TokenInspection inspect(String token) {
        JsonNode data = get(restClient.get().uri(uri -> uri
                .path("/debug_token")
                .queryParam("input_token", token)
                .queryParam("access_token", props.clientId() + "|" + props.clientSecret())
                .build())).path("data");

        List<String> scopes = new ArrayList<>();
        data.path("scopes").forEach(scope -> scopes.add(scope.asString()));

        String error = data.path("error").path("message").isString()
                ? data.path("error").path("message").asString()
                : null;

        return new TokenInspection(
                data.path("is_valid").asBoolean(false),
                List.copyOf(scopes),
                epoch(data.path("expires_at")),
                epoch(data.path("data_access_expires_at")),
                error);
    }

    private JsonNode get(RestClient.RequestHeadersSpec<?> request) {
        return mapper.readTree(request.retrieve().body(String.class));
    }

    /** Facebook note « pas d'échéance » par 0 ou par l'absence du champ. */
    private static LocalDateTime epoch(JsonNode node) {
        long seconds = node.asLong(0);
        return seconds > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault())
                : null;
    }

    public record ManagedPage(String id, String name, String accessToken) {
    }

    /**
     * @param expiresAt           {@code null} : jeton permanent
     * @param dataAccessExpiresAt {@code null} : non annoncé
     * @param error               motif donné par Facebook d'un jeton invalide
     */
    public record TokenInspection(boolean valid, List<String> scopes, LocalDateTime expiresAt,
                                  LocalDateTime dataAccessExpiresAt, String error) {
    }
}
