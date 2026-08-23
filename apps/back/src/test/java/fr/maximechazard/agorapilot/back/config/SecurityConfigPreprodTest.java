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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verrouille la configuration de sécurité du profil {@code preprod}.
 * <p>
 * Le bean {@code permissiveSecurityFilterChain} ne couvrait initialement que le
 * profil {@code dev} : tout autre profil retombait sur la configuration par
 * défaut de Spring Security (HTTP Basic, mot de passe généré) et l'API renvoyait
 * 401 sur l'ensemble des routes.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_preprod_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
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
    void declaresASecurityFilterChainUnderPreprodProfile() {
        assertThat(securityFilterChains).hasSize(1);
    }

    @Test
    void apiIsReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/publications"))
                .andExpect(status().isOk());
    }

    @Test
    void healthEndpointIsReachableForTheDockerHealthcheck() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
