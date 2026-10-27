package fr.maximechazard.agorapilot.back.publisher.facebook;

import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnection;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnectionProperties;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnectionRepository;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookTokenSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Le retour arrière tient à ce choix : quel jeton publie, selon FACEBOOK_TOKEN_SOURCE. */
@ExtendWith(MockitoExtension.class)
class FacebookPageTokenProviderTest {

    @Mock
    private FacebookConnectionRepository connections;
    @Mock
    private FacebookTokenService legacyTokens;

    private FacebookPageTokenProvider provider(FacebookTokenSource source) {
        return new FacebookPageTokenProvider(
                new FacebookConnectionProperties(source, "https://www.facebook.com", Duration.ofMinutes(10)),
                connections, legacyTokens);
    }

    private void connected() {
        FacebookConnection connection = new FacebookConnection();
        connection.setAccessToken("jeton-connecte");
        when(connections.findTopByOrderByConnectedAtDesc()).thenReturn(Optional.of(connection));
    }

    @Test
    void publishes_with_the_connected_token() {
        connected();

        FacebookPageTokenProvider provider = provider(FacebookTokenSource.CONNECTION);

        assertThat(provider.currentPageToken()).isEqualTo("jeton-connecte");
        assertThat(provider.origin()).isEqualTo(FacebookPageTokenProvider.Origin.CONNECTION);
        verifyNoInteractions(legacyTokens);
    }

    @Test
    void falls_back_on_the_legacy_token_until_a_page_is_connected() {
        when(connections.findTopByOrderByConnectedAtDesc()).thenReturn(Optional.empty());
        when(legacyTokens.getCurrentPageToken()).thenReturn("jeton-historique");

        assertThat(provider(FacebookTokenSource.CONNECTION).currentPageToken()).isEqualTo("jeton-historique");
    }

    @Test
    void ignores_the_connection_with_the_legacy_mechanism() {
        when(legacyTokens.getCurrentPageToken()).thenReturn("jeton-historique");
        when(legacyTokens.getTokenStatus(FacebookTokenService.PAGE_TOKEN_TYPE)).thenReturn(Optional.of(
                new FacebookTokenService.TokenStatus("jeton-hist...", LocalDateTime.now().plusYears(10), 3650)));

        FacebookPageTokenProvider provider = provider(FacebookTokenSource.LEGACY);

        assertThat(provider.currentPageToken()).isEqualTo("jeton-historique");
        assertThat(provider.origin()).isEqualTo(FacebookPageTokenProvider.Origin.LEGACY);
        verifyNoInteractions(connections);
    }

    @Test
    void reports_that_no_token_exists() {
        when(connections.findTopByOrderByConnectedAtDesc()).thenReturn(Optional.empty());
        when(legacyTokens.getTokenStatus(FacebookTokenService.PAGE_TOKEN_TYPE)).thenReturn(Optional.empty());
        when(legacyTokens.getCurrentPageToken()).thenThrow(new IllegalStateException("No token available"));

        FacebookPageTokenProvider provider = provider(FacebookTokenSource.CONNECTION);

        assertThat(provider.origin()).isEqualTo(FacebookPageTokenProvider.Origin.NONE);
        assertThatThrownBy(provider::currentPageToken).hasMessage("No token available");
    }
}
