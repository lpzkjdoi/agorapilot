package fr.maximechazard.agorapilot.back.account;

import com.jayway.jsonpath.JsonPath;
import fr.maximechazard.agorapilot.back.account.events.SecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

import static fr.maximechazard.agorapilot.back.account.Permission.MEDIAS_READ;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_READ;
import static fr.maximechazard.agorapilot.back.account.Permission.USERS_MANAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administration des comptes de bout en bout, avec de vraies sessions : ce que
 * peut faire un administrateur, ce que les garde-fous lui interdisent, et l'effet
 * immédiat de chaque action sur le compte visé.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_account_admin;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "spring.jpa.show-sql=false"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class AccountAdminFlowTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository repository;
    @Autowired
    private SecurityEventRepository eventRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserAccount admin;
    private MockHttpSession adminSession;

    @BeforeEach
    void setUp() throws Exception {
        eventRepository.deleteAll();
        repository.deleteAll();
        admin = account("admin", AccountStatus.ACTIVE, EnumSet.allOf(Permission.class));
        adminSession = sessionOf("admin", PASSWORD);
    }

    // ------------------------------------------------------------ helpers

    private UserAccount account(String username, AccountStatus status, Set<Permission> permissions) {
        return repository.save(new UserAccount(username, passwordEncoder.encode(PASSWORD), status, permissions,
                LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MICROS)));
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

    private ResultActions createAccount(MockHttpSession session, String username, String... permissions) throws Exception {
        return mockMvc.perform(post("/api/users").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"permissions\":" + array(permissions) + "}"));
    }

    private ResultActions setPermissions(MockHttpSession session, Long id, String... permissions) throws Exception {
        return mockMvc.perform(put("/api/users/" + id + "/permissions").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":" + array(permissions) + "}"));
    }

    private ResultActions disable(MockHttpSession session, Long id) throws Exception {
        return mockMvc.perform(post("/api/users/" + id + "/disable").session(session).with(csrf()));
    }

    private ResultActions reset(MockHttpSession session, Long id) throws Exception {
        return mockMvc.perform(post("/api/users/" + id + "/password-reset").session(session).with(csrf()));
    }

    private String temporaryPasswordOf(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.temporaryPassword");
    }

    private static String array(String... values) {
        return values.length == 0 ? "[]" : "[\"" + String.join("\",\"", values) + "\"]";
    }

    // ------------------------------------------------------------ création

    @Nested
    class Create {

        @Test
        void creates_a_pending_account_whose_temporary_password_works_once_changed() throws Exception {
            ResultActions created = createAccount(adminSession, "Camille.Durand", "publications:read")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.account.username").value("camille.durand"))
                    .andExpect(jsonPath("$.account.status").value("PENDING_PASSWORD"))
                    .andExpect(jsonPath("$.account.permissions[0]").value("publications:read"));
            String temporary = temporaryPasswordOf(created);

            assertThat(temporary).matches("[a-z2-9]{4}(-[a-z2-9]{4}){3}");
            // Seule l'empreinte est conservée.
            assertThat(repository.findByUsername("camille.durand").orElseThrow().getPasswordHash()).doesNotContain(temporary);

            MockHttpSession session = sessionOf("camille.durand", temporary);
            mockMvc.perform(get("/api/publications").session(session)).andExpect(status().isForbidden());
        }

        @Test
        void refuses_a_username_already_taken_whatever_its_case() throws Exception {
            account("camille", AccountStatus.ACTIVE, Set.of());

            createAccount(adminSession, "CAMILLE").andExpect(status().isConflict());
        }

        @Test
        void refuses_an_invalid_username_or_an_unknown_permission() throws Exception {
            createAccount(adminSession, "camille durand").andExpect(status().isBadRequest());
            createAccount(adminSession, "ca").andExpect(status().isBadRequest());
            createAccount(adminSession, "camille", "publications:everything").andExpect(status().isBadRequest());
            assertThat(repository.findByUsername("camille")).isEmpty();
        }

        @Test
        void cannot_give_a_permission_the_administrator_does_not_hold() throws Exception {
            account("chef", AccountStatus.ACTIVE, EnumSet.of(USERS_MANAGE, PUBLICATIONS_READ));
            MockHttpSession chef = sessionOf("chef", PASSWORD);

            createAccount(chef, "camille", "publications:read", "medias:read")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("medias:read")));
            assertThat(repository.findByUsername("camille")).isEmpty();

            createAccount(chef, "camille", "publications:read").andExpect(status().isCreated());
        }

        @Test
        void is_forbidden_without_users_manage() throws Exception {
            account("camille", AccountStatus.ACTIVE, EnumSet.of(PUBLICATIONS_READ));
            MockHttpSession camille = sessionOf("camille", PASSWORD);

            createAccount(camille, "intrus").andExpect(status().isForbidden());
            mockMvc.perform(get("/api/users").session(camille)).andExpect(status().isForbidden());
        }
    }

    // ------------------------------------------------------------ droits

    @Nested
    class Permissions {

        @Test
        void replaces_the_permissions_and_applies_them_on_the_next_request() throws Exception {
            UserAccount camille = account("camille", AccountStatus.ACTIVE, EnumSet.of(PUBLICATIONS_READ));
            MockHttpSession session = sessionOf("camille", PASSWORD);
            mockMvc.perform(get("/api/medias").session(session)).andExpect(status().isForbidden());

            setPermissions(adminSession, camille.getId(), "medias:read")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.permissions.length()").value(1))
                    .andExpect(jsonPath("$.permissions[0]").value("medias:read"));

            mockMvc.perform(get("/api/medias").session(session)).andExpect(status().isOk());
            mockMvc.perform(get("/api/publications").session(session)).andExpect(status().isForbidden());
        }

        @Test
        void cannot_add_nor_remove_a_permission_the_administrator_does_not_hold() throws Exception {
            account("chef", AccountStatus.ACTIVE, EnumSet.of(USERS_MANAGE, PUBLICATIONS_READ));
            MockHttpSession chef = sessionOf("chef", PASSWORD);
            UserAccount camille = account("camille", AccountStatus.ACTIVE, EnumSet.of(MEDIAS_READ));

            // Retirer medias:read, que le chef n'a pas.
            setPermissions(chef, camille.getId(), "publications:read").andExpect(status().isForbidden());
            // Le garder, et ajouter ce que le chef détient : permis.
            setPermissions(chef, camille.getId(), "medias:read", "publications:read").andExpect(status().isOk());

            assertThat(repository.findById(camille.getId()).orElseThrow().getPermissions())
                    .containsExactlyInAnyOrder(MEDIAS_READ, PUBLICATIONS_READ);
        }

        @Test
        void cannot_change_its_own_permissions() throws Exception {
            setPermissions(adminSession, admin.getId(), "publications:read")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Vous ne pouvez pas modifier vos propres droits."));

            assertThat(repository.findById(admin.getId()).orElseThrow().getPermissions())
                    .containsExactlyInAnyOrder(Permission.values());
        }
    }

    // ------------------------------------------------------------ désactivation et réinitialisation

    @Nested
    class DisableAndReset {

        @Test
        void disabling_closes_the_sessions_and_refuses_the_login() throws Exception {
            UserAccount camille = account("camille", AccountStatus.ACTIVE, EnumSet.of(PUBLICATIONS_READ));
            MockHttpSession session = sessionOf("camille", PASSWORD);

            disable(adminSession, camille.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DISABLED"));

            mockMvc.perform(get("/api/publications").session(session)).andExpect(status().isUnauthorized());
            login("camille", PASSWORD).andExpect(status().isUnauthorized());
        }

        @Test
        void cannot_disable_nor_reset_its_own_account() throws Exception {
            disable(adminSession, admin.getId()).andExpect(status().isForbidden());
            reset(adminSession, admin.getId()).andExpect(status().isForbidden());

            assertThat(repository.findById(admin.getId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void reset_gives_a_new_temporary_password_and_closes_the_sessions() throws Exception {
            UserAccount camille = account("camille", AccountStatus.ACTIVE, EnumSet.of(PUBLICATIONS_READ));
            MockHttpSession session = sessionOf("camille", PASSWORD);

            String temporary = temporaryPasswordOf(reset(adminSession, camille.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.account.status").value("PENDING_PASSWORD")));

            mockMvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
            login("camille", PASSWORD).andExpect(status().isUnauthorized());
            login("camille", temporary).andExpect(status().isOk())
                                       .andExpect(jsonPath("$.status").value("PENDING_PASSWORD"));
        }

        /** Réactiver, c'est réinitialiser : jamais avec l'ancien mot de passe. */
        @Test
        void reset_reactivates_a_disabled_account() throws Exception {
            UserAccount camille = account("camille", AccountStatus.DISABLED, EnumSet.of(PUBLICATIONS_READ));

            String temporary = temporaryPasswordOf(reset(adminSession, camille.getId()).andExpect(status().isOk()));

            login("camille", PASSWORD).andExpect(status().isUnauthorized());
            login("camille", temporary).andExpect(status().isOk());
        }

        @Test
        void reset_lifts_a_lock() throws Exception {
            UserAccount camille = account("camille", AccountStatus.ACTIVE, Set.of());
            for (int i = 0; i < 5; i++) {
                login("camille", "mauvais mot de passe");
            }
            mockMvc.perform(get("/api/users").session(adminSession))
                   .andExpect(jsonPath("$[?(@.username == 'camille')].lockedUntil").isNotEmpty());

            String temporary = temporaryPasswordOf(reset(adminSession, camille.getId()));

            login("camille", temporary).andExpect(status().isOk());
        }

        @Test
        void answers_404_for_an_unknown_account() throws Exception {
            disable(adminSession, 999_999L).andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------ liste et journal

    @Nested
    class ListAndJournal {

        @Test
        void lists_the_accounts_by_username_with_their_assigned_permissions() throws Exception {
            account("zoe", AccountStatus.PENDING_PASSWORD, EnumSet.of(PUBLICATIONS_READ));
            account("bruno", AccountStatus.DISABLED, Set.of());

            mockMvc.perform(get("/api/users").session(adminSession))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$[0].username").value("admin"))
                   .andExpect(jsonPath("$[1].username").value("bruno"))
                   .andExpect(jsonPath("$[1].status").value("DISABLED"))
                   .andExpect(jsonPath("$[2].username").value("zoe"))
                   // Attribués, même s'ils ne sont pas encore exercés.
                   .andExpect(jsonPath("$[2].permissions[0]").value("publications:read"))
                   .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        }

        @Test
        void journals_logins_and_every_administration_action_newest_first() throws Exception {
            login("inconnu", PASSWORD);
            UserAccount camille = account("camille", AccountStatus.ACTIVE, EnumSet.of(PUBLICATIONS_READ));
            setPermissions(adminSession, camille.getId(), "medias:read");
            disable(adminSession, camille.getId());

            mockMvc.perform(get("/api/security-events").session(adminSession))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$[0].type").value("ACCOUNT_DISABLED"))
                   .andExpect(jsonPath("$[0].actor").value("admin"))
                   .andExpect(jsonPath("$[0].target").value("camille"))
                   .andExpect(jsonPath("$[1].type").value("PERMISSIONS_CHANGED"))
                   .andExpect(jsonPath("$[1].details").value("Ajoutés : medias:read ; retirés : publications:read"))
                   .andExpect(jsonPath("$[2].type").value("LOGIN_FAILED"))
                   .andExpect(jsonPath("$[2].target").value("inconnu"))
                   .andExpect(jsonPath("$[2].details").value("Pseudo inconnu"))
                   .andExpect(jsonPath("$[3].type").value("LOGIN_SUCCEEDED"))
                   .andExpect(jsonPath("$[3].actor").value("admin"));

            mockMvc.perform(get("/api/security-events?limit=1").session(adminSession))
                   .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        void journals_a_lock() throws Exception {
            account("camille", AccountStatus.ACTIVE, Set.of());
            for (int i = 0; i < 5; i++) {
                login("camille", "mauvais mot de passe");
            }

            mockMvc.perform(get("/api/security-events?limit=1").session(adminSession))
                   .andExpect(jsonPath("$[0].type").value("ACCOUNT_LOCKED"))
                   .andExpect(jsonPath("$[0].target").value("camille"));
        }
    }
}
