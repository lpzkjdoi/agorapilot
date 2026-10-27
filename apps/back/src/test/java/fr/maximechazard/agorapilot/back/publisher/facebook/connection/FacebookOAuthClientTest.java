package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import fr.maximechazard.agorapilot.back.publisher.facebook.FacebookProperties;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookOAuthClient.TokenInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FacebookOAuthClientTest {

    private static final FacebookProperties PROPS =
            new FacebookProperties("client-id", "client-secret", "page-id", "https://graph.facebook.com", "v25.0");
    private static final String GRAPH = "https://graph.facebook.com/v25.0";

    private MockRestServiceServer server;
    private FacebookOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(GRAPH);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FacebookOAuthClient(builder.build(), PROPS, new ObjectMapper());
    }

    @Test
    void exchanges_the_code_with_the_same_redirect_uri_as_the_dialog() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(GRAPH + "/oauth/access_token")))
              .andExpect(method(HttpMethod.GET))
              .andExpect(queryParam("code", "le-code"))
              .andExpect(queryParam("client_secret", "client-secret"))
              .andExpect(queryParam("redirect_uri", "https://agora.test/api/channels/facebook/callback"))
              .andRespond(withSuccess("{\"access_token\":\"court\"}", MediaType.APPLICATION_JSON));

        assertThat(client.exchangeCode("le-code", "https://agora.test/api/channels/facebook/callback"))
                .isEqualTo("court");
        server.verify();
    }

    @Test
    void exchanges_a_short_lived_user_token_for_a_long_lived_one() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(GRAPH + "/oauth/access_token")))
              .andExpect(queryParam("grant_type", "fb_exchange_token"))
              .andExpect(queryParam("fb_exchange_token", "court"))
              .andRespond(withSuccess("{\"access_token\":\"long\",\"expires_in\":5184000}", MediaType.APPLICATION_JSON));

        assertThat(client.exchangeForLongLived("court")).isEqualTo("long");
        server.verify();
    }

    @Test
    void finds_the_configured_page_among_the_managed_ones() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(GRAPH + "/me/accounts")))
              .andExpect(queryParam("access_token", "long"))
              .andRespond(withSuccess("""
                      {"data":[
                        {"id":"autre","name":"Autre Page","access_token":"jeton-autre"},
                        {"id":"page-id","name":"Mairie","access_token":"jeton-page"}
                      ]}""", MediaType.APPLICATION_JSON));

        assertThat(client.findManagedPage("long", "page-id"))
                .contains(new FacebookOAuthClient.ManagedPage("page-id", "Mairie", "jeton-page"));
    }

    @Test
    void finds_nothing_when_the_account_does_not_manage_the_page() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(GRAPH + "/me/accounts")))
              .andRespond(withSuccess("{\"data\":[{\"id\":\"autre\",\"name\":\"Autre\",\"access_token\":\"x\"}]}",
                      MediaType.APPLICATION_JSON));

        assertThat(client.findManagedPage("long", "page-id")).isEmpty();
    }

    @Test
    void reads_validity_scopes_and_expiries_from_debug_token() {
        long dataAccess = 1_800_000_000L;
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(GRAPH + "/debug_token")))
              .andExpect(queryParam("input_token", "jeton-page"))
              .andExpect(queryParam("access_token", "client-id%7Cclient-secret"))
              .andRespond(withSuccess("""
                      {"data":{"is_valid":true,"expires_at":0,"data_access_expires_at":%d,
                               "scopes":["pages_show_list","pages_manage_posts"]}}""".formatted(dataAccess),
                      MediaType.APPLICATION_JSON));

        TokenInspection inspection = client.inspect("jeton-page");

        assertThat(inspection.valid()).isTrue();
        assertThat(inspection.scopes()).containsExactly("pages_show_list", "pages_manage_posts");
        // 0 : jeton permanent, sans échéance — pas une fausse date à dix ans.
        assertThat(inspection.expiresAt()).isNull();
        assertThat(inspection.dataAccessExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(Instant.ofEpochSecond(dataAccess), ZoneId.systemDefault()));
        assertThat(inspection.error()).isNull();
    }

    @Test
    void reports_facebooks_reason_for_an_invalid_token() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(GRAPH + "/debug_token")))
              .andRespond(withSuccess("""
                      {"data":{"is_valid":false,"scopes":[],
                               "error":{"code":190,"message":"The session has been invalidated because the user changed their password."}}}""",
                      MediaType.APPLICATION_JSON));

        TokenInspection inspection = client.inspect("jeton-page");

        assertThat(inspection.valid()).isFalse();
        assertThat(inspection.error()).contains("changed their password");
    }
}
