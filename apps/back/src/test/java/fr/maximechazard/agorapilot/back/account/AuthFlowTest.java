package fr.maximechazard.agorapilot.back.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_READ;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Connexion, session et mot de passe de bout en bout, contre une vraie base et
 * avec une horloge que les tests avancent.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_auth_flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "spring.jpa.show-sql=false"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
// Importée explicitement : les classes @Nested ne détectent pas la configuration
// déclarée dans la classe englobante.
@Import(AuthFlowTest.ClockConfig.class)
class AuthFlowTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 24, 9, 0);
    private static final String PASSWORD = "correct horse battery";
    private static final String GENERIC_FAILURE = "Identifiant ou mot de passe incorrect.";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private Instant instant = START.atZone(ZONE).toInstant();

        void set(LocalDateTime now) {
            instant = now.atZone(ZONE).toInstant();
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository repository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        clock.set(START);
    }

    // ------------------------------------------------------------ helpers

    private UserAccount account(String username, AccountStatus status, Set<Permission> permissions) {
        return repository.save(new UserAccount(username, passwordEncoder.encode(PASSWORD), status, permissions,
                START.minusDays(1).truncatedTo(ChronoUnit.MICROS)));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private MockHttpSession sessionOf(String username, String password) throws Exception {
        MvcResult result = login(username, password).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** La session est renouvelée par le changement : le navigateur suit le nouveau cookie. */
    private static MockHttpSession renewedSession(ResultActions actions) {
        return (MockHttpSession) actions.andReturn().getRequest().getSession(false);
    }

    private ResultActions changePassword(MockHttpSession session, String current, String next) throws Exception {
        return mockMvc.perform(put("/api/me/password").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    // ------------------------------------------------------------ connexion

    @Nested
    class Login {

        @Test
        void opens_a_session_and_returns_the_account_with_its_permissions() throws Exception {
            account("camille", AccountStatus.ACTIVE, EnumSet.of(PUBLICATIONS_WRITE, PUBLICATIONS_READ));

            MvcResult result = login("Camille", PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("camille"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.permissions[0]").value("publications:read"))
                    .andExpect(jsonPath("$.permissions[1]").value("publications:write"))
                    .andReturn();
            MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

            mockMvc.perform(get("/api/me").session(session))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.username").value("camille"));
            mockMvc.perform(get("/api/publications").session(session))
                   .andExpect(status().isOk());
            assertThat(repository.findByUsername("camille").orElseThrow().getLastLoginAt()).isEqualTo(START);
        }

        @Test
        void requires_the_csrf_token() throws Exception {
            account("camille", AccountStatus.ACTIVE, Set.of());

            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                           .content("{\"username\":\"camille\",\"password\":\"" + PASSWORD + "\"}"))
                   .andExpect(status().isForbidden());
        }

        /** Fixation de session : un identifiant connu avant la connexion ne sert plus à rien après. */
        @Test
        void replaces_the_session_that_existed_before() throws Exception {
            account("camille", AccountStatus.ACTIVE, Set.of(PUBLICATIONS_READ));
            MockHttpSession before = new MockHttpSession();

            MvcResult result = mockMvc.perform(post("/api/auth/login").session(before).with(csrf())
                                              .contentType(MediaType.APPLICATION_JSON)
                                              .content("{\"username\":\"camille\",\"password\":\"" + PASSWORD + "\"}"))
                                      .andExpect(status().isOk()).andReturn();

            assertThat(before.isInvalid()).isTrue();
            assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(before.getId());
        }

        @Test
        void gives_the_same_answer_for_an_unknown_username_and_a_wrong_password() throws Exception {
            account("camille", AccountStatus.ACTIVE, Set.of());

            login("inconnu", PASSWORD).andExpect(status().isUnauthorized())
                                      .andExpect(jsonPath("$.message").value(GENERIC_FAILURE));
            login("camille", "mauvais mot de passe").andExpect(status().isUnauthorized())
                                                    .andExpect(jsonPath("$.message").value(GENERIC_FAILURE));
        }

        @Test
        void refuses_a_disabled_account_with_the_same_answer() throws Exception {
            account("camille", AccountStatus.DISABLED, Set.of(PUBLICATIONS_READ));

            login("camille", PASSWORD).andExpect(status().isUnauthorized())
                                      .andExpect(jsonPath("$.message").value(GENERIC_FAILURE));
        }

        @Test
        void locks_the_account_for_fifteen_minutes_after_five_failures() throws Exception {
            account("camille", AccountStatus.ACTIVE, Set.of());

            for (int i = 0; i < 5; i++) {
                login("camille", "mauvais mot de passe").andExpect(status().isUnauthorized());
            }

            // Même le bon mot de passe est refusé, avec la même réponse.
            login("camille", PASSWORD).andExpect(status().isUnauthorized())
                                      .andExpect(jsonPath("$.message").value(GENERIC_FAILURE));

            clock.advance(Duration.ofMinutes(15).plusSeconds(1));
            login("camille", PASSWORD).andExpect(status().isOk());
        }

        @Test
        void forgets_earlier_failures_after_a_success() throws Exception {
            account("camille", AccountStatus.ACTIVE, Set.of());

            for (int i = 0; i < 4; i++) {
                login("camille", "mauvais mot de passe").andExpect(status().isUnauthorized());
            }
            login("camille", PASSWORD).andExpect(status().isOk());
            login("camille", "mauvais mot de passe").andExpect(status().isUnauthorized());

            login("camille", PASSWORD).andExpect(status().isOk());
        }

        @Test
        void closes_the_session_on_logout() throws Exception {
            account("camille", AccountStatus.ACTIVE, Set.of(PUBLICATIONS_READ));
            MockHttpSession session = sessionOf("camille", PASSWORD);

            mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                   .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
        }

        @Test
        void answers_401_to_me_without_a_session() throws Exception {
            mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------ mot de passe provisoire

    @Nested
    class PendingPassword {

        @Test
        void can_only_change_the_password_until_it_is_done() throws Exception {
            account("camille", AccountStatus.PENDING_PASSWORD, EnumSet.of(PUBLICATIONS_READ));
            MockHttpSession session = sessionOf("camille", PASSWORD);

            mockMvc.perform(get("/api/me").session(session))
                   .andExpect(jsonPath("$.status").value("PENDING_PASSWORD"))
                   .andExpect(jsonPath("$.permissions").isEmpty());
            mockMvc.perform(get("/api/publications").session(session)).andExpect(status().isForbidden());

            MockHttpSession renewed = renewedSession(
                    changePassword(session, PASSWORD, "un nouveau mot de passe").andExpect(status().isNoContent()));

            assertThat(session.isInvalid()).isTrue();
            mockMvc.perform(get("/api/me").session(renewed))
                   .andExpect(jsonPath("$.status").value("ACTIVE"))
                   .andExpect(jsonPath("$.permissions[0]").value("publications:read"));
            mockMvc.perform(get("/api/publications").session(renewed)).andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------ changement de mot de passe

    @Nested
    class ChangePassword {

        @Test
        void refuses_a_wrong_current_password() throws Exception {
            account("camille", AccountStatus.ACTIVE, Set.of());
            MockHttpSession session = sessionOf("camille", PASSWORD);

            changePassword(session, "pas le bon", "un nouveau mot de passe")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Le mot de passe actuel est incorrect."));
        }

        @Test
        void refuses_a_password_shorter_than_twelve_characters() throws Exception {
            account("camille", AccountStatus.ACTIVE, Set.of());
            MockHttpSession session = sessionOf("camille", PASSWORD);

            changePassword(session, PASSWORD, "trop court").andExpect(status().isBadRequest());
        }

        @Test
        void refuses_the_username_and_the_current_password() throws Exception {
            account("camille.durand", AccountStatus.ACTIVE, Set.of());
            MockHttpSession session = sessionOf("camille.durand", PASSWORD);

            changePassword(session, PASSWORD, "Camille.Durand").andExpect(status().isBadRequest());
            changePassword(session, PASSWORD, PASSWORD).andExpect(status().isBadRequest());
        }

        /** Les autres sessions tombent ; celle qui a changé le mot de passe reste ouverte. */
        @Test
        void closes_every_other_session_of_the_account() throws Exception {
            account("camille", AccountStatus.ACTIVE, Set.of(PUBLICATIONS_READ));
            MockHttpSession elsewhere = sessionOf("camille", PASSWORD);
            clock.advance(Duration.ofMinutes(1));
            MockHttpSession here = sessionOf("camille", PASSWORD);
            clock.advance(Duration.ofMinutes(1));

            MockHttpSession renewed = renewedSession(
                    changePassword(here, PASSWORD, "un nouveau mot de passe").andExpect(status().isNoContent()));

            mockMvc.perform(get("/api/me").session(elsewhere)).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/me").session(renewed)).andExpect(status().isOk());
            login("camille", "un nouveau mot de passe").andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------ relecture à chaque requête

    @Nested
    class EveryRequest {

        @Test
        void closes_the_session_of_an_account_disabled_meanwhile() throws Exception {
            UserAccount account = account("camille", AccountStatus.ACTIVE, Set.of(PUBLICATIONS_READ));
            MockHttpSession session = sessionOf("camille", PASSWORD);

            account = repository.findById(account.getId()).orElseThrow();
            account.setStatus(AccountStatus.DISABLED);
            repository.save(account);

            mockMvc.perform(get("/api/publications").session(session)).andExpect(status().isUnauthorized());
            assertThat(session.isInvalid()).isTrue();
        }

        @Test
        void applies_a_permission_withdrawn_meanwhile_on_the_next_request() throws Exception {
            UserAccount account = account("camille", AccountStatus.ACTIVE, EnumSet.of(PUBLICATIONS_READ));
            MockHttpSession session = sessionOf("camille", PASSWORD);
            mockMvc.perform(get("/api/publications").session(session)).andExpect(status().isOk());

            account = repository.findById(account.getId()).orElseThrow();
            account.getPermissions().clear();
            repository.save(account);

            mockMvc.perform(get("/api/publications").session(session)).andExpect(status().isForbidden());
        }

        @Test
        void ends_any_session_after_twelve_hours_even_if_active() throws Exception {
            account("camille", AccountStatus.ACTIVE, Set.of(PUBLICATIONS_READ));
            MockHttpSession session = sessionOf("camille", PASSWORD);

            clock.advance(Duration.ofHours(11).plusMinutes(59));
            mockMvc.perform(get("/api/me").session(session)).andExpect(status().isOk());

            clock.advance(Duration.ofMinutes(2));
            mockMvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
        }
    }
}
