package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import fr.maximechazard.agorapilot.back.account.events.SecurityEventService;
import fr.maximechazard.agorapilot.back.account.events.SecurityEventType;
import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publisher.facebook.FacebookPageTokenProvider;
import fr.maximechazard.agorapilot.back.publisher.facebook.FacebookProperties;
import fr.maximechazard.agorapilot.back.publisher.facebook.GraphApiError;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnection.CheckResult;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookOAuthClient.ManagedPage;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookOAuthClient.TokenInspection;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.dtos.FacebookConnectionStatusDTO;
import fr.maximechazard.agorapilot.back.publisher.suspension.ChannelSuspensionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Connexion de la Page Facebook depuis l'interface, et contrôle de son jeton.
 * <p>
 * Remplace, à terme, le bootstrap à la main ({@code POST /admin/facebook/bootstrap})
 * et le renouvellement de {@code FacebookTokenService}, qui ne se déclenche jamais
 * pour un jeton de Page permanent. Les deux mécanismes cohabitent ; cf.
 * {@link FacebookTokenSource}.
 */
@Service
@Slf4j
public class FacebookConnectionService {

    /** Ce qu'il faut pour publier sur la Page, et la retrouver parmi celles du compte. */
    static final List<String> SCOPES = List.of("pages_show_list", "pages_read_engagement", "pages_manage_posts");
    static final String PUBLISH_SCOPE = "pages_manage_posts";
    static final String CALLBACK_PATH = "/api/channels/facebook/callback";
    static final String FRONT_PATH = "/canal-facebook";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final FacebookConnectionRepository connections;
    private final FacebookOAuthStateRepository states;
    private final FacebookOAuthClient oauthClient;
    private final FacebookProperties facebook;
    private final FacebookConnectionProperties props;
    private final FacebookPageTokenProvider tokens;
    private final ChannelSuspensionService suspensions;
    private final SecurityEventService events;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String frontendUrl;

    public FacebookConnectionService(FacebookConnectionRepository connections, FacebookOAuthStateRepository states,
                                     FacebookOAuthClient oauthClient, FacebookProperties facebook,
                                     FacebookConnectionProperties props, FacebookPageTokenProvider tokens,
                                     ChannelSuspensionService suspensions, SecurityEventService events,
                                     ObjectMapper mapper, Clock clock, @Value("${frontend.url}") String frontendUrl) {
        this.connections = connections;
        this.states = states;
        this.oauthClient = oauthClient;
        this.facebook = facebook;
        this.props = props;
        this.tokens = tokens;
        this.suspensions = suspensions;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
    }

    // ------------------------------- État -----------------------------------

    @Transactional(readOnly = true)
    public Optional<FacebookConnection> current() {
        return connections.findTopByOrderByConnectedAtDesc();
    }

    @Transactional(readOnly = true)
    public FacebookConnectionStatusDTO status() {
        return FacebookConnectionStatusDTO.of(props.tokenSource(), tokens.origin(), current().orElse(null));
    }

    // ----------------------------- Connexion --------------------------------

    /**
     * Ouvre une demande de connexion au nom du compte et rend l'adresse de la
     * boîte de dialogue Facebook, où le front envoie le navigateur.
     *
     * @throws FacebookConnectionDisabledException {@code FACEBOOK_TOKEN_SOURCE=legacy}
     */
    @Transactional
    public String start(String username) {
        ensureEnabled();

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        states.deleteCreatedBefore(now.minus(props.stateTtl()));

        String state = randomState();
        states.save(new FacebookOAuthState(state, username, now));

        return UriComponentsBuilder.fromUriString(props.dialogBaseUrl())
                .pathSegment(facebook.apiVersion(), "dialog", "oauth")
                .queryParam("client_id", facebook.clientId())
                .queryParam("redirect_uri", redirectUri())
                .queryParam("state", state)
                .queryParam("scope", String.join(",", SCOPES))
                .queryParam("response_type", "code")
                .encode()
                .build()
                .toUriString();
    }

    /**
     * Le retour de Facebook. Ne lève jamais : l'issue, réussite ou motif d'échec,
     * part dans l'adresse de la page du front vers laquelle le navigateur est
     * renvoyé. La demande est consommée dans tous les cas.
     *
     * @param error renseigné par Facebook quand la personne a refusé ou annulé
     */
    @Transactional
    public String complete(String state, String code, String error) {
        try {
            ensureEnabled();
            FacebookOAuthState request = consume(state);

            if (error != null) {
                throw new FacebookConnectionException("Connexion annulée sur Facebook.");
            }
            if (code == null || code.isBlank()) {
                throw new FacebookConnectionException("Facebook n'a pas renvoyé d'autorisation.");
            }

            FacebookConnection connection = connect(code, request.getUsername());
            log.info("Page Facebook {} connectée par {}.", connection.getPageId(), connection.getConnectedBy());
            return frontUrl("ok", null);

        } catch (FacebookConnectionException | FacebookConnectionDisabledException e) {
            log.warn("Connexion Facebook refusée : {}", e.getMessage());
            return frontUrl("erreur", e.getMessage());
        } catch (RestClientResponseException e) {
            log.warn("Connexion Facebook : l'API Graph a répondu {} — {}", e.getStatusCode(), e.getResponseBodyAsString());
            String reason = GraphApiError.parse(e.getResponseBodyAsString(), mapper)
                    .map(GraphApiError::describe)
                    .orElse(e.getMessage());
            return frontUrl("erreur", "Facebook a refusé la connexion : " + reason);
        } catch (RuntimeException e) {
            log.error("Connexion Facebook en échec", e);
            return frontUrl("erreur", "La connexion à Facebook a échoué : " + e.getMessage());
        }
    }

