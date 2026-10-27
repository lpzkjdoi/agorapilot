package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import com.jayway.jsonpath.JsonPath;
import fr.maximechazard.agorapilot.back.account.AccountStatus;
import fr.maximechazard.agorapilot.back.account.Permission;
import fr.maximechazard.agorapilot.back.account.UserAccount;
import fr.maximechazard.agorapilot.back.account.UserAccountRepository;
import fr.maximechazard.agorapilot.back.account.events.SecurityEventRepository;
import fr.maximechazard.agorapilot.back.account.events.SecurityEventType;
import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publisher.facebook.FacebookPageTokenProvider;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookOAuthClient.ManagedPage;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.FacebookOAuthClient.TokenInspection;
import fr.maximechazard.agorapilot.back.publisher.suspension.ChannelSuspensionRepository;
import fr.maximechazard.agorapilot.back.publisher.suspension.ChannelSuspensionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La connexion de bout en bout, avec une vraie session et une vraie base ; seule
 * l'API Graph est simulée. Le retour de Facebook est joué <strong>sans</strong>
 * session, comme le navigateur le fait réellement (cookie SameSite=strict).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_facebook_connection_flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "spring.jpa.show-sql=false",
        "frontend.url=https://agora.test",
        "facebook.page-id=page-id"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class FacebookConnectionFlowTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository accountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private FacebookConnectionRepository connectionRepository;
    @Autowired
    private FacebookOAuthStateRepository stateRepository;
    @Autowired
    private SecurityEventRepository eventRepository;
    @Autowired
    private ChannelSuspensionRepository suspensionRepository;
    @Autowired
    private ChannelSuspensionService suspensionService;
    @Autowired
    private FacebookPageTokenProvider tokenProvider;

    @MockitoBean
    private FacebookOAuthClient oauthClient;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        connectionRepository.deleteAll();
        stateRepository.deleteAll();
        suspensionRepository.deleteAll();
        accountRepository.deleteAll();

        accountRepository.save(new UserAccount("maxime", passwordEncoder.encode(PASSWORD), AccountStatus.ACTIVE,
                EnumSet.of(Permission.CHANNELS_MANAGE), LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MICROS)));
        session = (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf())
                                                   .contentType(MediaType.APPLICATION_JSON)
                                                   .content("{\"username\":\"maxime\",\"password\":\"" + PASSWORD + "\"}"))
                                           .andExpect(status().isOk())
                                           .andReturn().getRequest().getSession(false);
        eventRepository.deleteAll();
    }

    private String startConnection() throws Exception {
        String body = mockMvc.perform(post("/api/channels/facebook/connection").session(session).with(csrf()))
                             .andExpect(status().isOk())
                             .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.authorizationUrl");
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
    }

    @Test
    void connects_the_page_from_the_interface_and_resumes_the_channel() throws Exception {
        suspensionService.suspend(DeliveryChannel.FACEBOOK, "Jeton invalide (Facebook, code 190)");
        when(oauthClient.exchangeCode(anyString(), anyString())).thenReturn("court");
        when(oauthClient.exchangeForLongLived("court")).thenReturn("long");
        when(oauthClient.findManagedPage("long", "page-id"))
                .thenReturn(Optional.of(new ManagedPage("page-id", "Mairie", "jeton-page")));
        when(oauthClient.inspect("jeton-page"))
                .thenReturn(new TokenInspection(true, List.of("pages_show_list", "pages_manage_posts"), null, null, null));

        String state = startConnection();
        assertThat(stateRepository.findById(state)).get()
                .extracting(FacebookOAuthState::getUsername).isEqualTo("maxime");

        // Sans session : c'est ainsi que le navigateur revient de facebook.com.
        mockMvc.perform(get("/api/channels/facebook/callback").param("state", state).param("code", "le-code"))
               .andExpect(status().isFound())
               .andExpect(header().string("Location", "https://agora.test/canal-facebook?connexion=ok"));

        assertThat(stateRepository.findById(state)).isEmpty();
        assertThat(suspensionRepository.findById(DeliveryChannel.FACEBOOK)).isEmpty();
        assertThat(tokenProvider.currentPageToken()).isEqualTo("jeton-page");
        assertThat(eventRepository.findAll())
                .anySatisfy(event -> {
                    assertThat(event.getType()).isEqualTo(SecurityEventType.FACEBOOK_CONNECTED);
                    assertThat(event.getActorUsername()).isEqualTo("maxime");
                });

        mockMvc.perform(get("/api/channels/facebook/connection").session(session))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.tokenInUse").value("CONNECTION"))
               .andExpect(jsonPath("$.connection.pageName").value("Mairie"))
               .andExpect(jsonPath("$.connection.connectedBy").value("maxime"));
    }

    @Test
    void refuses_to_replay_a_used_state() throws Exception {
        when(oauthClient.exchangeCode(anyString(), anyString())).thenReturn("court");
        when(oauthClient.exchangeForLongLived("court")).thenReturn("long");
        when(oauthClient.findManagedPage("long", "page-id")).thenReturn(Optional.empty());

        String state = startConnection();
        mockMvc.perform(get("/api/channels/facebook/callback").param("state", state).param("code", "le-code"))
               .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("connexion=erreur")));

        mockMvc.perform(get("/api/channels/facebook/callback").param("state", state).param("code", "le-code"))
               .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("d%C3%A9j%C3%A0%20utilis%C3%A9e")));
        assertThat(connectionRepository.count()).isZero();
    }
}
