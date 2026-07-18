package fr.maximechazard.agorapilot.back.publisher.facebook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacebookTokenServiceTest {

    private static final FacebookProperties PROPS =
            new FacebookProperties("client-id", "client-secret", "page-id", "", "https://graph.facebook.com", "v25.0");

    @Mock
    private FacebookTokenRepository tokenRepository;

    private RestClient restClient;
    private FacebookTokenService service;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        service = new FacebookTokenService(tokenRepository, PROPS, restClient, new ObjectMapper());
    }

    private static FacebookToken tokenExpiringIn(long days) {
        FacebookToken token = new FacebookToken();
        token.setAccessToken("current-token");
        token.setExpiresAt(LocalDateTime.now().plusDays(days));
        return token;
    }

    /**
     * The service always calls the Graph API in the same order: exchange the long-lived
     * token, inspect it, fetch the page token, inspect it. Stubbing body(String.class) in
     * that same order is what lets a single deep-stub mock stand in for all three endpoints.
     */
    private void stubGraphApiCallSequence(String newUserToken, String newPageToken, Long expiresAtEpoch) {
        String exchangeJson = "{\"access_token\":\"" + newUserToken + "\",\"token_type\":\"bearer\",\"expires_in\":5184000}";
        String pageJson = "{\"access_token\":\"" + newPageToken + "\",\"id\":\"page-id\"}";
        String debugJson = expiresAtEpoch != null
                ? "{\"data\":{\"is_valid\":true,\"expires_at\":" + expiresAtEpoch + "}}"
                : "{\"data\":{\"is_valid\":true}}";

        when(restClient.get().uri(any(Function.class)).retrieve().body(String.class))
                .thenReturn(exchangeJson, debugJson, pageJson, debugJson);
    }

    @Test
    void renewIfNeeded_doesNothing_whenPageTokenNotCloseToExpiry() {
        when(tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(FacebookTokenService.PAGE_TOKEN_TYPE))
                .thenReturn(Optional.of(tokenExpiringIn(30)));

        service.renewIfNeeded();

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(restClient);
    }

    @Test
    void renewIfNeeded_doesNothing_whenNoPageTokenStored() {
        when(tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(FacebookTokenService.PAGE_TOKEN_TYPE))
                .thenReturn(Optional.empty());

        service.renewIfNeeded();

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(restClient);
    }

    @Test
    void renewIfNeeded_doesNothing_whenPageTokenExpiringButNoUserTokenStored() {
        when(tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(FacebookTokenService.PAGE_TOKEN_TYPE))
                .thenReturn(Optional.of(tokenExpiringIn(5)));
        when(tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(FacebookTokenService.USER_TOKEN_TYPE))
                .thenReturn(Optional.empty());

        service.renewIfNeeded();

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(restClient);
    }

    @Test
    void renewIfNeeded_refreshesUserAndPageTokens_whenPageTokenCloseToExpiry() {
        when(tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(FacebookTokenService.PAGE_TOKEN_TYPE))
                .thenReturn(Optional.of(tokenExpiringIn(5)));
        when(tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(FacebookTokenService.USER_TOKEN_TYPE))
                .thenReturn(Optional.of(tokenExpiringIn(20)));
        stubGraphApiCallSequence("new-user-token", "new-page-token",
                LocalDateTime.now().plusDays(50).toEpochSecond(ZoneOffset.UTC));

        service.renewIfNeeded();

        ArgumentCaptor<FacebookToken> captor = ArgumentCaptor.forClass(FacebookToken.class);
        verify(tokenRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(FacebookToken::getTokenType, FacebookToken::getAccessToken)
                .containsExactly(
                        tuple(FacebookTokenService.USER_TOKEN_TYPE, "new-user-token"),
                        tuple(FacebookTokenService.PAGE_TOKEN_TYPE, "new-page-token")
                );
    }

    @Test
    void bootstrap_savesUserThenPageToken() {
        when(tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(any())).thenReturn(Optional.empty());
        stubGraphApiCallSequence("long-lived-user-token", "page-token",
                LocalDateTime.now().plusDays(50).toEpochSecond(ZoneOffset.UTC));

        service.bootstrap("short-lived-token");

        ArgumentCaptor<FacebookToken> captor = ArgumentCaptor.forClass(FacebookToken.class);
        verify(tokenRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(FacebookToken::getTokenType, FacebookToken::getAccessToken)
                .containsExactly(
                        tuple(FacebookTokenService.USER_TOKEN_TYPE, "long-lived-user-token"),
                        tuple(FacebookTokenService.PAGE_TOKEN_TYPE, "page-token")
                );
    }

    @Test
    void bootstrap_treatsMissingExpiresAt_asPermanentToken() {
        when(tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(any())).thenReturn(Optional.empty());
        stubGraphApiCallSequence("user-token", "page-token", null);

        service.bootstrap("short-lived-token");

        ArgumentCaptor<FacebookToken> captor = ArgumentCaptor.forClass(FacebookToken.class);
        verify(tokenRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(token -> assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now().plusYears(5)));
    }

    @Test
    void getCurrentPageToken_throws_whenNoTokenStored() {
        when(tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(FacebookTokenService.PAGE_TOKEN_TYPE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentPageToken())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getCurrentPageToken_returnsStoredToken() {
        when(tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(FacebookTokenService.PAGE_TOKEN_TYPE))
                .thenReturn(Optional.of(tokenExpiringIn(30)));

        assertThat(service.getCurrentPageToken()).isEqualTo("current-token");
    }

    @Test
    void getTokenStatus_returnsPreviewAndDaysRemaining() {
        FacebookToken token = tokenExpiringIn(10);
        token.setAccessToken("abcdefghijklmnop");
        when(tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(FacebookTokenService.PAGE_TOKEN_TYPE))
                .thenReturn(Optional.of(token));

        Optional<FacebookTokenService.TokenStatus> status = service.getTokenStatus(FacebookTokenService.PAGE_TOKEN_TYPE);

        assertThat(status).isPresent();
        assertThat(status.get().tokenPreview()).isEqualTo("abcdefghij...");
        assertThat(status.get().daysRemaining()).isEqualTo(9L);
    }

    @Test
    void getTokenStatus_returnsEmpty_whenNoTokenStored() {
        when(tokenRepository.findTopByTokenTypeOrderByUpdatedAtDesc(FacebookTokenService.USER_TOKEN_TYPE))
                .thenReturn(Optional.empty());

        assertThat(service.getTokenStatus(FacebookTokenService.USER_TOKEN_TYPE)).isEmpty();
    }
}
