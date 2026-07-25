package fr.maximechazard.agorapilot.back.publisher.facebook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie que les erreurs de l'API Graph remontent au format exploitable plutôt
 * qu'en 500 opaque — cas réellement rencontré lors de la mise en service de la
 * préproduction, où le message de Facebook n'était visible que dans les logs du
 * conteneur.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_fb_error_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("preprod")
@AutoConfigureMockMvc
class FacebookAdminExceptionHandlerTest {

    private static final String GRAPH_API_ERROR = """
            {"error":{"message":"The access token does not belong to application 944918291404883",\
            "type":"OAuthException","code":1,"fbtrace_id":"AZA1LYnqgHWvTUELQrq0C1t"}}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacebookTokenService tokenService;

    @Test
    void translatesGraphApiClientErrorIntoReadableBadRequest() throws Exception {
        willThrow(new HttpClientErrorException(
                HttpStatus.BAD_REQUEST, "Bad Request",
                GRAPH_API_ERROR.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
                .given(tokenService).bootstrap(anyString());

        mockMvc.perform(post("/admin/facebook/bootstrap").param("shortLivedToken", "peu-importe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("facebook_api_error"))
                .andExpect(jsonPath("$.message")
                        .value("The access token does not belong to application 944918291404883"))
                .andExpect(jsonPath("$.facebookCode").value(1))
                .andExpect(jsonPath("$.upstreamStatus").value(400));
    }

    @Test
    void translatesGraphApiServerErrorIntoBadGateway() throws Exception {
        willThrow(new HttpServerErrorException(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                "indisponible".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
                .given(tokenService).bootstrap(anyString());

        mockMvc.perform(post("/admin/facebook/bootstrap").param("shortLivedToken", "peu-importe"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.upstreamStatus").value(503));
    }

    @Test
    void keepsRawBodyWhenFacebookResponseIsNotTheExpectedShape() throws Exception {
        willThrow(new HttpClientErrorException(
                HttpStatus.BAD_REQUEST, "Bad Request",
                "pas du json".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
                .given(tokenService).bootstrap(anyString());

        mockMvc.perform(post("/admin/facebook/bootstrap").param("shortLivedToken", "peu-importe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("pas du json"));
    }

    @Test
    void translatesMissingTokenIntoConflict() throws Exception {
        willThrow(new IllegalStateException("No token available"))
                .given(tokenService).getTokenStatus(anyString());

        mockMvc.perform(get("/admin/facebook/token/status"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("facebook_invalid_state"))
                .andExpect(jsonPath("$.message").value("No token available"));
    }
}
