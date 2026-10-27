package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import fr.maximechazard.agorapilot.back.publisher.facebook.FacebookPageTokenProvider;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.dtos.FacebookConnectionStatusDTO;
import fr.maximechazard.agorapilot.back.support.ApiControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApiControllerTest(FacebookConnectionController.class)
class FacebookConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacebookConnectionService connectionService;

    private static FacebookConnectionStatusDTO connectedStatus() {
        return new FacebookConnectionStatusDTO(FacebookTokenSource.CONNECTION, FacebookPageTokenProvider.Origin.CONNECTION,
                new FacebookConnectionStatusDTO.Connection("page-id", "Mairie", List.of("pages_manage_posts"), "maxime",
                        LocalDateTime.of(2026, 10, 27, 10, 0), null, null,
                        LocalDateTime.of(2026, 10, 28, 7, 0), FacebookConnection.CheckResult.VALID, null));
    }

    @Test
    void describes_the_connection_without_ever_exposing_the_token() throws Exception {
        when(connectionService.status()).thenReturn(connectedStatus());

        mockMvc.perform(get("/api/channels/facebook/connection"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.source").value("CONNECTION"))
               .andExpect(jsonPath("$.tokenInUse").value("CONNECTION"))
               .andExpect(jsonPath("$.connection.pageName").value("Mairie"))
               .andExpect(jsonPath("$.connection.connectedBy").value("maxime"))
               .andExpect(jsonPath("$.connection.lastCheckResult").value("VALID"))
               .andExpect(jsonPath("$.connection.expiresAt").isEmpty())
               .andExpect(jsonPath("$.connection.accessToken").doesNotExist());
    }

    // Ouvrir une demande exige un vrai compte (AuthenticatedUser) : couvert par
    // FacebookConnectionFlowTest, de bout en bout.

    @Test
    void checks_the_token_on_demand() throws Exception {
        when(connectionService.status()).thenReturn(connectedStatus());

        mockMvc.perform(post("/api/channels/facebook/connection/check"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.connection.lastCheckResult").value("VALID"));
        verify(connectionService).check();
    }

    @Test
    void sends_the_browser_back_to_the_front_after_facebook() throws Exception {
        when(connectionService.complete("etat", "code", null)).thenReturn("https://agora.test/canal-facebook?connexion=ok");

        mockMvc.perform(get("/api/channels/facebook/callback").param("state", "etat").param("code", "code"))
               .andExpect(status().isFound())
               .andExpect(header().string("Location", "https://agora.test/canal-facebook?connexion=ok"));
    }
}
