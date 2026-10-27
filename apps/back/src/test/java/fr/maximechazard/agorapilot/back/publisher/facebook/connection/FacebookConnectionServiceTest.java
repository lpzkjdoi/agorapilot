package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import fr.maximechazard.agorapilot.back.account.events.SecurityEventService;
import fr.maximechazard.agorapilot.back.account.events.SecurityEventType;
import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publisher.facebook.FacebookPageTokenProvider;
import fr.maximechazard.agorapilot.back.publisher.facebook.FacebookProperties;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnection.CheckResult;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookOAuthClient.ManagedPage;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookOAuthClient.TokenInspection;
import fr.maximechazard.agorapilot.back.publisher.suspension.ChannelSuspensionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacebookConnectionServiceTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 10, 0);
    private static final String FRONT = "https://agora.test";
    private static final FacebookProperties FACEBOOK =
            new FacebookProperties("client-id", "client-secret", "page-id", "https://graph.facebook.com", "v25.0");
    private static final TokenInspection VALID = new TokenInspection(true,
            List.of("pages_show_list", "pages_read_engagement", "pages_manage_posts"), null, null, null);

    @Mock
    private FacebookConnectionRepository connections;
    @Mock
    private FacebookOAuthStateRepository states;
    @Mock
    private FacebookOAuthClient oauthClient;
    @Mock
    private FacebookPageTokenProvider tokens;
    @Mock
    private ChannelSuspensionService suspensions;
    @Mock
    private SecurityEventService events;

    private FacebookConnectionService service;

    @BeforeEach
    void setUp() {
        service = serviceWith(FacebookTokenSource.CONNECTION);
    }

    private FacebookConnectionService serviceWith(FacebookTokenSource source) {
        Clock clock = Clock.fixed(NOW.atZone(PARIS).toInstant(), PARIS);
        return new FacebookConnectionService(connections, states, oauthClient, FACEBOOK,
                new FacebookConnectionProperties(source, "https://www.facebook.com", Duration.ofMinutes(10)),
                tokens, suspensions, events, new ObjectMapper(), clock, FRONT);
    }

    private static UriComponents parse(String url) {
        return UriComponentsBuilder.fromUriString(url).build();
    }

    private static String decoded(UriComponents url, String param) {
        return java.net.URLDecoder.decode(url.getQueryParams().getFirst(param), StandardCharsets.UTF_8);
    }

    @Nested
    class Start {

        @Test
        void opens_a_request_and_sends_the_browser_to_the_facebook_dialog() {
            UriComponents url = parse(service.start("maxime"));

            ArgumentCaptor<FacebookOAuthState> saved = ArgumentCaptor.forClass(FacebookOAuthState.class);
            verify(states).save(saved.capture());
            assertThat(saved.getValue().getUsername()).isEqualTo("maxime");
            assertThat(saved.getValue().getState()).hasSizeGreaterThanOrEqualTo(32);

            assertThat(url.getHost()).isEqualTo("www.facebook.com");
            assertThat(url.getPath()).isEqualTo("/v25.0/dialog/oauth");
            assertThat(url.getQueryParams().getFirst("client_id")).isEqualTo("client-id");
            assertThat(url.getQueryParams().getFirst("state")).isEqualTo(saved.getValue().getState());
            assertThat(decoded(url, "redirect_uri")).isEqualTo(FRONT + "/api/channels/facebook/callback");
            assertThat(decoded(url, "scope")).isEqualTo("pages_show_list,pages_read_engagement,pages_manage_posts");
        }

        @Test
        void purges_requests_older_than_their_lifetime() {
            service.start("maxime");

            verify(states).deleteCreatedBefore(NOW.minusMinutes(10));
        }

        @Test
        void is_refused_when_the_legacy_mechanism_is_selected() {
            FacebookConnectionService legacy = serviceWith(FacebookTokenSource.LEGACY);

            assertThatThrownBy(() -> legacy.start("maxime")).isInstanceOf(FacebookConnectionDisabledException.class);
            verifyNoInteractions(states);
        }
    }

    @Nested
    class Complete {

        private void pendingRequest(LocalDateTime createdAt) {
            when(states.findById("etat")).thenReturn(Optional.of(new FacebookOAuthState("etat", "maxime", createdAt)));
        }

        private void facebookGrants(TokenInspection inspection) {
            when(oauthClient.exchangeCode("code", FRONT + "/api/channels/facebook/callback")).thenReturn("court");
            when(oauthClient.exchangeForLongLived("court")).thenReturn("long");
            when(oauthClient.findManagedPage("long", "page-id"))
                    .thenReturn(Optional.of(new ManagedPage("page-id", "Mairie", "jeton-page")));
            when(oauthClient.inspect("jeton-page")).thenReturn(inspection);
        }

        private String reasonOf(String redirect) {
            UriComponents url = parse(redirect);
            assertThat(url.getPath()).isEqualTo("/canal-facebook");
            assertThat(url.getQueryParams().getFirst("connexion")).isEqualTo("erreur");
            return decoded(url, "motif");
        }

        @Test
        void stores_the_page_token_and_lifts_the_suspension() {
            pendingRequest(NOW.minusMinutes(2));
            facebookGrants(VALID);

            String redirect = service.complete("etat", "code", null);

            assertThat(redirect).isEqualTo(FRONT + "/canal-facebook?connexion=ok");

            ArgumentCaptor<FacebookConnection> saved = ArgumentCaptor.forClass(FacebookConnection.class);
            verify(connections).deleteAll();
            verify(connections).save(saved.capture());
            FacebookConnection connection = saved.getValue();
            assertThat(connection.getPageId()).isEqualTo("page-id");
            assertThat(connection.getPageName()).isEqualTo("Mairie");
            assertThat(connection.getAccessToken()).isEqualTo("jeton-page");
            assertThat(connection.getConnectedBy()).isEqualTo("maxime");
            assertThat(connection.getConnectedAt()).isEqualTo(NOW);
            assertThat(connection.getExpiresAt()).isNull();
            assertThat(connection.getLastCheckResult()).isEqualTo(CheckResult.VALID);

            verify(events).record(SecurityEventType.FACEBOOK_CONNECTED, "maxime", null, "Page Mairie (page-id)");
            verify(suspensions).lift(DeliveryChannel.FACEBOOK);
        }

        @Test
        void consumes_the_request_so_it_cannot_be_replayed() {
            pendingRequest(NOW.minusMinutes(2));
            facebookGrants(VALID);

            service.complete("etat", "code", null);

            verify(states).delete(any(FacebookOAuthState.class));
        }

        @Test
        void refuses_an_unknown_state() {
            when(states.findById("inconnu")).thenReturn(Optional.empty());

            assertThat(reasonOf(service.complete("inconnu", "code", null))).contains("inconnue ou déjà utilisée");
            verifyNoInteractions(oauthClient, suspensions);
        }

        @Test
        void refuses_a_missing_state() {
            assertThat(reasonOf(service.complete(null, "code", null))).contains("inconnue ou déjà utilisée");
            verifyNoInteractions(oauthClient);
        }

        @Test
        void refuses_an_expired_request() {
            pendingRequest(NOW.minusMinutes(11));

            assertThat(reasonOf(service.complete("etat", "code", null))).contains("expirée");
            verifyNoInteractions(oauthClient);
        }

        @Test
        void reports_a_cancellation_on_facebook() {
            pendingRequest(NOW.minusMinutes(2));

            assertThat(reasonOf(service.complete("etat", null, "access_denied"))).isEqualTo("Connexion annulée sur Facebook.");
            verifyNoInteractions(oauthClient);
        }

        @Test
        void refuses_an_account_that_does_not_manage_the_page() {
            pendingRequest(NOW.minusMinutes(2));
            when(oauthClient.exchangeCode(anyString(), anyString())).thenReturn("court");
            when(oauthClient.exchangeForLongLived("court")).thenReturn("long");
            when(oauthClient.findManagedPage("long", "page-id")).thenReturn(Optional.empty());

            assertThat(reasonOf(service.complete("etat", "code", null))).contains("n'administre pas la Page page-id");
            verify(connections, never()).save(any());
            verify(suspensions, never()).lift(any());
        }

        @Test
        void refuses_a_token_without_the_publishing_permission() {
            pendingRequest(NOW.minusMinutes(2));
            facebookGrants(new TokenInspection(true, List.of("pages_show_list"), null, null, null));

            assertThat(reasonOf(service.complete("etat", "code", null))).contains("pages_manage_posts");
            verify(connections, never()).save(any());
        }

        @Test
        void relays_facebooks_refusal() {
            pendingRequest(NOW.minusMinutes(2));
            when(oauthClient.exchangeCode(anyString(), anyString())).thenThrow(HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST, "Bad Request", null,
                    "{\"error\":{\"message\":\"This authorization code has expired.\",\"code\":100}}"
                            .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

            assertThat(reasonOf(service.complete("etat", "code", null))).contains("This authorization code has expired.");
        }

        @Test
        void is_refused_when_the_legacy_mechanism_is_selected() {
            String redirect = serviceWith(FacebookTokenSource.LEGACY).complete("etat", "code", null);

            assertThat(reasonOf(redirect)).contains("FACEBOOK_TOKEN_SOURCE=legacy");
            verifyNoInteractions(states, oauthClient);
        }
    }

    @Nested
    class Check {

        private FacebookConnection connected() {
            FacebookConnection connection = new FacebookConnection();
            connection.setPageId("page-id");
            connection.setAccessToken("jeton-page");
            connection.setScopes("pages_manage_posts");
            when(connections.findTopByOrderByConnectedAtDesc()).thenReturn(Optional.of(connection));
            when(connections.save(any(FacebookConnection.class))).thenAnswer(invocation -> invocation.getArgument(0));
            return connection;
        }

        @Test
        void records_a_valid_token_without_touching_the_channel() {
            FacebookConnection connection = connected();
            when(oauthClient.inspect("jeton-page")).thenReturn(VALID);

            service.check();

            assertThat(connection.getLastCheckResult()).isEqualTo(CheckResult.VALID);
            assertThat(connection.getLastCheckedAt()).isEqualTo(NOW);
            assertThat(connection.getLastCheckMessage()).isNull();
            verifyNoInteractions(suspensions);
        }

        @Test
        void suspends_the_channel_as_soon_as_the_token_is_invalid() {
            FacebookConnection connection = connected();
            when(oauthClient.inspect("jeton-page")).thenReturn(new TokenInspection(false, List.of(), null, null,
                    "The user changed their password."));

            service.check();

            assertThat(connection.getLastCheckResult()).isEqualTo(CheckResult.INVALID);
            assertThat(connection.getLastCheckMessage()).contains("changed their password");
            verify(suspensions).suspend(eq(DeliveryChannel.FACEBOOK), contains("Reconnectez la Page"));
        }

        @Test
        void suspends_the_channel_when_the_publishing_permission_was_withdrawn() {
            connected();
            when(oauthClient.inspect("jeton-page")).thenReturn(new TokenInspection(true, List.of("pages_show_list"),
                    null, null, null));

            service.check();

            verify(suspensions).suspend(eq(DeliveryChannel.FACEBOOK), contains("pages_manage_posts"));
        }

        @Test
        void concludes_nothing_when_facebook_cannot_be_reached() {
            FacebookConnection connection = connected();
            when(oauthClient.inspect("jeton-page")).thenThrow(new ResourceAccessException("Read timed out"));

            service.check();

            assertThat(connection.getLastCheckResult()).isEqualTo(CheckResult.UNKNOWN);
            assertThat(connection.getLastCheckMessage()).contains("Read timed out");
            verifyNoInteractions(suspensions);
        }

        @Test
        void does_nothing_without_a_connected_page() {
            when(connections.findTopByOrderByConnectedAtDesc()).thenReturn(Optional.empty());

            assertThat(service.check()).isEmpty();
            verifyNoInteractions(oauthClient, suspensions);
        }

        @Test
        void does_nothing_with_the_legacy_mechanism() {
            assertThat(serviceWith(FacebookTokenSource.LEGACY).check()).isEmpty();
            verifyNoInteractions(connections, oauthClient, suspensions);
        }
    }

    @Test
    void reports_the_source_and_the_token_in_use() {
        when(connections.findTopByOrderByConnectedAtDesc()).thenReturn(Optional.empty());
        when(tokens.origin()).thenReturn(FacebookPageTokenProvider.Origin.LEGACY);

        var status = service.status();

        assertThat(status.source()).isEqualTo(FacebookTokenSource.CONNECTION);
        assertThat(status.tokenInUse()).isEqualTo(FacebookPageTokenProvider.Origin.LEGACY);
        assertThat(status.connection()).isNull();
    }

    @Test
    void lets_unexpected_errors_of_the_start_propagate() {
        when(states.save(any())).thenThrow(new IllegalStateException("base indisponible"));

        assertThatThrownBy(() -> service.start("maxime")).hasMessage("base indisponible");
    }
}
