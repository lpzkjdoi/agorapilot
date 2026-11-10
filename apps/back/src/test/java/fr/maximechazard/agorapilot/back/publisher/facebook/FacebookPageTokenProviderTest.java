package fr.maximechazard.agorapilot.back.publisher.facebook;

import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnection;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookConnectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacebookPageTokenProviderTest {

    @Mock
    private FacebookConnectionRepository connections;

    @InjectMocks
    private FacebookPageTokenProvider provider;

    @Test
    void publishes_with_the_connected_token() {
        FacebookConnection connection = new FacebookConnection();
        connection.setAccessToken("jeton-connecte");
        when(connections.findTopByOrderByConnectedAtDesc()).thenReturn(Optional.of(connection));

        assertThat(provider.currentPageToken()).isEqualTo("jeton-connecte");
    }

    /** Le message finit dans le motif d'échec de la diffusion : il dit quoi faire. */
    @Test
    void refuses_to_publish_without_a_connected_page() {
        when(connections.findTopByOrderByConnectedAtDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(provider::currentPageToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Canal Facebook");
    }
}
