package fr.maximechazard.agorapilot.back.account;

import fr.maximechazard.agorapilot.back.publisher.facebook.FacebookTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import java.util.Arrays;
import java.util.stream.Stream;

import static fr.maximechazard.agorapilot.back.account.Permission.CAMPAIGNS_READ;
import static fr.maximechazard.agorapilot.back.account.Permission.CAMPAIGNS_WRITE;
import static fr.maximechazard.agorapilot.back.account.Permission.CHANNELS_MANAGE;
import static fr.maximechazard.agorapilot.back.account.Permission.MEDIAS_DELETE;
import static fr.maximechazard.agorapilot.back.account.Permission.MEDIAS_READ;
import static fr.maximechazard.agorapilot.back.account.Permission.MEDIAS_WRITE;
import static fr.maximechazard.agorapilot.back.account.Permission.OCCURRENCES_SCHEDULE;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_DELETE;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_PUBLISH;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_READ;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_WRITE;
import static fr.maximechazard.agorapilot.back.account.Permission.USERS_MANAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La table des droits de {@code SecurityConfig}, endpoint par endpoint : refusé
 * aux anonymes (401), refusé à qui a tous les droits sauf le bon (403), ouvert à
 * qui n'a que le bon.
 * <p>
 * Ouvert veut dire « ni 401 ni 403 » : la requête atteint le contrôleur, qui peut
 * répondre 400 ou 404 sur ces identifiants inventés.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_endpoint_permissions;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "spring.jpa.show-sql=false"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class EndpointPermissionsTest {

    private static final String JSON = "{}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository repository;

    /** Le jeton Facebook ne doit pas être réellement demandé à Facebook. */
    @MockitoBean
    private FacebookTokenService facebookTokenService;

    /**
     * Une session ouverte par un vrai compte qui détient exactement ces droits :
     * {@code AccountRefreshFilter} le relit en base, comme en production.
     */
    private RequestPostProcessor accountWith(Permission... permissions) {
        repository.deleteAll();
        UserAccount account = repository.save(new UserAccount("tester", "pas-de-mot-de-passe", AccountStatus.ACTIVE,
                Set.of(permissions), LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MICROS)));
        AuthenticatedUser user = new AuthenticatedUser(account.getId(), account.getUsername(), LocalDateTime.now());
        RequestPostProcessor session = authentication(AccountRefreshFilter.authenticationFor(user, account));

        return request -> csrf().postProcessRequest(session.postProcessRequest(request));
    }

    record Endpoint(HttpMethod method, String path, Permission required) {
        MockHttpServletRequestBuilder toRequest() {
            return request(method, path).contentType(MediaType.APPLICATION_JSON).content(JSON);
        }

        @Override
        public String toString() {
            return method + " " + path + " → " + required.code();
        }
    }

    static Stream<Endpoint> endpoints() {
        return Stream.of(
                new Endpoint(HttpMethod.GET, "/api/publications", PUBLICATIONS_READ),
                new Endpoint(HttpMethod.POST, "/api/publications", PUBLICATIONS_WRITE),
                new Endpoint(HttpMethod.PUT, "/api/publications/999", PUBLICATIONS_WRITE),
                new Endpoint(HttpMethod.PUT, "/api/publications/999/medias", PUBLICATIONS_WRITE),
                new Endpoint(HttpMethod.PUT, "/api/publications/999/campaign", PUBLICATIONS_WRITE),
                new Endpoint(HttpMethod.DELETE, "/api/publications/999", PUBLICATIONS_DELETE),
                new Endpoint(HttpMethod.POST, "/api/publications/999/deliveries", PUBLICATIONS_PUBLISH),

                new Endpoint(HttpMethod.GET, "/api/occurrences/weekly", PUBLICATIONS_READ),
                new Endpoint(HttpMethod.GET, "/api/occurrences?from=2026-10-01&to=2026-11-01", PUBLICATIONS_READ),
                new Endpoint(HttpMethod.POST, "/api/occurrences", OCCURRENCES_SCHEDULE),
                new Endpoint(HttpMethod.POST, "/api/occurrences/999/retry", OCCURRENCES_SCHEDULE),
                new Endpoint(HttpMethod.PUT, "/api/occurrences/999/schedule", OCCURRENCES_SCHEDULE),
                new Endpoint(HttpMethod.DELETE, "/api/occurrences/999", OCCURRENCES_SCHEDULE),

                new Endpoint(HttpMethod.GET, "/api/dashboard", PUBLICATIONS_READ),

                new Endpoint(HttpMethod.GET, "/api/channels/suspensions", PUBLICATIONS_READ),
                new Endpoint(HttpMethod.DELETE, "/api/channels/FACEBOOK/suspension", CHANNELS_MANAGE),
                new Endpoint(HttpMethod.GET, "/api/channels/facebook/connection", CHANNELS_MANAGE),
                new Endpoint(HttpMethod.POST, "/api/channels/facebook/connection", CHANNELS_MANAGE),
                new Endpoint(HttpMethod.POST, "/api/channels/facebook/connection/check", CHANNELS_MANAGE),
                new Endpoint(HttpMethod.GET, "/admin/scheduler/status", CHANNELS_MANAGE),
                new Endpoint(HttpMethod.GET, "/admin/facebook/token/status", CHANNELS_MANAGE),
                new Endpoint(HttpMethod.POST, "/admin/facebook/bootstrap?shortLivedToken=x", CHANNELS_MANAGE),

                new Endpoint(HttpMethod.GET, "/api/campaigns", CAMPAIGNS_READ),
                new Endpoint(HttpMethod.POST, "/api/campaigns", CAMPAIGNS_WRITE),
                new Endpoint(HttpMethod.POST, "/api/campaigns/999/closure", CAMPAIGNS_WRITE),

                new Endpoint(HttpMethod.GET, "/api/medias", MEDIAS_READ),
                new Endpoint(HttpMethod.GET, "/api/medias/999", MEDIAS_READ),
                new Endpoint(HttpMethod.GET, "/api/medias/999/file", MEDIAS_READ),
                new Endpoint(HttpMethod.POST, "/api/medias", MEDIAS_WRITE),
                new Endpoint(HttpMethod.PATCH, "/api/medias/999", MEDIAS_WRITE),
                new Endpoint(HttpMethod.DELETE, "/api/medias/999", MEDIAS_DELETE),

                new Endpoint(HttpMethod.GET, "/api/users", USERS_MANAGE),
                new Endpoint(HttpMethod.POST, "/api/users", USERS_MANAGE),
                new Endpoint(HttpMethod.PUT, "/api/users/999/permissions", USERS_MANAGE),
                new Endpoint(HttpMethod.POST, "/api/users/999/disable", USERS_MANAGE),
                new Endpoint(HttpMethod.POST, "/api/users/999/password-reset", USERS_MANAGE),
                new Endpoint(HttpMethod.GET, "/api/security-events", USERS_MANAGE)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void is_closed_to_anonymous_requests(Endpoint endpoint) throws Exception {
        mockMvc.perform(endpoint.toRequest().with(csrf()))
               .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void is_forbidden_without_its_permission(Endpoint endpoint) throws Exception {
        Permission[] allButRequired = Arrays.stream(Permission.values())
                .filter(permission -> permission != endpoint.required())
                .toArray(Permission[]::new);

        mockMvc.perform(endpoint.toRequest().with(accountWith(allButRequired)))
               .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void is_open_with_its_permission_alone(Endpoint endpoint) throws Exception {
        int status = mockMvc.perform(endpoint.toRequest().with(accountWith(endpoint.required())))
                            .andReturn().getResponse().getStatus();

        assertThat(status).as(endpoint.toString()).isNotIn(401, 403);
    }

    /** Un endpoint sans règle est fermé, quels que soient les droits. */
    @Test
    void denies_any_route_without_a_rule_even_with_every_permission() throws Exception {
        mockMvc.perform(get("/api/unknown").with(accountWith(Permission.values())))
               .andExpect(status().isForbidden());
        mockMvc.perform(request(HttpMethod.PATCH, "/api/publications/1").with(accountWith(Permission.values())))
               .andExpect(status().isForbidden());
    }

    @Test
    void refuses_a_modifying_request_without_the_csrf_token() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/api/publications")
                       .with(user("test").authorities(new SimpleGrantedAuthority(PUBLICATIONS_WRITE.code())))
                       .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Marché\"}"))
               .andExpect(status().isForbidden());
    }

    /**
     * Le retour de Facebook arrive sans cookie de session (SameSite=strict) : il
     * est public, et c'est le `state` qui l'authentifie. Sans `state` connu, il
     * renvoie vers le front avec un motif d'échec, sans rien connecter.
     */
    @Test
    void lets_the_facebook_callback_through_without_a_session() throws Exception {
        mockMvc.perform(get("/api/channels/facebook/callback").param("state", "inconnu").param("code", "x"))
               .andExpect(status().isFound())
               .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/canal-facebook?connexion=erreur")));
    }

    @Test
    void serves_swagger_in_development() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
               .andExpect(status().isOk());
    }
}
