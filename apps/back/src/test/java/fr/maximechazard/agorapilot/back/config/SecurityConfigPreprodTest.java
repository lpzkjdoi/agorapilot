package fr.maximechazard.agorapilot.back.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La configuration de sécurité telle qu'elle tourne en préproduction : une seule
 * chaîne, l'API fermée aux anonymes, le healthcheck ouvert, Swagger absent.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_preprod_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // Racine de médiathèque jetable : sans elle le contexte de test écrirait
        // dans l'arbre de travail (apps/back/data/media).
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
})
@ActiveProfiles("preprod")
@AutoConfigureMockMvc
class SecurityConfigPreprodTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private List<SecurityFilterChain> securityFilterChains;

    @Test
    void declaresASingleSecurityFilterChain() {
        assertThat(securityFilterChains).hasSize(1);
    }

    @Test
    void apiIsClosedToAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/publications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsReachableForTheDockerHealthcheck() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerIsNotServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());
    }

    /** Le front a besoin du jeton avant même de se connecter : la connexion est un POST. */
    @Test
    void handsTheCsrfTokenToAnonymousVisitors() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }
}