    private FacebookConnection connect(String code, String username) {
        String shortLived = oauthClient.exchangeCode(code, redirectUri());
        String longLived = oauthClient.exchangeForLongLived(shortLived);

        ManagedPage page = oauthClient.findManagedPage(longLived, facebook.pageId())
                .orElseThrow(() -> new FacebookConnectionException(
                        "Le compte Facebook connecté n'administre pas la Page " + facebook.pageId()
                                + ", ou ne l'a pas cochée lors de la connexion."));

        TokenInspection inspection = oauthClient.inspect(page.accessToken());
        problemWith(inspection).ifPresent(problem -> {
            throw new FacebookConnectionException(problem);
        });

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);

        FacebookConnection connection = new FacebookConnection();
        connection.setPageId(page.id());
        connection.setPageName(page.name());
        connection.setAccessToken(page.accessToken());
        connection.setScopes(String.join(",", inspection.scopes()));
        connection.setConnectedBy(username);
        connection.setConnectedAt(now);
        connection.setExpiresAt(inspection.expiresAt());
        connection.setDataAccessExpiresAt(inspection.dataAccessExpiresAt());
        connection.setLastCheckedAt(now);
        connection.setLastCheckResult(CheckResult.VALID);

        connections.deleteAll();
        connections.save(connection);

        events.record(SecurityEventType.FACEBOOK_CONNECTED, username, null,
                "Page " + page.name() + " (" + page.id() + ")");
        // Un jeton neuf et contrôlé : la raison d'une suspension pour jeton
        // invalide a disparu. Une Page bloquée (368) le serait encore, mais la
        // diffusion suivante la suspendrait de nouveau.
        suspensions.lift(DeliveryChannel.FACEBOOK);

        return connection;
    }

    private FacebookOAuthState consume(String state) {
        FacebookOAuthState request = Optional.ofNullable(state)
                .flatMap(states::findById)
                .orElseThrow(() -> new FacebookConnectionException(
                        "Demande de connexion inconnue ou déjà utilisée : recommencez depuis AgoraPilot."));
        states.delete(request);

        if (request.getCreatedAt().plus(props.stateTtl()).isBefore(LocalDateTime.now(clock))) {
            throw new FacebookConnectionException("Demande de connexion expirée : recommencez depuis AgoraPilot.");
        }
        return request;
    }

    // ------------------------------ Contrôle --------------------------------

    /**
     * Demande à Facebook si le jeton connecté publie encore, et suspend le canal
     * dès qu'il ne le peut plus — avant qu'une diffusion n'échoue. Sans effet
     * hors {@link FacebookTokenSource#CONNECTION} ou sans Page connectée.
     */
    @Transactional
    public Optional<FacebookConnection> check() {
        if (!props.enabled()) {
            return Optional.empty();
        }

        return current().map(connection -> {
            LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
            connection.setLastCheckedAt(now);

            try {
                TokenInspection inspection = oauthClient.inspect(connection.getAccessToken());
                Optional<String> problem = problemWith(inspection);

                connection.setScopes(String.join(",", inspection.scopes()));
                connection.setExpiresAt(inspection.expiresAt());
                connection.setDataAccessExpiresAt(inspection.dataAccessExpiresAt());

                if (problem.isPresent()) {
                    connection.setLastCheckResult(CheckResult.INVALID);
                    connection.setLastCheckMessage(truncate(problem.get()));
                    suspensions.suspend(DeliveryChannel.FACEBOOK,
                            problem.get() + " Reconnectez la Page depuis « Canal Facebook ».");
                } else {
                    connection.setLastCheckResult(CheckResult.VALID);
                    connection.setLastCheckMessage(null);
                }
            } catch (RuntimeException e) {
                // Facebook injoignable ne dit rien du jeton : on ne suspend pas.
                log.warn("Contrôle du jeton Facebook impossible : {}", e.getMessage());
                connection.setLastCheckResult(CheckResult.UNKNOWN);
                connection.setLastCheckMessage(truncate("Contrôle impossible : " + e.getMessage()));
            }

            return connections.save(connection);
        });
    }

    /** Ce qui empêche ce jeton de publier, formulé pour l'utilisateur. */
    static Optional<String> problemWith(TokenInspection inspection) {
        if (!inspection.valid()) {
            return Optional.of("Le jeton Facebook n'est plus valide"
                    + (inspection.error() != null ? " : " + inspection.error() : "") + ".");
        }
        if (!inspection.scopes().contains(PUBLISH_SCOPE)) {
            return Optional.of("La permission " + PUBLISH_SCOPE + " n'a pas été accordée à AgoraPilot.");
        }
        return Optional.empty();
    }

    // ------------------------------- Outils ---------------------------------

    private void ensureEnabled() {
        if (!props.enabled()) {
            throw new FacebookConnectionDisabledException();
        }
    }

    String redirectUri() {
        return frontendUrl + CALLBACK_PATH;
    }

    private String frontUrl(String outcome, String reason) {
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(frontendUrl)
                .path(FRONT_PATH)
                .queryParam("connexion", outcome);
        if (reason != null) {
            url.queryParam("motif", reason);
        }
        return url.encode().build().toUriString();
    }

    private static String randomState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String truncate(String message) {
        return message.length() <= 512 ? message : message.substring(0, 509) + "...";
    }
}
